import {chatConstants} from './Constants.js';
import * as timeUtils from './timeUtils.js';
import * as dbStorage from '../../../js/dbStorage.js'


const replyToCache = new Map();
const userCache = new Map();
const reactionEvents = new Map();
let isPolling = false;


// variables that will be get from the server
const MATRIX_SERVER_URL='http://localhost:8008';
const JWT_COOKIE_NAME = 'matrix_jwt_token';
const DEFAULT_ROOM_AVATAR = '/matrix/img/room-default.jpg';
const MATRIX_SYNC_SINCE = 'matrix-sync-since';
const MATRIX_SYNC_TIMEOUT = 30000;
const MATRIX_ACTION_MESSAGE_RECEIVED = 'matrix-message-received';
const PUSH_APP_ID = "exo.matrix.app";
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
    const regex = new RegExp(`(^| )${name}=([^;]+)`)
    const match = document.cookie.match(regex)
    if (match) {
      return match[2]
    }
}

export function authenticate() {
  const JWT = getCookieValue(JWT_COOKIE_NAME);
  if(JWT) {
    return fetch(`/_matrix/client/r0/login`, {
      method: 'POST',
      body: JSON.stringify({
        'type':'org.matrix.login.jwt',
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
                    "room":{
                      "timeline":{
                        "unread_thread_notifications":true,
                        "limit":50,
                        "types": [
                          "m.room.message",
                          "m.reaction"
                        ]
                      },
                      "state":{
                        "lazy_load_members":true
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
  return fetch(`/matrix/rest/matrix/processRooms`, {
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
    return processedRooms
  });
}

export function loadRoom(roomId) {
  return fetch(`/_matrix/client/v3/directory/room/${roomId}`, {
    method: 'GET',
    headers: {
      'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
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
                     "preset": "trusted_private_chat",
                     "visibility": "private",
                     "invite": [
                       "@" + matrixIdUserTwo + ":" + serverName
                     ],
                     "is_direct": true,
                     "initial_state": [
                       {
                         "type": "m.room.guest_access",
                         "state_key": "",
                         "content": {
                           "guest_access": "forbidden"
                         }
                       }
                     ]
                   };
  return fetch(`/_matrix/client/v3/createRoom?`, {
      method: 'POST',
      headers: {
        'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
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
        'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
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


export async function processEvents(response) {
  const updatedRooms = response?.rooms?.join;
  if(updatedRooms) {
    for (const roomId in response.rooms.join) {
      const roomEvents = response.rooms.join[roomId].timeline?.events;
      for (const e of roomEvents) {
        if (e.type === 'm.room.message') {
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
        } else if(e.type === 'm.room.member') {
          if(e.content.membership === 'join') {
            const member = {};
            member.id = e.sender;
            member.name = e.content.displayname;
            member.avatarUrl = e.content.avatar_url || DEFAULT_ROOM_AVATAR;
            const room = {};
            room.members = [];
            room.members.push(member);
            room.id = roomId;
            if(!room.updated) {
              room.updated = new Date().getTime();
            }
            if(localStorage.getItem('matrix_user_id') !== e.sender) {
              getRoomById(roomId).then(roomItem => {
                document.dispatchEvent(new CustomEvent('matrix-joined-room', { detail: roomItem }));
              });
            }
          }
        }
      }
      const ephemeralEvents = response.rooms.join[roomId].ephemeral?.events;
      ephemeralEvents.forEach(e => {
        //Users are typing in the room
        if(e.type === 'm.typing') {
          if(e.content.user_ids?.length) {
            console.log(`Users ${e.content.user_ids} are typing on room ${roomId}`);
            //document.dispatchEvent(new CustomEvent('matrix-room-user-typing-received', { detail: {roomId: roomId, usersTyping: e.content.user_ids}}));
          }
        }
        //User sent a read receipt of a room
        if(e.type === 'm.receipt') {
          if(e.content) {
            for (const eventId in e.content) {
              if(e.content[eventId]["m.read"][matrixUserId]?.thread_id) {
                document.dispatchEvent(new CustomEvent('matrix-room-mark-full-read', { detail: {roomId: roomId}}));
              }
            }
          }
        }
      });
    }
  }
  if(response?.presence?.events) {
    response.presence.events.forEach(event => {
      if(localStorage.getItem('matrix_user_id') === event.sender) {
        localStorage.setItem('matrix_user_presence', event.content.presence);
      }
      document.dispatchEvent(new CustomEvent('matrix-user-status-updated', { detail: {userId: event.sender, presence: event.content.presence}}));
    });
  }
}

export async function toRoomObject(rooms, currentMemberId) {
  let myRooms = {rooms: [], totalUnreadMessages: 0};
  const roomMap = new Map();

  for (const roomId in rooms) {
    const roomData = rooms[roomId];
    const events = [...roomData.timeline.events, ...roomData.state.events];

    let roomItem = {
      id: roomId,
      enabledUser: true,
      external: false,
      favorite: false,
      unreadMessages: roomData.unread_notifications.notification_count,
      members: [],
    };

    let membersMap = {};
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
                            "roomId": data.room_id,
                            "firstParticipant": firstParticipant,
                            "secondParticipant": secondParticipant
                          };
          return fetch(`/matrix/rest/matrix`, {
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
    document.dispatchEvent(new CustomEvent(chatConstants.ACTION_OPEN_CHAT_ROOM, { detail : data }));
  }).catch(e => {
    console.log(e)
  });
}

export function openSpaceRoom(spaceId) {
  getSpaceRoom(spaceId).then(data => {
    document.dispatchEvent(new CustomEvent(chatConstants.ACTION_OPEN_CHAT_ROOM, { detail : data }));
  }).catch(e => {
    console.log(e)
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
      await processEvents(data);

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
  const payload = {"room":{"timeline":{"unread_thread_notifications":true},"state":{"lazy_load_members":true}}};
  const matrixUserId = localStorage.getItem("matrix_user_id");
  return fetch(`/_matrix/client/v3/user/${matrixUserId}/filter`, {
    method: 'POST',
    headers: {
     'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
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
    "app_display_name":PUSH_APP_DISPLAY_NAME,
    "app_id": PUSH_APP_ID,
    "append":false,
    "data":{
       "format": "event_id_only",
       "url": window.location.protocol + "//" + window.location.hostname + "/_matrix/push/v1/notify"
    },
    "device_display_name": "Browser",
    "kind":kind || null,
    "lang":eXo.env.portal.language,
    "profile_tag": eXo.env.portal.userName,
    "pushkey": pushKey
  }
  return fetch(`/_matrix/client/v3/pushers/set`, {
    method: 'POST',
    headers: {
     'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
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
  return fetch(`/_matrix/client/v3/pushers`, {
    method: 'GET',
    headers: {
     'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
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
    if(resp.pushers?.length) {
      for(const pusher of resp.pushers) {
        if(pusher.app_id && pusher.app_id === PUSH_APP_ID) {
          found = true;
          if(pusher.pushkey !== token) {
            savePushGateway(null, pusher.pushkey).then(response => {
              savePushGateway('http', token);
            });
          }
        }
      }
    }
  });
  if(!found) {
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

  const memberId = extractUserIdFromRoomMembers(room, matrixId);
  if (!memberId) {
    return Promise.resolve(null);
  }

  return getUserIdentity(memberId).then(user => {
    userCache.set(matrixId, user);
    return user;
  });
}

export async function loadRoomMessages(roomId, from, to) {
  const filter = {'lazy_load_members': true, types: ['m.room.message', 'm.reaction']};
  const formData = new FormData();
  formData.append('limit', chatConstants.MESSAGES_LOAD_LIMIT);
  if(from) {
    formData.append('from', from);
  }
  if(to) {
    formData.append('to', to);
  }
  formData.append('dir', 'b'); // f: chronological order, b: revers-chronological order
  formData.append('filter', JSON.stringify(filter));
  const params = new URLSearchParams(formData).toString();
  if(!roomId.includes(":")) {
    roomId = `${roomId}:${matrixServerName}`;
  }
  return fetch(`/_matrix/client/v3/rooms/${roomId}/messages?${params}`, {
    method: 'GET',
    headers: {
      'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
    },
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Load room messages : Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export async function loadAllRoomMessages(roomId, loadAll) {
  let from = 's0_0_0_0_0_0_0_0_0_0';
  if(!loadAll) {
    from = localStorage.getItem(`${roomId}-latest-messages-from`) || 's0_0_0_0_0_0_0_0_0_0';
  }
  let to = localStorage.getItem(`${roomId}-latest-messages-to`) || '';
  localStorage.setItem(`${roomId}-latest-messages-from`, from);
  let allData = [];
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
      weekday: "long"
    };
    return new Date(resetDateToFormat).toLocaleDateString(localeOfUser, options);
  } else if (timeUtils.differenceInDays(today.getTime(), resetDateToFormat.getTime()) < 31) {// In the last 31 days
    options = {
      weekday: "short",
      style: "short",
      month: "short",
      day: "numeric",
    };
    return new Date(resetDateToFormat.getTime()).toLocaleDateString(localeOfUser, options);
  } else {// Difference more than a month
    options = {
      year: "numeric",
      month: "short",
      day: "numeric"
    };
    return new Date(resetDateToFormat.getTime()).toLocaleDateString(localeOfUser, options);
  }
}

export function getUserDisplayNameFontColor(identityId) {
  const colors = ["rgb(239, 83, 80)", // copied from org.exoplatform.social.core.image.ImageUtils.createDefaultAvatar
                  "rgb(25, 118, 210)",
                  "rgb(171, 71, 188)",
                  "rgb(0, 137, 123)",
                  "rgb(158, 157, 36)",
                  "rgb(251, 192, 45)",
                  "rgb(0, 191, 165)",
                  "rgb(117, 117, 117)",
                  "rgb(244, 67, 54)",
                  "rgb(33, 150, 243)",
                  "rgb(124, 179, 66)",
                  "rgb(48, 63, 159)",
                  "rgb(69, 39, 160)",
                  "rgb(141, 110, 99)",
                  "rgb(255, 111, 0)"];
  return `color: ${colors[Number(identityId) % colors.length]} !important`;
}

export function sendMessage(payload, roomId) {
  let index = localStorage.getItem('matrix_transaction_index') || 1;
  const transactionId = `${new Date().getTime()}-${index}`;
  const eventType = 'm.room.message';
  roomId = roomId.includes(matrixServerName) ? roomId : roomId + ":" + matrixServerName;
  return fetch(`/_matrix/client/v3/rooms/${roomId}/send/${eventType}/${transactionId}`, {
    method: 'PUT',
    headers: {
      'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
    },
    body: JSON.stringify(payload)
  }).then(resp => {
    if (!resp?.ok) {
      console.warn(`Request failed for sending message : \n text = [${message}] \n roomId = ${roomId} \n transactionId ${transactionId}`);
      throw new Error('Response code indicates a server error', resp);
    } else {
      localStorage.setItem('matrix_transaction_index', index ++);
      return true;
    }
  });
}

export function markRoomAsFullyRead(roomId, eventId) {
  if(!roomId) {
    console.warn('No roomId provided, Mark as read call will be canceled')
    return;
  }
  if(!eventId) {
    console.warn('No event Id provided, Mark as read call will be canceled')
    return;
  }
  if(!roomId.includes(":")) {
    roomId = `${roomId}:${matrixServerName}`;
  }
  const payload = {
                    "thread_id": "main"
                  };
  return fetch(`/_matrix/client/v3/rooms/${roomId}/receipt/m.read/${eventId}`, {
    method: 'POST',
    headers: {
      'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
    },
    body: JSON.stringify(payload)
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Mark room as fully read : Response code indicates a server error', resp);
    } else {
      return true;
    }
  });
}

export function formatMentionsInMessage(message) {
  return message
      .replace(
          /<a href="https:\/\/matrix\.to\/#\/([^"]+)">([^<]+)<\/a>/g,
          (match, matrixId, displayName) => {
            const userId = userCache.get(matrixId.slice(1, matrixId.indexOf(':')))?.remoteId;
            const profileUrl = `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/profile/${userId}`;
            return `<a href="${profileUrl}" class="font-weight-bold text-decoration-none" target="_blank">@${displayName}</a>`;
          }
      ).replace(/\n/g, '<br />') || '';
}
export function formatMentionsInRoomList(message) {
  return message.replace(/<a href=\"https:\/\/matrix\.to\/#\/([^"]+)\">([^"]+)<\/a>/g, '<span class=\"font-weight-bold\" target=\"_blank\">@$2<\/span>')
                      .replace(/\n/g, '<br />') || '';
}

export function processMessages(messageItems) {
  const messagesMap = new Map();
  const replyDependencyMap = new Map();
  const leftReactions = [];

  messageItems.forEach(item => {
    if (item.type === 'm.room.message') {
      const relatesTo = item.content['m.relates_to'];
      const newContent = item.content['m.new_content'];

      // Handle edits
      if (relatesTo?.rel_type === 'm.replace' && newContent) {
        const targetEventId = relatesTo.event_id;
        const originalMessage = messagesMap.get(targetEventId);
        if (originalMessage && !isRedacted(originalMessage)) {
          originalMessage.content.body = newContent.body;
          originalMessage.content.msgtype = newContent.msgtype || originalMessage.content.msgtype;
          if(newContent.format && newContent.format === 'org.matrix.custom.html') {
            originalMessage.content.format = newContent.format;
            originalMessage.content.formatted_body = newContent.formatted_body;
          } else {
            delete originalMessage.content.format;
            delete originalMessage.content.formatted_body;
          }
          if(newContent['m.mentions'] && newContent['m.mentions'].user_ids?.length > 0) {
            originalMessage.content['m.mentions'] = newContent['m.mentions'];
          } else {
            delete originalMessage.content['m.mentions'];
          }
          originalMessage.edited = true;
          originalMessage.updatedAt = item.origin_server_ts;

          const dependents = replyDependencyMap.get(targetEventId) || [];
          for (const messageId of dependents) {
            const message = messagesMap.get(messageId);
            if (message) {
              message.replyTo = buildReplyToObject(messagesMap, targetEventId);
            }
          }
        }
      } else {
        item.reactions = item.reactions || [];

        const inReplyTo = relatesTo?.['m.in_reply_to']?.event_id;
        if (inReplyTo) {
          item.replyTo = buildReplyToObject(messagesMap, inReplyTo);

          if (!replyDependencyMap.has(inReplyTo)) {
            replyDependencyMap.set(inReplyTo, []);
          }
          replyDependencyMap.get(inReplyTo).push(item.event_id);
        }

        messagesMap.set(item.event_id, item);
      }
    } else if (item.type === 'm.reaction') {
      const relatesTo = item.content['m.relates_to'];
      const isAnnotation = relatesTo?.rel_type === 'm.annotation';

      if (isAnnotation) {
        const messageReactedTo = messagesMap.get(relatesTo.event_id);
        if (messageReactedTo) {
          processMessageReaction(messageReactedTo, item);
        } else {
          leftReactions.push(item);
        }
      }
    }
  });

  return {
    messages: Array.from(messagesMap.values()),
    leftReactions,
  };
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

export function buildReplyToObject(messages, eventId) {
  const getMessageById = (id) =>
      (messages instanceof Map)
          ? messages.get(id)
          : (Array.isArray(messages) ? messages.find(msg => msg.event_id === id) : null);

  const parentEvent = getMessageById(eventId);
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

  let targetUser = parentEvent.sender;
  let targetEventId = parentEvent.event_id;
  let targetType = parentEvent.content?.msgtype
  let targetThumbnailURL = parentEvent.content?.thumbnail_url;
  let targetUrl = parentEvent.content?.url;
  let targetThumbnailWidth = parentEvent.content?.info?.w || parentEvent.content?.w;
  let targetThumbnailHeight = parentEvent.content?.info?.h || parentEvent.content?.h
  let fileMimeType = parentEvent.content?.info?.mimetype;
  let isUploadedAudioFile = targetType === 'm.audio' && (!parentEvent?.content?.['org.matrix.msc3245.voice']
                                              && !parentEvent?.content?.['org.matrix.msc2516.voice']);

  const replyToObject = {
    body: parentEvent.content.body,
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

export function uploadMatrixImage(file, onProgress) {
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
    "m.relates_to": {
      "rel_type": "m.annotation",
      "event_id": eventId,
      "key": emoji
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
    return null
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

  if (!response.ok) throw new Error(`Failed to fetch event: ${response.statusText}`);
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
    const filteredEvents = [];

    if (events.length === 0 && !data.next_batch) {
      break;
    }

    for (const event of events) {
      if (event.unsigned?.redacted_because) {
        continue;
      }

      if (event.type === 'm.reaction') {
        const relatedEventId = event.content?.['m.relates_to']?.event_id;
        if (!relatedEventId) {
          continue;
        }

        const redacted = await isEventRedacted(roomId, relatedEventId);
        if (redacted) {
          continue;
        }
      }
      if (event.type === 'm.room.redaction') {
        const redactedEventId = event.redacts;
        if (!redactedEventId) {
          continue;
        }
        const redactedEvent = await getEvent(roomId, redactedEventId);
        if (!redactedEvent) {
          continue;
        }
        if (redactedEvent.type === 'm.reaction') {
          continue;
        }
      }
      filteredEvents.push(event);
    }

    const sorted = filteredEvents.sort((a, b) => b.origin_server_ts - a.origin_server_ts);
    if (sorted[0]) return sorted[0];

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
  const user = await Vue.prototype?.$identityService?.getIdentityByProviderIdAndRemoteId?.('organization', userId, 'settings');
  if (user) {
    userCache.set(userId, user);
  }
  return user;
}

function extractUserIdFromRoomMembers(room, matrixId) {
  if (room.spaceId) {
    return room.members.find(member => member.matrixId === matrixId)?.userId
  }
  return room.userId
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
  if(!targetMessage) {
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
  if(!spaceId) {
    console.warn('the space Id parameter should not be null');
    return;
  }
  let statusChange = enable && 'enable' || 'disable';
  return fetch(`/matrix/rest/matrix/${statusChange}/${spaceId}`, {
    method: 'PUT',
    credentials: 'include',
  }).then(resp => {
    if (!resp?.ok) {
      if(resp.status === 504) {
        throw new Error('Timeout : operation is still in progress');
      } else {
        throw new Error(`Error while ${enable ? 'enabling' : 'disabling'} the chat on this space : response status = ${resp.status}`);
      }
    }
  });
}

export function dropUserData() {
  localStorage.removeItem("matrix_user_id");
  localStorage.removeItem("matrix_access_token");
  localStorage.removeItem('matrix_last_login');
}

export function initUserData(data) {
  localStorage.setItem("matrix_user_id", data.user_id);
  localStorage.setItem("matrix_access_token", data.access_token);
  localStorage.setItem("matrix_last_login", new Date().getTime());
}

export async function registerUserToken() {
  const dbExists = await dbStorage.isDatabaseExists(chatConstants.DB_SETTINGS.dbName);
  if (!dbExists) {
    await dbStorage.createDatabase(chatConstants.DB_SETTINGS);
  }
  await dbStorage.setValue(chatConstants.DB_SETTINGS, 'access_token', localStorage.getItem("matrix_access_token"));
}
