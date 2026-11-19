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
  const response = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: 'POST',
    headers: { authorization: `Bearer ${accessToken}`, 'content-type': 'application/json' },
    body: JSON.stringify({ message: messageFor(device, outgoing) }),
  });
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
  const { body, ...event } = noticeData(notice, senderUid, device.language);
  if (device.platform === 'android') {
    return {
      ...targetOf(device),
      // `uid` is the account this device was registered for; the phone drops a push for any other.
      data: { groupId: notice.groupId, uid: device.uid, title, body, ...event },
      android: { priority: 'high' },
    };
  }
  return {
    ...targetOf(device),
    notification: { title, body },
    data: { groupId: notice.groupId, uid: device.uid, ...event },
    apns: {
      headers: { 'apns-priority': '10' },
      payload: { aps: { 'content-available': 1 } },
    },
  };
}
