import { HttpError } from './http';

const CERTS_URL =
  'https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com';
const TOKEN_URL = 'https://oauth2.googleapis.com/token';
const SCOPES =
  'https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging';
const RSA = { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' } as const;
const CLOCK_SKEW_SECONDS = 300;
const ACCESS_TOKEN_MARGIN_MS = 60_000;
const CERT_REFETCH_INTERVAL_MS = 60_000;

const encoder = new TextEncoder();

// Firebase ID tokens

/** Checks a Firebase ID token and returns its uid. Any failure is a 401. */
export async function verifyIdToken(token: string, projectId: string): Promise<string> {
  const jwt = parseJwt(token);
  if (jwt.header.alg !== 'RS256' || typeof jwt.header.kid !== 'string') throw unauthorized();
  const key = await signingKey(jwt.header.kid);
  const valid = await crypto.subtle.verify(RSA.name, key, jwt.signature, encoder.encode(jwt.signed));
  if (!valid) throw unauthorized();

  const { aud, iss, sub, exp, iat } = jwt.claims;
  const now = Math.floor(Date.now() / 1000);
  const claimsOk =
    aud === projectId &&
    iss === `https://securetoken.google.com/${projectId}` &&
    typeof sub === 'string' &&
    sub.length > 0 &&
    typeof exp === 'number' &&
    exp > now &&
    typeof iat === 'number' &&
    iat <= now + CLOCK_SKEW_SECONDS;
  if (!claimsOk) throw unauthorized();
  return sub;
}

interface Jwt {
  header: Record<string, unknown>;
  claims: Record<string, unknown>;
  signature: Uint8Array;
  signed: string;
}

function parseJwt(token: string): Jwt {
  const parts = token.split('.');
  if (parts.length !== 3) throw unauthorized();
  const [headerPart, payloadPart, signaturePart] = parts as [string, string, string];
  try {
    return {
      header: decodeJson(headerPart),
      claims: decodeJson(payloadPart),
      signature: base64UrlDecode(signaturePart),
      signed: `${headerPart}.${payloadPart}`,
    };
  } catch {
    throw unauthorized();
  }
}

function decodeJson(part: string): Record<string, unknown> {
  const value: unknown = JSON.parse(new TextDecoder().decode(base64UrlDecode(part)));
  if (typeof value !== 'object' || value === null) throw unauthorized();
  return value as Record<string, unknown>;
}

function unauthorized(): HttpError {
  return new HttpError(401, 'invalid token');
}

// Google's securetoken certificates

interface CertCache {
  certs: Record<string, string>;
  expiresAt: number;
}

let certCache: CertCache | undefined;
let lastCertFetchAt = 0;
const keyCache = new Map<string, CryptoKey>();

async function signingKey(kid: string): Promise<CryptoKey> {
  const cached = keyCache.get(kid);
  if (cached) return cached;
  const pem = (await certificates(kid))[kid];
  if (!pem) throw unauthorized();
  const key = await crypto.subtle.importKey('spki', spkiFromCertificate(pemToDer(pem)), RSA, false, [
    'verify',
  ]);
  keyCache.set(kid, key);
  return key;
}

async function certificates(kid: string): Promise<Record<string, string>> {
  const fresh = certCache !== undefined && certCache.expiresAt > Date.now();
  if (fresh && kid in certCache!.certs) return certCache!.certs;
  // A kid the fresh set lacks means Google rotated, so skip the edge cache. Once a minute at most,
  // or a flood of made-up kids would turn into a flood of fetches.
  const bypassEdge = fresh;
  if (bypassEdge && Date.now() - lastCertFetchAt < CERT_REFETCH_INTERVAL_MS) throw unauthorized();
  certCache = await fetchCertificates(bypassEdge);
  keyCache.clear();
  return certCache.certs;
}

async function fetchCertificates(bypassEdge: boolean): Promise<CertCache> {
  const request = new Request(CERTS_URL);
  const edge = caches.default;
  let response = bypassEdge ? undefined : await edge.match(request);
  if (!response) {
    lastCertFetchAt = Date.now();
    response = await fetch(request);
    if (!response.ok) throw new HttpError(503, 'certificates unavailable');
    await edge.put(request, response.clone());
  }
  const certs = (await response.json()) as Record<string, string>;
  return { certs, expiresAt: Date.now() + maxAgeMs(response.headers.get('cache-control')) };
}

function maxAgeMs(cacheControl: string | null): number {
  const match = /max-age=(\d+)/.exec(cacheControl ?? '');
  return match ? Number(match[1]) * 1000 : 5 * 60 * 1000;
}

// WebCrypto imports public keys as SPKI, not as X.509 certificates, so the SPKI is cut out of the
// certificate's DER: Certificate > tbsCertificate > [version] serial signature issuer validity subject SPKI.
function spkiFromCertificate(der: Uint8Array): Uint8Array {
  const certificate = derElement(der, 0);
  const tbs = derElement(der, certificate.start);
  let offset = tbs.start;
  if (derElement(der, offset).tag === 0xa0) offset = derElement(der, offset).end;
  for (let skipped = 0; skipped < 5; skipped++) offset = derElement(der, offset).end;
  return der.slice(offset, derElement(der, offset).end);
}

function derElement(bytes: Uint8Array, offset: number): { tag: number; start: number; end: number } {
  const tag = bytes[offset];
  let length = bytes[offset + 1];
  let headerLength = 2;
  if (length & 0x80) {
    const lengthBytes = length & 0x7f;
    length = 0;
    for (let i = 0; i < lengthBytes; i++) length = (length << 8) | bytes[offset + 2 + i];
    headerLength = 2 + lengthBytes;
  }
  return { tag, start: offset + headerLength, end: offset + headerLength + length };
}

// Service-account access token

interface ServiceAccount {
  client_email: string;
  private_key: string;
}

interface AccessToken {
  value: string;
  expiresAt: number;
}

let accessTokenCache: AccessToken | undefined;
let privateKey: CryptoKey | undefined;

/** An OAuth access token for Firestore and FCM, minted from the service account and cached. */
export async function accessToken(serviceAccountJson: string): Promise<string> {
  if (accessTokenCache && accessTokenCache.expiresAt - ACCESS_TOKEN_MARGIN_MS > Date.now()) {
    return accessTokenCache.value;
  }
  const account = JSON.parse(serviceAccountJson) as ServiceAccount;
  privateKey ??= await crypto.subtle.importKey('pkcs8', pemToDer(account.private_key), RSA, false, [
    'sign',
  ]);
  const now = Math.floor(Date.now() / 1000);
  const assertion = await signJwt(
    { iss: account.client_email, scope: SCOPES, aud: TOKEN_URL, iat: now, exp: now + 3600 },
    privateKey,
  );
  const response = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  });
  if (!response.ok) throw new HttpError(503, 'token exchange failed');
  const body = (await response.json()) as { access_token: string; expires_in: number };
  accessTokenCache = { value: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 };
  return body.access_token;
}

async function signJwt(claims: Record<string, unknown>, key: CryptoKey): Promise<string> {
  const header = base64UrlEncode(encoder.encode(JSON.stringify({ alg: 'RS256', typ: 'JWT' })));
  const payload = base64UrlEncode(encoder.encode(JSON.stringify(claims)));
  const signed = `${header}.${payload}`;
  const signature = await crypto.subtle.sign(RSA.name, key, encoder.encode(signed));
  return `${signed}.${base64UrlEncode(new Uint8Array(signature))}`;
}

// Encoding

function pemToDer(pem: string): Uint8Array {
  const base64 = pem.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
  return Uint8Array.from(atob(base64), (char) => char.charCodeAt(0));
}

function base64UrlDecode(text: string): Uint8Array {
  const base64 = text.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  return Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
