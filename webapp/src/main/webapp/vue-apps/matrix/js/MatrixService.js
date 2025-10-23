import {chatConstants} from './Constants.js';
import * as timeUtils from './timeUtils.js';
import * as dbStorage from '../../../js/dbStorage.js';
import * as matrixUtils from './matrixUtils.js';

const replyToCache = new Map();
const userCache = new Map();
const reactionEvents = new Map();
const lastMessagesByRoom = new Map();
const messageTimestampsMap = new Map();
let isPolling = false;


// variables that will be get from the server
const MATRIX_SERVER_URL='http://localhost:8008';
const JWT_COOKIE_NAME = 'matrix_jwt_token';
const DEFAULT_ROOM_AVATAR = '/matrix/img/room-default.jpg';
const MATRIX_SYNC_SINCE = 'matrix-sync-since';
const MATRIX_SYNC_TIMEOUT = 30000;
const MATRIX_ACTION_MESSAGE_RECEIVED = 'matrix-message-received';
const PUSH_APP_ID = 'exo.matrix.app';
const PUSH_APP_DISPLAY_NAME = 'Meeds application';


export function checkAuthenticationTypes() {
  return fetch('/_matrix/client/r0/login', {
    method: 'GET',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  }).then(resp => {
    return resp.flows.some(flow => flow.type === 'org.matrix.login.jwt');
  });
}

export function getCookieValue(name) {
  const regex = new RegExp(`(^| )${name}=([^;]+)`);
  const match = document.cookie.match(regex);
  if (match) {
    return match[2];
  }
}

export function authenticate() {
  const JWT = getCookieValue(JWT_COOKIE_NAME);
  if (JWT) {
    return fetch('/_matrix/client/r0/login', {
      method: 'POST',
      body: JSON.stringify({
        'type': 'org.matrix.login.jwt',
        'token': JWT
      })
    }).then(resp => {
      if (!resp?.ok) {
        throw new Error('Response code indicates a server error', resp);
      } else {
        return resp.json();
      }
    });
  } else {
    throw new Error('Could not find the JWT token');
  }
}

// change this function and make it async
export function loadChatRooms(currentMemberId) {
  const filter = {
    'room': {
      'timeline': {
        'unread_thread_notifications': true,
        'limit': 50,
        'types': [
          'm.room.message',
          'm.reaction'
        ]
      },
      'state': {
        'lazy_load_members': true
      },
    }
  };
  return sync(filter).then(resp => {
    if (!resp?.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  }).then(resp => {
    localStorage.setItem(MATRIX_SYNC_SINCE, resp?.next_batch);
    return toRoomObject(resp?.rooms?.join, currentMemberId);
  }).then(roomsResponse => {
    return processRooms(roomsResponse);
  });
}

export function processRooms(rooms) {
  return fetch('/matrix/rest/matrix/processRooms', {
    credentials: 'include',
    method: 'POST',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(rooms)
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Could not process rooms : Response code indicates a server error', resp);
    }
    return resp.json();
  }).then(processedRooms => {
    return processedRooms;
  });
}

export function loadRoom(roomId) {
  return fetch(`/_matrix/client/v3/directory/room/${roomId}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
    }
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function createMatrixDMRoom(matrixIdUserTwo, serverName) {
  const payLoad = {
    'preset': 'trusted_private_chat',
    'visibility': 'private',
    'invite': [
      `@${  matrixIdUserTwo  }:${  serverName}`
    ],
    'is_direct': true,
    'initial_state': [
      {
        'type': 'm.room.guest_access',
        'state_key': '',
        'content': {
          'guest_access': 'forbidden'
        }
      }
    ]
  };
  return fetch('/_matrix/client/v3/createRoom?', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
    },
    body: JSON.stringify(payLoad),
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function getDMRoomsAccountData(userName) {
  return fetch(`/matrix/rest/matrix/dmRooms?user=${userName}`, {
    method: 'GET',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function updateDMRoomsAccountData(matrixIDUser, matrixUserDMRooms) {
  const encodedMatrixId = encodeURIComponent(matrixIDUser);
  return fetch(`/_matrix/client/v3/user/${encodedMatrixId}/account_data/m.direct`, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
    },
    body: JSON.stringify(matrixUserDMRooms)
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return true;
    }
  });
}

export function sync(filter, since, timeoutMs = 30000) {
  const params = new URLSearchParams();

  if (filter) {
    params.append('filter', typeof filter === 'string' ? filter : JSON.stringify(filter));
  } else {
    params.append('filter', '0');
  }

  if (since) {
    params.append('since', since);
  }
  if (timeoutMs) {
    params.append('timeout', timeoutMs);
  }

  const controller = new AbortController();

  // fail after timeoutMs + buffer
  const browserTimeout = setTimeout(() => {
    controller.abort();
  }, timeoutMs + 5000);

  return fetch(`/_matrix/client/v3/sync?${params.toString()}`, {
    method: 'GET',
    signal: controller.signal,
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
    }
  }).finally(() => clearTimeout(browserTimeout));
}


export async function processEvents(response, isInitialSync) {
  const updatedRooms = response?.rooms?.join;
  if (updatedRooms) {
    for (const roomId in response.rooms.join) {
      const roomEvents = response.rooms.join[roomId].timeline?.events;
      for (const e of roomEvents) {
        if (e.type === 'm.room.message') {
          if (isInitialSync) {
            continue;
          }
          const isReplacement = e.content?.['m.relates_to']?.rel_type === 'm.replace';
          const isSupportedMsgType = ['m.text', 'm.image', 'm.audio', 'm.file', 'm.video'].includes(e.content.msgtype);

          // Normal or edit message
          if (e.type === 'm.room.message' && (isReplacement || isSupportedMsgType)) {
            const message = isReplacement && e.content?.['m.new_content']
              ? {
                ...e,
                content: e.content['m.new_content'],
                edited: true,
                updatedAt: e.origin_server_ts,
                event_id: e.content['m.relates_to'].event_id
              }
              : e;
            document.dispatchEvent(new CustomEvent('matrix-message-received', {
              detail: {
                roomId: roomId,
                message: message
              }
            }));
            lastMessagesByRoom.set(roomId, message);
            messageTimestampsMap.set(message.event_id, message.origin_server_ts);
          }
        } else if (e.type === 'm.room.redaction') {
          const redactedEventId = e.redacts;
          document.dispatchEvent(new CustomEvent('matrix-message-deleted', {
            detail: {
              roomId: roomId,
              eventId: redactedEventId,
              redaction: e,
              sender: e.sender
            }
          }));
          handleRedactReaction(redactedEventId, roomId);
        }
        if (e.type === 'm.reaction') {
          const relatedEventId = e.content?.['m.relates_to']?.event_id;
          const emojiKey = e.content?.['m.relates_to']?.key;
          const sender = e.sender;

          if (relatedEventId && emojiKey && sender) {
            reactionEvents.set(e.event_id, {
              targetEventId: relatedEventId,
              emoji: emojiKey,
              userId: sender
            });
            const targetEvent = await getEvent(roomId, relatedEventId);
            const targetMessageBody = getFormattedMessageBody(targetEvent);
            document.dispatchEvent(new CustomEvent('matrix-message-reaction-added', {
              detail: {
                roomId: roomId,
                message: e,
                user_id: sender,
                emojiKey: emojiKey,
                targetMessageBody: targetMessageBody
              }
            }));
          }
        } else if (e.type === 'm.room.member') {
          if (e.content.membership === 'join') {
            const member = {};
            member.id = e.sender;
            member.name = e.content.displayname;
            member.avatarUrl = e.content.avatar_url || DEFAULT_ROOM_AVATAR;
            const room = {};
            room.members = [];
            room.members.push(member);
            room.id = roomId;
            if (!room.updated) {
              room.updated = new Date().getTime();
            }
            if (localStorage.getItem('matrix_user_id') !== e.sender) {
              getRoomById(roomId).then(roomItem => {
                document.dispatchEvent(new CustomEvent('matrix-joined-room', { detail: roomItem }));
              });
            }
          }
        } else if (e.type === 'io.meeds.unseen-data-reset') {
          const userId = e.content?.userId;
          deleteUnseenData(roomId,userId);
          document.dispatchEvent(new CustomEvent('matrix-unseen-data-reset', {
            detail: {
              roomId,
              userId: userId,
              timestamp: e?.origin_server_ts,
              event: e
            }
          }));
        } else if (e.type === 'io.meeds.unseen-data-updated') {
          const userId = e.content?.userId;
          const unseenData = e?.content?.unseenData;
          saveUnseenData(roomId, userId, unseenData);
          document.dispatchEvent(new CustomEvent('matrix-unseen-data-updated', {
            detail: {
              roomId,
              userId: userId,
              unseenData: unseenData
            }
          }));
        }
      }
      const ephemeralEvents = response.rooms.join[roomId].ephemeral?.events;
      for (const e of ephemeralEvents) {
        if (e.type === 'm.typing') {
          const typingUsers = (e.content.user_ids || [])
            .filter(userId => userId !== matrixUserId);
          document.dispatchEvent(new CustomEvent('matrix-room-typing-received',
            {
              detail: {
                roomId: roomId,
                users: typingUsers
              }
            }));
        }
        if (e.type === 'm.receipt') {
          await handleReadReceiptEvent(e, roomId);
        }
      }
    }
  }
}

async function handleReadReceiptEvent(event, roomId) {
  const dbSettings = chatConstants.DB_SETTINGS;
  const receiptsStore = dbSettings.DB_STORES.READ_RECEIPTS;
  if (!event.content) {
    return;
  }

  // Load current map of last reads per room
  const storeKey = `lastRead::${roomId}`;
  let lastReads = await dbStorage.getValue(dbSettings, receiptsStore, storeKey);
  if (!lastReads) {
    lastReads = {};
  }

  let updated = false;

  for (const eventId in event.content) {
    const receipt = event.content[eventId]?.['m.read'];
    if (!receipt) {
      continue;
    }

    for (const userId in receipt) {
      const readData = receipt[userId];
      if (!readData) {
        continue;
      }

      // Only update if eventId is newer
      const prevEventId = lastReads?.[userId]?.eventId;
      const prevTimestamp = lastReads?.[userId]?.ts || 0;
      const newTimestamp = messageTimestampsMap?.get(eventId) ?? readData.ts ?? Date.now();

      if (!prevEventId || (newTimestamp >= prevTimestamp)) {
        lastReads[userId] = {
          eventId: eventId,
          ts: newTimestamp,
        };
        updated = true;
      }

      document.dispatchEvent(new CustomEvent('matrix-message-read', {
        detail: { roomId, eventId, userId, readData }
      }));

      if (userId === matrixUserId && readData.thread_id) {
        const isLast = isLastMessageInRoom(eventId, roomId);
        if (isLast) {
          document.dispatchEvent(new CustomEvent('matrix-room-mark-full-read', { 
            detail: { roomId } 
          })
          );
        }
      }
    }
  }

  // Save updated map in one write
  if (updated) {
    await dbStorage.setValue(dbSettings, receiptsStore, storeKey, lastReads);
  }
}

export function loadReadReceiptsForMessage(lastReads, eventId) {
  if (!lastReads) {
    return [];
  }
  return Object.entries(lastReads)
    .filter(([userId, lastReadEvent]) => userId !== matrixUserId && lastReadEvent.eventId === eventId)
    .map(([userId]) => userId);
}

export async function loadLastReadReceipts(roomId) {
  const dbSettings = chatConstants.DB_SETTINGS;
  const receiptsStore = dbSettings.DB_STORES.READ_RECEIPTS;
  const storeKey = `lastRead::${roomId}`;
  return await dbStorage.getValue(dbSettings, receiptsStore, storeKey) || {};
}

function isLastMessageInRoom(eventId, roomId) {
  if (!lastMessagesByRoom) {
    return false;
  }
  return lastMessagesByRoom.get(roomId)?.event_id === eventId;
}

export function toRoomObject(rooms, currentMemberId) {
  const myRooms = {rooms: [], totalUnreadMessages: 0};
  const roomMap = new Map();

  for (const roomId in rooms) {
    const roomData = rooms[roomId];
    const events = [...roomData.timeline.events, ...roomData.state.events];

    const roomItem = {
      id: roomId,
      enabledUser: true,
      external: false,
      favorite: false,
      unreadMessages: roomData.unread_notifications.notification_count,
      members: [],
    };

    const membersMap = {};
    let latestMessage = null;
    for (const e of events) {
      switch (e.type) {
      case 'm.room.create':
        roomItem.created = e.origin_server_ts;
        break;
      case 'm.room.topic':
        roomItem.topic = e.content.topic;
        break;
      case 'm.room.name':
        roomItem.name = e.content.name;
        break;
      case 'm.room.avatar':
        if (!roomItem.avatarLastUpdated || roomItem.avatarLastUpdated < e.origin_server_ts) {
          const url = e.content.url ? e.content.url.substring(6) : DEFAULT_ROOM_AVATAR;
          roomItem.avatarUrl = `/_matrix/media/v3/thumbnail/${url}?width=32&height=32&method=crop&allow_redirect=true`;
          roomItem.avatarLastUpdated = e.origin_server_ts;
        }
        break;
      case 'm.room.member':
        if (e.content.membership === 'join') {
          const member = membersMap[e.sender] ?? {};
          if (!member.lastUpdated || member.lastUpdated <= e.origin_server_ts) {
            membersMap[e.sender] = {
              id: e.sender,
              name: e.content.displayname,
              avatarUrl: e.content.avatar_url,
              lastUpdated: e.origin_server_ts
            };
          }
        }
        break;
      case 'm.room.message':
        if (!latestMessage || latestMessage.origin_server_ts < e.origin_server_ts) {
          latestMessage = e;
        }
        break;
      case 'm.reaction': {
        if (!roomItem.updated || roomItem.updated <= e.origin_server_ts) {
          roomItem.updated = e.origin_server_ts;
        }
        break;
      }
      }
    }

    roomItem.members = Object.values(membersMap);

    // Fallback: if no name & 2-member DM
    if (!roomItem.name && roomItem.members.length === 2) {
      const other = roomItem.members.find(m => m.id !== currentMemberId);
      roomItem.name = other?.name;
      roomItem.dmMemberId = other?.id;
      roomItem.presence = 'offline';
      roomItem.avatarUrl = other?.avatarUrl
        ? `/_matrix/media/v3/thumbnail/${other.avatarUrl.substring(6)}?width=32&height=32&method=crop&allow_redirect=true`
        : DEFAULT_ROOM_AVATAR;
      roomItem.directChat = true;
    }

    if (roomItem.members.length === 1) {
      roomItem.name = 'Empty Room';
    }

    roomItem.avatarUrl = roomItem.avatarUrl || DEFAULT_ROOM_AVATAR;
    roomItem.updated = roomItem.updated || roomItem.created || Date.now();
    myRooms.totalUnreadMessages += roomItem.unreadMessages;

    const existing = roomMap.get(roomId);
    if (!existing || existing.updated < roomItem.updated) {
      roomMap.set(roomId, roomItem);
    }

    if (latestMessage) {
      lastMessagesByRoom?.set?.(roomId, latestMessage);
      if (latestMessage.origin_server_ts > roomItem.updated) {
        roomItem.updated = latestMessage.origin_server_ts;
      }
    }
  }
  myRooms.rooms = Array.from(roomMap.values());
  return myRooms;
}

export function getSpaceRoom(spaceId) {
  return fetch(`/matrix/rest/matrix/spaceRoom?spaceId=${spaceId}`, {
    method: 'GET',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Get room by space : Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function getDMRoom(firstParticipant, secondParticipant, serverName, firstUserMatrixId, secondUserMatrixId) {
  return fetch(`/matrix/rest/matrix/dmRoom?firstParticipant=${firstParticipant}&secondParticipant=${secondParticipant}`, {
    method: 'GET',
  }).then(resp => {
    if (!resp?.ok) {
      if (resp.status === 404) {
        return createMatrixDMRoom(secondUserMatrixId || secondParticipant, serverName).then(data => {
          const payload = {
            'roomId': data.room_id,
            'firstParticipant': firstParticipant,
            'secondParticipant': secondParticipant
          };
          return fetch('/matrix/rest/matrix', {
            credentials: 'include',
            method: 'POST',
            headers: {
              Accept: 'application/json',
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(payload)
          }).then(createdRoom => {
            if (!createdRoom?.ok) {
              throw new Error('Response code indicates a server error', resp);
            } else {
              return getDMRoomsAccountData(firstParticipant).then(accountData =>
                updateDMRoomsAccountData(`${localStorage.getItem('matrix_user_id')}`, accountData)
              ).then(dataResp => {
                //document.dispatchEvent(new CustomEvent('chat-load-chat-rooms'));
                return createdRoom.json();
              });
            }
          });
        });
      } else {
        throw new Error('Response code indicates a server error', resp);
      }
    } else {
      return resp.json();
    }
  });
}

export function openDMRoom(firstParticipant, secondParticipant, matrixServerName, firstUserMatrixId, secondUserMatrixId) {
  getDMRoom(firstParticipant, secondParticipant, matrixServerName, firstUserMatrixId, secondUserMatrixId).then(data => {
    document.dispatchEvent(new CustomEvent(chatConstants.ACTION_OPEN_CHAT_ROOM, { detail: {room: data} }));
  }).catch(e => {
    console.log(e);
  });
}

export function openSpaceRoom(spaceId) {
  getSpaceRoom(spaceId).then(data => {
    document.dispatchEvent(new CustomEvent(chatConstants.ACTION_OPEN_CHAT_ROOM, { detail: {room: data} }));
  }).catch(e => {
    console.log(e);
  });
}

export function getRoomById(roomId) {
  return fetch(`/matrix/rest/matrix/byRoomId?roomId=${roomId}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Get Room by Room Id : Response code indicates a server error or space not found', resp);
    } else {
      return resp.json();
    }
  });
}

export async function startMatrixSyncLoop(matrixFilterId) {
  if (isPolling) {
    return;
  }
  isPolling = true;
  let since = localStorage.getItem(MATRIX_SYNC_SINCE) || '';
  let errorDelay = 5000;
  const maxDelay = 300000;

  while (isPolling) {
    try {
      const response = await sync(matrixFilterId || '0', since, MATRIX_SYNC_TIMEOUT);
      if (response.status === 502) {
        continue;
      }
      if (response.status === 401) {
        console.warn('Unauthorized! Token may be expired.');
        // Optionally stop polling or try to refresh token
        break;
      }

      if (response.status !== 200) {
        console.error('Matrix sync error:', response.status, response.statusText);
        console.warn(`Retrying in ${Math.round(errorDelay / 1000)}s...`);
        await delay(errorDelay);
        errorDelay = Math.min(errorDelay * 2, maxDelay);
        continue;
      }

      const data = await response.json();
      const isInitialSync = !since;
      await processEvents(data, isInitialSync);

      if (data.next_batch) {
        since = data.next_batch;
        localStorage.setItem(MATRIX_SYNC_SINCE, since);
      }

      errorDelay = 5000;

    } catch (err) {
      console.error('Matrix sync failed:', err);
      console.warn(`Retrying in ${Math.round(errorDelay / 1000)}s...`);
      await delay(errorDelay);
      errorDelay = Math.min(errorDelay * 2, maxDelay);
    }
  }

  isPolling = false;
}

export function saveFilter() {
  const payload = {'room': {'timeline': {'unread_thread_notifications': true},'state': {'lazy_load_members': true}}};
  const matrixUserId = localStorage.getItem('matrix_user_id');
  return fetch(`/_matrix/client/v3/user/${matrixUserId}/filter`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
    },
    body: JSON.stringify(payload)
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Save Filter : Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function savePushGateway(kind, pushKey) {
  const payload =
  {
    'app_display_name': PUSH_APP_DISPLAY_NAME,
    'app_id': PUSH_APP_ID,
    'append': false,
    'data': {
      'format': 'event_id_only',
      'url': `${window.location.protocol  }//${  window.location.hostname  }/_matrix/push/v1/notify`
    },
    'device_display_name': 'Browser',
    'kind': kind || null,
    'lang': eXo.env.portal.language,
    'profile_tag': eXo.env.portal.userName,
    'pushkey': pushKey
  };
  return fetch('/_matrix/client/v3/pushers/set', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
    },
    body: JSON.stringify(payload)
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Save Push gateway : Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function getPushers() {
  return fetch('/_matrix/client/v3/pushers', {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
    },
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Get Push gateway : Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });

}

export function installPusher() {
  const token = getCookieValue(JWT_COOKIE_NAME);
  let found = false;
  getPushers().then(resp => {
    if (resp.pushers?.length) {
      for (const pusher of resp.pushers) {
        if (pusher.app_id && pusher.app_id === PUSH_APP_ID) {
          found = true;
          if (pusher.pushkey !== token) {
            savePushGateway(null, pusher.pushkey).then(response => {
              savePushGateway('http', token);
            });
          }
        }
      }
    }
  });
  if (!found) {
    savePushGateway('http', token);
  }
}

export function getByRoomId(roomId) {
  return fetch(`/matrix/rest/matrix/byRoom?roomId=${roomId}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Get Space by Room Id : Response code indicates a server error or space not found', resp);
    } else {
      return resp.json();
    }
  });
}

export function getUserByMatrixId(userIdOnMatrix, room) {
  if (!userIdOnMatrix || !room) {
    return Promise.resolve(null);
  }

  const matrixId = userIdOnMatrix.startsWith('@')
    ? userIdOnMatrix.slice(1, userIdOnMatrix.indexOf(':'))
    : userIdOnMatrix;

  const cachedUser = userCache.get(matrixId);
  if (cachedUser) {
    return Promise.resolve(cachedUser);
  }

  const memberId = extractUserIdFromRoomMembers(room, userIdOnMatrix);
  if (!memberId) {
    return Promise.resolve(null);
  }

  return getUserIdentity(memberId).then(user => {
    userCache.set(matrixId, user);
    return user;
  });
}

export async function loadAllMessagesWithOriginalCount(roomId, from = null, to = null, desiredOriginalCount = 25, ) {
  let allMessages = [];
  let originalCount = 0;
  let done = false;
  let lastResponse = null;

  while (!done) {
    const response = await loadRoomMessages(roomId, from, to);
    lastResponse = response;
    const chunk = response.chunk || [];
    if (!chunk.length) {
      break;
    }

    allMessages = [...allMessages, ...chunk];
    originalCount = allMessages.filter(msg => !msg.content['m.relates_to']?.rel_type).length;

    if (originalCount >= desiredOriginalCount) {
      done = true; 
    } else {
      from = response.end;
      if (!from) {
        break;
      } 
    }
  }

  return {
    chunk: allMessages,
    start: lastResponse?.start || from || null,
    end: lastResponse?.end || to || null,
  };}

export function loadRoomMessages(roomId, from, to) {
  const filter = {'lazy_load_members': true, types: ['m.room.message']};
  const formData = new FormData();
  formData.append('limit', chatConstants.MESSAGES_LOAD_LIMIT);
  if (from) {
    formData.append('from', from);
  }
  if (to) {
    formData.append('to', to);
  }
  formData.append('dir', 'b'); // f: chronological order, b: revers-chronological order
  formData.append('filter', JSON.stringify(filter));
  const params = new URLSearchParams(formData).toString();
  if (!roomId.includes(':')) {
    roomId = `${roomId}:${matrixServerName}`;
  }
  return fetch(`/_matrix/client/v3/rooms/${roomId}/messages?${params}`, {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
    },
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Load room messages : Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export async function loadMessageReactions(roomId, eventId) {
  const url = `/_matrix/client/v1/rooms/${encodeURIComponent(roomId)}/relations/${encodeURIComponent(eventId)}/m.annotation/m.reaction?limit=100`;
  try {
    const resp = await fetch(url, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
      },
      method: 'GET',
    });

    if (!resp.ok) {
      const text = await resp.text();
      console.error(`Load reactions failed: ${resp.status} ${text}`);
      return;
    }
    const data = await resp.json();

    return data.chunk || [];
  } catch (err) {
    console.error('Error loading reactions for message', eventId, err);
    return [];
  }
}

export async function fetchAndProcessReactions(message, roomId) {
  if (!Array.isArray(message.reactions)) {
    message.reactions = [];
  }
  if (!message.reactionsMap) {
    message.reactionsMap = new Map();
  }
  const reactions = await loadMessageReactions(roomId, message.event_id);
  reactions.forEach(reactionEvent => {
    processMessageReaction(message, reactionEvent);
  });
}

export async function loadAllRoomMessages(roomId, loadAll) {
  let from = 's0_0_0_0_0_0_0_0_0_0';
  if (!loadAll) {
    from = localStorage.getItem(`${roomId}-latest-messages-from`) || 's0_0_0_0_0_0_0_0_0_0';
  }
  let to = localStorage.getItem(`${roomId}-latest-messages-to`) || '';
  localStorage.setItem(`${roomId}-latest-messages-from`, from);
  const allData = [];
  let loadFrom = from;
  while (true) {
    const response = await loadRoomMessages(roomId, loadFrom);
    const data = await response.json();
    if (!data.chunk?.length) {
      localStorage.setItem(`${roomId}-latest-messages-from`, from);
      localStorage.setItem(`${roomId}-latest-messages-to`, to);
      break;
    }
    from = data.start;
    to = data.end;
    loadFrom = to;
    allData.push(...data.chunk);
  }
  return allData;
}

export function formatDate(timestamp, dateIfSameDay) {
  if (!timestamp) {
    return '';
  }
  if (timeUtils.isSameDay(timestamp, new Date().getTime()) && !dateIfSameDay) {
    return timeUtils.getTimeString(timestamp);
  } else if (timestamp === -1){
    return '';
  } else {
    return timeUtils.getDayDateString(timestamp);
  }
}

/**
* Format the date to return : today, yesterday,
* or short format of date i.e
* in the same year : Thur, 25 April
* in previous year :  12 Oct 2023
* params : dateToFormat : timestamp
*/
export function formatDateString(dateToFormat) {
  const today = new Date();
  today.setHours(0,0,0,0);
  const resetDateToFormat = new Date(dateToFormat);
  resetDateToFormat.setHours(0,0,0,0);
  let options = {};
  const localeOfUser = eXo.env.portal.language.replace('_', '-');
  if (timeUtils.differenceInDays(today.getTime(), resetDateToFormat.getTime()) < 7){ // In the same week
    options = {
      weekday: 'long'
    };
    return new Date(resetDateToFormat).toLocaleDateString(localeOfUser, options);
  } else if (timeUtils.differenceInDays(today.getTime(), resetDateToFormat.getTime()) < 31) {// In the last 31 days
    options = {
      weekday: 'short',
      style: 'short',
      month: 'short',
      day: 'numeric',
    };
    return new Date(resetDateToFormat.getTime()).toLocaleDateString(localeOfUser, options);
  } else {// Difference more than a month
    options = {
      year: 'numeric',
      month: 'short',
      day: 'numeric'
    };
    return new Date(resetDateToFormat.getTime()).toLocaleDateString(localeOfUser, options);
  }
}

export function getUserDisplayNameFontColor(identityId) {
  const colors = ['rgb(239, 83, 80)', // copied from org.exoplatform.social.core.image.ImageUtils.createDefaultAvatar
    'rgb(25, 118, 210)',
    'rgb(171, 71, 188)',
    'rgb(0, 137, 123)',
    'rgb(158, 157, 36)',
    'rgb(251, 192, 45)',
    'rgb(0, 191, 165)',
    'rgb(117, 117, 117)',
    'rgb(244, 67, 54)',
    'rgb(33, 150, 243)',
    'rgb(124, 179, 66)',
    'rgb(48, 63, 159)',
    'rgb(69, 39, 160)',
    'rgb(141, 110, 99)',
    'rgb(255, 111, 0)'];
  return `color: ${colors[Number(identityId) % colors.length]} !important`;
}

export function sendMessage(payload, roomId) {
  let index = localStorage.getItem('matrix_transaction_index') || 1;
  const transactionId = `${new Date().getTime()}-${index}`;
  const eventType = 'm.room.message';
  roomId = roomId.includes(matrixServerName) ? roomId : `${roomId  }:${  matrixServerName}`;
  return fetch(`/_matrix/client/v3/rooms/${roomId}/send/${eventType}/${transactionId}`, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
    },
    body: JSON.stringify(payload)
  }).then(async resp => {
    if (!resp?.ok) {
      console.warn(`Request failed for sending message : \n text = [${message}] \n roomId = ${roomId} \n transactionId ${transactionId}`);
      throw new Error('Response code indicates a server error', resp);
    } else {
      const data = await resp.json();
      localStorage.setItem('matrix_transaction_index', index++);
      return data.event_id;
    }
  });
}

export function markRoomAsFullyRead(roomId, eventId) {
  if (!roomId) {
    console.warn('No roomId provided, Mark as read call will be canceled');
    return Promise.resolve(false);
  }
  if (!eventId) {
    console.warn('No event Id provided, Mark as read call will be canceled');
    return Promise.resolve(false);
  }
  if (!roomId.includes(':')) {
    roomId = `${roomId}:${matrixServerName}`;
  }
  const payload = {
    'thread_id': 'main'
  };
  return fetch(`/_matrix/client/v3/rooms/${roomId}/receipt/m.read/${eventId}`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
    },
    body: JSON.stringify(payload)
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Mark room as fully read : Response code indicates a server error', resp);
    } else {
      return Promise.resolve(true);
    }
  });
}

export async function formatMentionsInMessage(message, room) {
  if (!message) {
    return '';
  }
  const mentionRegex = /<a href="https:\/\/matrix\.to\/#\/([^"]+)">([^<]+)<\/a>/g;
  const mentions = [...message.matchAll(mentionRegex)];

  const replacements = await Promise.all(
    mentions.map(async ([fullMatch, matrixId, displayName]) => {
      const user = await getUserByMatrixId(matrixId, room);
      const userId = user?.remoteId;
      if (!userId) {
        return fullMatch;
      }

      const profileUrl = `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/profile/${userId}`;
      const formatted = `<a href="${profileUrl}" class="font-weight-bold text-decoration-none" target="_blank">@${displayName}</a>`;
      return { fullMatch, formatted };
    })
  );
  let formattedMessage = message;
  for (const { fullMatch, formatted } of replacements) {
    formattedMessage = formattedMessage.replace(fullMatch, formatted);
  }

  return formattedMessage.replace(/\n/g, '<br />');
}

export function formatMentionsInRoomList(message) {
  return message.replace(/<a href=\"https:\/\/matrix\.to\/#\/([^"]+)\">([^"]+)<\/a>/g, '<span class=\"font-weight-bold\" target=\"_blank\">@$2<\/span>')
    .replace(/\n/g, '<br />') || '';
}

export async function processMessages(room, messageItems) {
  const roomId = room?.id;
  const messagesMap = new Map();
  const replyDependencyMap = new Map();

  const lastReadEventIds = await getLastReadEventIds(roomId);
  const reactionPromises = [];
  const replyPromises = [];
  const formatPromises = [];
  const lastAppliedEditTsMap = new Map();

  for (const item of messageItems) {
    if (item.type !== 'm.room.message') {
      continue;
    }

    const relatesTo = item.content['m.relates_to'];
    const newContent = item.content['m.new_content'];
    messageTimestampsMap.set(item.event_id, item.origin_server_ts);

    if (relatesTo?.rel_type === 'm.replace' && newContent) {
      const targetEventId = relatesTo.event_id;
      const originalMessage = messagesMap.get(targetEventId) || getMessageById(targetEventId, messageItems);
      if (originalMessage && !isRedacted(originalMessage)) {
        const applied = applyEditToMessage(originalMessage, newContent, item, lastAppliedEditTsMap, targetEventId);
        if (!applied) {
          continue;
        }
        const dependents = replyDependencyMap.get(targetEventId) || [];
        for (const messageId of dependents) {
          const message = messagesMap.get(messageId);
          if (message) {
            replyPromises.push(
              buildReplyToObject(messageItems, targetEventId).then(replyTo => {
                message.replyTo = replyTo;
              })
            );
          }
        }
      }
    } else {
      const inReplyTo = relatesTo?.['m.in_reply_to']?.event_id;
      if (inReplyTo) {
        replyPromises.push(
          buildReplyToObject(messageItems, inReplyTo).then(replyToObject => {
            if (replyToObject) {
              item.replyTo = replyToObject;
            }
          })
        );
        if (!replyDependencyMap.has(inReplyTo)) {
          replyDependencyMap.set(inReplyTo, []);
        }
        replyDependencyMap.get(inReplyTo).push(item.event_id);
      }

      item.hasLastReaders = lastReadEventIds.has(item.event_id);
      messagesMap.set(item.event_id, item);

      reactionPromises.push(fetchAndProcessReactions(item, roomId));
    }
    formatPromises.push(processMessageMentions(item, room));
  }

  await Promise.allSettled([...formatPromises, ...replyPromises, ...reactionPromises]);
  return {
    roomId,
    messages: Array.from(messagesMap.values()),
  };
}

function applyEditToMessage(originalMessage, newContent, item, lastAppliedEditTsMap, targetEventId) {
  const lastAppliedTs = lastAppliedEditTsMap.get(targetEventId) || 0;
  if (item.origin_server_ts <= lastAppliedTs) {
    return false;
  }

  originalMessage.content.body = newContent.body;
  originalMessage.content.msgtype = newContent.msgtype || originalMessage.content.msgtype;

  if (newContent.format === 'org.matrix.custom.html') {
    originalMessage.content.format = newContent.format;
    originalMessage.content.formatted_body = newContent.formatted_body;
  } else {
    delete originalMessage.content.format;
    delete originalMessage.content.formatted_body;
  }

  if (newContent['m.mentions']?.user_ids?.length) {
    originalMessage.content['m.mentions'] = newContent['m.mentions'];
  } else {
    delete originalMessage.content['m.mentions'];
  }

  originalMessage.edited = true;
  originalMessage.updatedAt = item.origin_server_ts;

  lastAppliedEditTsMap.set(targetEventId, item.origin_server_ts);
  return true;
}

function getMessageById(id, messages) {
  if (!messages) {
    return null;
  }
  if (messages instanceof Map) {
    return messages.get(id) || null;
  }
  if (Array.isArray(messages)) {
    for (let i = 0; i < messages.length; i++) {
      const msg = messages[i];
      if (msg.event_id === id) {
        return msg;
      }
    }
  }
  return null;
}

export async function processMessageMentions(item, room) {
  const rawMessage =
        (item.content.format === 'org.matrix.custom.html' && item.content.formatted_body) ||
        item.content.body?.replace(/\n/g, '<br />') || '';

  if (!rawMessage) {
    return;
  }
  try {
    item.formattedMessage = await formatMentionsInMessage(rawMessage, room);
  } catch (err) {
    console.error('processMentions failed for', item.event_id, err);
  }
}

export async function getLastReadEventIds(roomId) {
  const lastReads = await loadLastReadReceipts(roomId);
  const filteredReads = Object.entries(lastReads)
    .filter(([key]) => key !== matrixUserId)
    .map(([, value]) => value.eventId);
  return new Set(filteredReads);
}

export function processMessageReaction(messageReactedTo, reactionItem) {
  if (!messageReactedTo.reactionsMap) {
    messageReactedTo.reactionsMap = new Map();
  }

  const key = reactionItem.content['m.relates_to'].key;
  const userId = reactionItem.user_id || reactionItem.sender;
  const targetEventId = reactionItem.content?.['m.relates_to']?.event_id;
  const reactionEventId = reactionItem.event_id;

  let entry = messageReactedTo.reactionsMap.get(key);
  if (!entry) {
    entry = {key, userIds: []};
    messageReactedTo.reactionsMap.set(key, entry);
  }

  if (!entry.userIds.includes(userId)) {
    entry.userIds.push(userId);
  }
  if (reactionEventId) {
    reactionEvents.set(reactionEventId, {
      targetEventId: targetEventId,
      emoji: key,
      userId: userId
    });
  }
  messageReactedTo.reactions = Array.from(messageReactedTo.reactionsMap.values());
  return messageReactedTo;
}

export async function buildReplyToObject(messages, eventId) {
  if (!messages || !messages.length) {
    return null;
  }
  const getMessageById = (id) =>
    ((messages instanceof Map)
      ? messages.get(id)
      : (Array.isArray(messages) ? messages.find(msg => msg.event_id === id) : null));

  let parentEvent = getMessageById(eventId);
  if (!parentEvent) {
    parentEvent = await getEvent(messages[0].room_id, eventId);
  }
  if (!parentEvent) {
    return null;
  }

  const cachedReplyTo = replyToCache.get(eventId);
  const parentUpdatedAt = parentEvent.updatedAt || parentEvent.origin_server_ts;

  if (cachedReplyTo && cachedReplyTo.updatedAt === parentUpdatedAt) {
    return cachedReplyTo.replyTo;
  }

  const parentRelatesTo = parentEvent.content?.['m.relates_to'];
  const isReplyToReply = !!parentRelatesTo?.['m.in_reply_to'];

  const targetUser = parentEvent.sender;
  const targetEventId = parentEvent.event_id;
  const targetType = parentEvent.content?.msgtype;
  const targetThumbnailURL = parentEvent.content?.thumbnail_url;
  const targetUrl = parentEvent.content?.url;
  const targetThumbnailWidth = parentEvent.content?.info?.w || parentEvent.content?.w;
  const targetThumbnailHeight = parentEvent.content?.info?.h || parentEvent.content?.h;
  const fileMimeType = parentEvent.content?.info?.mimetype;
  const isUploadedAudioFile = targetType === 'm.audio' && (!parentEvent?.content?.['org.matrix.msc3245.voice']
                                              && !parentEvent?.content?.['org.matrix.msc2516.voice']);

  const replyToObject = {
    body: parentEvent.content.body?.replace(/<mx-reply>.*?<\/mx-reply>/, ''),
    isReplyToReply: isReplyToReply,
    targetUser: targetUser,
    targetEventId: targetEventId,
    targetType: targetType,
    targetThumbnailURL: targetThumbnailURL,
    targetUrl: targetUrl,
    targetThumbnailWidth: targetThumbnailWidth,
    targetThumbnailHeight: targetThumbnailHeight,
    fileMimeType: fileMimeType,
    isUploadedAudioFile: isUploadedAudioFile,
  };

  replyToCache.set(eventId, { replyTo: replyToObject, updatedAt: parentUpdatedAt });
  return replyToObject;
}

export function uploadMatrixFile(file, onProgress) {
  return new Promise((resolve, reject) => {
    const uploadUrl = `/_matrix/media/v3/upload?filename=${encodeURIComponent(file.name)}`;
    const xhr = new XMLHttpRequest();

    xhr.open('POST', uploadUrl, true);
    xhr.setRequestHeader('Authorization', `Bearer ${localStorage.getItem('matrix_access_token')}`);
    xhr.setRequestHeader('Content-Type', file.type);

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) {
        const percent = Math.round((event.loaded / event.total) * 95);
        onProgress(percent);
      }
    };

    xhr.onload = () => {
      if (xhr.status === 200) {
        onProgress(100);
        const response = JSON.parse(xhr.responseText);
        resolve(response.content_uri);
      } else {
        reject(new Error(`Upload failed: ${xhr.status} ${xhr.statusText}`));
      }
    };

    xhr.onerror = () => reject(new Error('Upload error'));
    xhr.send(file);
  });
}

export async function getMaxUploadSize() {
  try {
    const response = await fetch('/_matrix/media/v3/config', {
      headers: {
        Authorization: `Bearer ${localStorage.getItem('matrix_access_token')}`,
      },
    });
    const data = await response.json();
    return data?.['m.upload.size'] || 20 * 1024 * 1024;
  } catch (error) {
    console.error('Error fetching data:', error);
  }
}

export function getParticipantInfo(userId) {
  return fetch(`/matrix/rest/matrix/participant/${userId}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Error while getting invited participant info');
    }
    return resp.json();
  });
}

export async function reactToMessage(emoji, roomId, eventId) {
  const txnId = `m${Date.now()}`; // unique txn id
  const url = `/_matrix/client/v3/rooms/${encodeURIComponent(roomId)}/send/m.reaction/${txnId}`;

  const body = {
    'm.relates_to': {
      'rel_type': 'm.annotation',
      'event_id': eventId,
      'key': emoji
    }
  };
  const response = await fetch(url, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(body)
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Failed to send reaction: ${error}`);
  }

  return await response.json();
}

export async function redactEvent(roomId, eventId) {
  const txnId = `r${Date.now()}`;
  const url = `/_matrix/client/v3/rooms/${encodeURIComponent(roomId)}/redact/${eventId}/${txnId}`;

  const response = await fetch(url, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({})
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Failed to redact reaction: ${error}`);
  }
}

export async function findReactionEventId(emoji, targetEventId, userId, roomId) {
  const url = `/_matrix/client/v1/rooms/${encodeURIComponent(roomId)}/relations/${targetEventId}?rel_type=m.annotation&limit=100`;
  const response = await fetch(url, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`
    }
  });

  if (!response.ok) {
    return null;
  }
  const data = await response.json();
  const match = data.chunk.find(e =>
    e.sender === userId &&
      e.content?.['m.relates_to']?.key === emoji
  );

  return match?.event_id || null;
}

export async function getEvent(roomId, eventId) {
  const url = `/_matrix/client/v3/rooms/${encodeURIComponent(roomId)}/event/${encodeURIComponent(eventId)}`;
  const response = await fetch(url, {
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`
    }
  });

  if (!response.ok) {
    console.warn(`Failed to fetch event: ${eventId} in room ${roomId}, it may be deleted or inaccessible`);
    return null;
  }
  return await response.json();
}

export async function isEventRedacted(roomId, eventId) {
  try {
    const event = await getEvent(roomId, eventId);
    return !!event.unsigned?.redacted_because;
  } catch (e) {
    console.warn(`Could not fetch event ${eventId}:`, e.message);
    return true;
  }
}

export async function getRoomLastMessage(roomId) {
  const filter = {
    types: ['m.room.message', 'm.reaction', 'm.room.redaction']
  };

  const baseUrl = `/_matrix/client/v3/rooms/${encodeURIComponent(roomId)}/messages`;
  let limit = 3;
  const maxLimit = 50;
  let from = null;
  const maxIterations = 5;
  let iterations = 0;

  while (limit <= maxLimit && iterations < maxIterations) {
    const params = new URLSearchParams({
      dir: 'b',
      limit: limit.toString(),
      filter: JSON.stringify(filter)
    });

    if (from) {
      params.set('from', from);
    }

    const response = await fetch(`${baseUrl}?${params.toString()}`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`
      }
    });

    if (!response.ok) {
      console.error('Failed to fetch messages:', await response.text());
      return null;
    }

    const data = await response.json();
    const events = data.chunk || [];

    if (events.length === 0 && !data.next_batch) {
      break;
    }
    let lastMessage = null;

    for (const event of events) {
      if (event.unsigned?.redacted_because) {continue;}

      if (event.type === 'm.reaction') {
        const relatedEventId = event.content?.['m.relates_to']?.event_id;
        if (!relatedEventId) {continue;}

        const redacted = await isEventRedacted(roomId, relatedEventId);
        if (redacted) {continue;}
      }

      if (event.type === 'm.room.redaction') {
        const redactedEventId = event.redacts;
        if (!redactedEventId) {continue;}

        const redactedEvent = await getEvent(roomId, redactedEventId);
        if (!redactedEvent) {continue;}
        if (redactedEvent.type === 'm.reaction') {continue;}
      }

      if (!lastMessage || event.origin_server_ts > lastMessage.origin_server_ts) {
        lastMessage = event;
      }
    }

    if (lastMessage) {
      return lastMessage;
    }

    from = data.next_batch;
    limit = Math.min(limit * 2, maxLimit);
    iterations++;
  }

  return null;
}

export async function buildRoomLastMessage(e, type, roomItem, roomData) {
  switch (type) {
  case 'm.room.message': {
    const isRedacted = !!e.unsigned?.redacted_because;
    const isReplacement = e.content?.['m.relates_to']?.rel_type === 'm.replace' && e.content?.['m.new_content'];
    const content = isReplacement ? e.content['m.new_content'] : e.content;
    const eventId = isReplacement ? e.content['m.relates_to'].event_id : e.event_id;
    const isSupportedMsgType = ['m.text', 'm.image', 'm.audio', 'm.file', 'm.video'].includes(content?.msgtype);

    if (isRedacted) {
      return {
        content: exoi18n.i18n.t('matrix.chat.message.deleted'),
        sender: e.sender,
        eventId,
        redacted: true
      };
    } else if (isSupportedMsgType) {
      return {
        content: content.format === 'org.matrix.custom.html'
          ? formatMentionsInRoomList(content.formatted_body)
          : content.body,
        sender: e.sender,
        eventId,
        edited: isReplacement ? true : undefined
      };
    }
    return null;
  }
  case 'm.reaction': {
    const reactionKey = e.content?.['m.relates_to']?.key;
    const reactedEventId = e.content?.['m.relates_to']?.event_id;
    let target = roomData?.timeline?.events?.find(ev => ev.event_id === reactedEventId);
    if (!target && reactedEventId) {
      target = await getEvent(roomItem.id, reactedEventId);
    }
    const targetMessageBody = getFormattedMessageBody(target);
    if (reactionKey && targetMessageBody) {
      return {
        content: targetMessageBody,
        sender: e.sender,
        eventId: reactedEventId,
        reactionKey,
        reaction: true
      };
    }
    return null;
  }
  case 'm.room.redaction': {
    return {
      content: exoi18n.i18n.t('matrix.chat.message.deleted'),
      sender: e.sender,
      eventId: e.event_id,
      redacted: true
    };
  }
  default:
    return null;
  }
}

export async function getUserIdentity(userId) {
  if (!userId) {
    return;
  }
  if (userCache.has(userId)) {
    return userCache.get(userId);
  }
  const user = await Vue.prototype?.$identityService?.getIdentityByProviderIdAndRemoteId?.('organization', userId, 'settings,connectionsInCommonCount');
  if (user) {
    userCache.set(userId, user);
  }
  return user;
}

function extractUserIdFromRoomMembers(room, matrixId) {
  if (room.spaceId) {
    return room.members.find(member => member.matrixId === matrixId || matrixId === `@${  member.matrixId  }:${  matrixServerName}`)?.userId;
  }
  return matrixId === matrixUserId ? eXo.env.portal.userName : room.userId;
}

function handleRedactReaction(redactedEventId, roomId) {
  if (reactionEvents.has(redactedEventId)) {
    const reaction = reactionEvents.get(redactedEventId);

    document.dispatchEvent(new CustomEvent('matrix-message-reaction-removed', {
      detail: {
        roomId: roomId,
        reactionEventId: redactedEventId,
        targetEventId: reaction.targetEventId,
        emoji: reaction.emoji,
        userId: reaction.userId
      }
    }));
  }
}

function getFormattedMessageBody(targetMessage) {
  if (!targetMessage) {
    return '';
  }
  return targetMessage.content.format === 'org.matrix.custom.html'
    ? formatMentionsInRoomList(targetMessage.content.formatted_body)
    : targetMessage.content.body;
}

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function isRedacted(event) {
  return !!event.unsigned?.redacted_because || !!event.redacted_because;
}

export function enableOrDisableChat(spaceId, enable) {
  if (!spaceId) {
    console.warn('the space Id parameter should not be null');
    return;
  }
  const statusChange = enable && 'enable' || 'disable';
  return fetch(`/matrix/rest/matrix/${statusChange}/${spaceId}`, {
    method: 'PUT',
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      if (resp.status === 504) {
        throw new Error('Timeout : operation is still in progress');
      } else {
        throw new Error(`Error while ${enable ? 'enabling' : 'disabling'} the chat on this space : response status = ${resp.status}`);
      }
    }
  });
}

export function dropUserData() {
  localStorage.removeItem('matrix_user_id');
  localStorage.removeItem('matrix_access_token');
  localStorage.removeItem('matrix_last_login');
}

export function initUserData(data) {
  localStorage.setItem('matrix_user_id', data.user_id);
  localStorage.setItem('matrix_access_token', data.access_token);
  localStorage.setItem('matrix_last_login', new Date().getTime());
}

export async function registerUserToken() {
  const dbExists = await dbStorage.isDatabaseExists(chatConstants.DB_SETTINGS.DB_NAME);
  if (!dbExists) {
    await dbStorage.createDatabase(chatConstants.DB_SETTINGS);
  }
  const settings = {
    'access_token': localStorage.getItem('matrix_access_token'),
    'user_id': localStorage.getItem('matrix_user_id')
  };
  await dbStorage.setValue(
    chatConstants.DB_SETTINGS,
    chatConstants.DB_SETTINGS.DB_STORES.SETTINGS,
    'settings',
    settings
  );
}

export async function cacheRooms(rooms) {
  const dbExists = await dbStorage.isDatabaseExists(chatConstants.DB_SETTINGS.DB_NAME);
  if (!dbExists) {
    await dbStorage.createDatabase(chatConstants.DB_SETTINGS);
  }
  await dbStorage.setValue(
    chatConstants.DB_SETTINGS,
    chatConstants.DB_SETTINGS.DB_STORES.CACHE,
    'cachedRooms',
    rooms
  );
}

export function retrieveCachedRooms() {
  return dbStorage.getValue(chatConstants.DB_SETTINGS, chatConstants.DB_SETTINGS.DB_STORES.CACHE, 'cachedRooms');
}

export async function muteRoom(roomId, spaceId, isMuted) {
  if (spaceId) {
    return Vue.prototype.$spaceService.muteSpace(spaceId, isMuted);
  }
  try {
    const response = await fetch(`/matrix/rest/matrix/muteRoom?roomId=${encodeURIComponent(roomId)}`, {
      method: 'POST',
      credentials: 'include',
    });
    return await response.text();
  } catch (error) {
    console.error('Error muting private room:', error);
    throw error;
  }
}

export async function sendTyping(roomId, isTyping, timeoutMs = 30000) {
  const url = `/_matrix/client/v3/rooms/${encodeURIComponent(roomId)}/typing/${encodeURIComponent(matrixUserId)}`;
  const body = isTyping ? {typing: true, timeout: timeoutMs} : {typing: false};

  try {
    const response = await fetch(url, {
      method: 'PUT',
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      console.error('Failed to send typing notification', await response.text());
    }
  } catch (err) {
    console.error('Error sending typing notification', err);
  }
}

export async function saveUnseenMessages(roomId, userId, unseenData) {
  const result = await saveUnseenData(roomId, userId, unseenData);
  populateUnseenSectionData(roomId, 'io.meeds.unseen-data-updated', {roomId: roomId, userId: userId, unseenData});
  return result;
}

async function saveUnseenData(roomId, userId, unseenData) {
  if (userId !== matrixUserId) {
    return;
  }
  const key = `unseen::${roomId}::${userId}`;
  return await dbStorage.setValue(
    chatConstants.DB_SETTINGS,
    chatConstants.DB_SETTINGS.DB_STORES.UNSEEN_MESSAGES,
    key,
    unseenData
  );
}

export function getUnseenMessages(roomId, userId) {
  const key = `unseen::${roomId}::${userId}`;
  return dbStorage.getValue(
    chatConstants.DB_SETTINGS,
    chatConstants.DB_SETTINGS.DB_STORES.UNSEEN_MESSAGES,
    key
  );
}

export async function clearUnseenMessages(roomId, userId) {
  deleteUnseenData(roomId, userId);
  return await populateUnseenSectionData(roomId, 'io.meeds.unseen-data-reset',
    {roomId: roomId, userId: userId,});
}

function deleteUnseenData(roomId, userId) {
  if (userId !== matrixUserId) {
    return;
  }
  const key = `unseen::${roomId}::${userId}`;
  dbStorage.setValue(
    chatConstants.DB_SETTINGS,
    chatConstants.DB_SETTINGS.DB_STORES.UNSEEN_MESSAGES,
    key,
    {
      firstUnseenEventId: null,
    }
  );
}
export async function getUnseenMessagesData(roomId, userId) {
  const unseenData = await getUnseenMessages(roomId, userId);
  if (!unseenData?.firstUnseenEventId) {
    return {};
  }
  return {
    firstUnseenEventId: unseenData.firstUnseenEventId,
    viewPortInfo: matrixUtils.getMessageViewportInfo(unseenData.firstUnseenEventId)
  };
}

export async function resetUnseenOnFirstMessageSeen(roomId, userId) {
  const unseenData = await getUnseenMessages(roomId, userId);
  if (!unseenData || !unseenData?.firstUnseenEventId) {
    return false;
  }
  await clearUnseenMessages(roomId, userId);
  return true;
}

export function getMatrixIdOfUser(userId) {
  if (!userId) {
    return;
  }
  return fetch(`/matrix/rest/matrix/findId/${userId}`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (resp?.ok) {
      return resp.text();
    } else {
      throw new Error('Get Matrix Id of user : Response code indicates a server error', resp);
    }
  });
}

export async function markMessageAsRead(roomId, eventId) {
  await fetch(`/_matrix/client/v3/rooms/${roomId}/receipt/m.read/${eventId}`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({})
  });
}

export async function populateUnseenSectionData(roomId, event, content) {
  await sendCustomEvent(roomId, event, content);
}

export async function sendCustomEvent(roomId, eventType, content) {
  const txnId = `${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
  const url = `/_matrix/client/v3/rooms/${encodeURIComponent(roomId)}/send/${encodeURIComponent(eventType)}/${txnId}`;

  const accessToken = localStorage.getItem('matrix_access_token');
  const resp = await fetch(url, {
    method: 'PUT',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(content),
  });

  if (!resp.ok) {
    const errText = await resp.text();
    console.error(`Failed to send ${eventType}: ${resp.status} ${errText}`);
    return null;
  }

  return await resp.json();
}