import type { Device } from './firestore';
import { noticeData, type Notice } from './notice';

/** One notice on its way: the group's stored name as the title, and who sent it. */
export interface Outgoing {
  notice: Notice;
  title: string;
  senderUid: string;
}

export type SendResult = 'sent' | 'dead' | 'failed';

interface FcmErrorBody {
  error?: {
    message?: string;
    details?: Array<{
      errorCode?: string;
      fieldViolations?: Array<{ field?: string }>;
    }>;
  };
}

/** Sends one message. 'dead' means FCM rejected the installation ID itself and the device should go. */
export async function sendToDevice(
  projectId: string,
  accessToken: string,
  device: Device,
  outgoing: Outgoing,
): Promise<SendResult> {
  let response: Response;
  try {
    response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
      // One phone FCM does not answer must not hold up the rest of the group's sends.
      signal: AbortSignal.timeout(FCM_TIMEOUT_MS),
      method: 'POST',
      headers: { authorization: `Bearer ${accessToken}`, 'content-type': 'application/json' },
      body: JSON.stringify({ message: messageFor(device, outgoing) }),
    });
  } catch (error) {
    console.warn(`fcm no answer for ${device.platform}: ${error instanceof Error ? error.name : 'error'}`);
    return 'failed';
  }
  if (response.ok) return 'sent';

  const error = ((await response.json().catch(() => undefined)) as FcmErrorBody | undefined)?.error;
  const details = error?.details ?? [];
  const errorCode = details.find((detail) => typeof detail.errorCode === 'string')?.errorCode;
  const badFields = details.flatMap((detail) => detail.fieldViolations ?? []).map((v) => v.field ?? '');
  // INVALID_ARGUMENT also covers a malformed message, such as one too big; only a rejected target
  // field retires the device, so one bad notice can never wipe a group's phones.
  const tokenRejected =
    (response.status === 404 && errorCode === 'UNREGISTERED') ||
    (response.status === 400 &&
      errorCode === 'INVALID_ARGUMENT' &&
      badFields.some((field) => field.includes('fid') || field.includes('token')));
  const removed = tokenRejected ? ', device removed' : '';
  console.warn(`fcm ${response.status} ${errorCode ?? ''} for ${device.platform}${removed}: ${error?.message ?? ''}`);
  return tokenRejected ? 'dead' : 'failed';
}

/**
 * Play services older than 26.12 still registers with a classic token (`<id>:APA91…`), which FCM
 * takes only as `token`; a Firebase installation ID goes as `fid`.
 */
function targetOf(device: Device): Record<string, string> {
  return device.token.includes(':') ? { token: device.token } : { fid: device.token };
}

function messageFor(device: Device, { notice, title, senderUid }: Outgoing): Record<string, unknown> {
  // `uid` is the account this device was registered for; the phone drops a push for any other.
  const data = { groupId: notice.groupId, uid: device.uid, title, ...noticeData(notice, senderUid, device.language) };
  if (device.platform === 'android') {
    return { ...targetOf(device), data, android: { priority: 'high' } };
  }
  // M6: data only, so the iPhone words the notice itself and drops one for another account.
  return {
    ...targetOf(device),
    data,
    apns: {
      headers: { 'apns-priority': '5', 'apns-push-type': 'background' },
      payload: { aps: { 'content-available': 1 } },
    },
  };
}

const FCM_TIMEOUT_MS = 10_000;
