import {chatConstants} from './Constants.js';
import * as timeUtils from './timeUtils.js';

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
    if (!resp || !resp.ok) {
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
      if (!resp || !resp.ok) {
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
                        "limit":20
                      },
                      "state":{
                        "lazy_load_members":true
                      }
                    }
                  };
  return sync(filter).then(resp => {
    if (!resp || !resp.ok) {
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
    if (!resp || !resp.ok) {
      throw new Error('Could not process rooms : Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}
export function loadRoom(roomId) {
  return fetch(`/_matrix/client/v3/directory/room/${roomId}`, {
    method: 'GET',
    headers: {
      'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
    }
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export function createMatrixDMRoom(matrixIDUserOne, matrixIdUserTwo, serverName) {
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
      if (!resp || !resp.ok) {
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
      if (!resp || !resp.ok) {
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
      if (!resp || !resp.ok) {
        throw new Error('Response code indicates a server error', resp);
      } else {
        return true;
      }
    });
}

export function sync(filter, since, timeout) {
  const formData = new FormData();

  if(filter) {
    if(!isNaN(parseFloat(filter))) {
      formData.append('filter', Number(filter));
    } else {
      formData.append('filter', JSON.stringify(filter));
    }
  } else {
    formData.append('filter', "0");
  }
  if(since) {
    formData.append('since', since);
  }
  if(timeout) {
    formData.append('timeout', timeout);
  }
  const dataParams = new URLSearchParams(formData).toString();
  return fetch(`/_matrix/client/v3/sync?${dataParams}`, {
    method: 'GET',
    headers: {
      'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
    }
  });
}

export function processEvents(response) {
  const updatedRooms = response?.rooms?.join;
  if(updatedRooms) {
    for (const roomId in response.rooms.join) {
      const roomEvents = response.rooms.join[roomId].timeline?.events;
      roomEvents.forEach(e => {
        //message received in a room
        if(e.type === 'm.room.message') {
          if(e.content.msgtype === 'm.text') {
            document.dispatchEvent(new CustomEvent('matrix-message-received', { detail: {roomId: roomId, sender: e.sender, message: e.content.body, origin_server_ts: e.origin_server_ts}}));
          }
        } // Joined a new room
        else if(e.type === 'm.room.member') {
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
  let myRooms = {};
  myRooms.rooms = [];
  myRooms.totalUnreadMessages = 0;
  for (const property in rooms) {
    let roomItem = {};
    let members = [];
    const roomEvents = [...rooms[property].timeline.events, ...rooms[property].state.events];
    roomEvents.forEach(e => {
      if(e.type === 'm.room.create'){
        roomItem.updated = e.origin_server_ts;
      }
      if(e.type === 'm.room.topic'){
        roomItem.topic = e.content.topic;
      }
      if(e.type === 'm.room.name'){
        roomItem.name = e.content.name;
      }
      if(e.type === 'm.room.avatar'){
        if(!roomItem.avatarLastUpdated || roomItem.avatarLastUpdated < e.origin_server_ts) {
          const avatarUrl = e.content.url ? e.content.url.substring(6) : chatConstants.DEFAULT_ROOM_AVATAR; // removes the 'mcx://' from the beginning of the URL sent by Matrix
          roomItem.avatarUrl = '/_matrix/media/v3/thumbnail/' + avatarUrl +'?width=32&height=32&method=crop&allow_redirect=true';
          roomItem.avatarLastUpdated = e.origin_server_ts;
        }
      }
      if(e.type === 'm.room.member'){
        if(e.content.membership === 'join') {
          const oldMemberIndex = members.findIndex(element => element.id === e.sender);
          if(oldMemberIndex < 0 || (members && members.length > oldMemberIndex && members[oldMemberIndex].lastUpdated && members[oldMemberIndex].lastUpdated <= e.origin_server_ts)) {
            let member = oldMemberIndex > -1 ? members[oldMemberIndex] : {};
            member.id = e.sender;
            member.name = e.content.displayname;
            member.avatarUrl = e.content.avatar_url;
            member.lastUpdated = e.origin_server_ts;
            if(oldMemberIndex >= 0) {
              members[oldMemberIndex] = member;
            } else {
              members.push(member);
            }
          }
        }
      }
      if(e.type === 'm.room.message') {;
        if(e.content.msgtype === 'm.text' && (!roomItem.updated || roomItem.updated <= e.origin_server_ts)) {
          roomItem.updated = e.origin_server_ts;
          roomItem.lastMessage = {};
          roomItem.lastMessage.content = e.content.body;
          roomItem.lastMessage.sender = e.sender;
        }
      }
    });
    roomItem.members = members;

    roomItem.id = property;
    roomItem.enabledUser = true;
    roomItem.external = false;
    roomItem.favorite = false;
    roomItem.unreadMessages = rooms[property].unread_notifications.notification_count;
    if(!roomItem.name && roomItem.members.length == 2 ) {
      roomItem.name = roomItem.members.filter(member => member.id !== matrixUserId)?.shift()?.name;
      let avatarUrl = roomItem.members.filter(member => member.id !== matrixUserId)?.shift()?.avatarUrl;
      roomItem.dmMemberId = roomItem.members.filter(member => member.id !== matrixUserId)?.shift()?.id;
      roomItem.presence = await getUserPresence(roomItem.dmMemberId);
      if(avatarUrl) {
        avatarUrl = avatarUrl.substring(6); // removes the 'mcx://' from the beginning of the URL sent by Matrix
        roomItem.avatarUrl = '/_matrix/media/v3/thumbnail/' + avatarUrl +'?width=32&height=32&method=crop&allow_redirect=true';
      } else {
        roomItem.avatarUrl = DEFAULT_ROOM_AVATAR;
      }
      roomItem.directChat = true;
    }
    if(roomItem.members == 1) {
      roomItem.name = 'Empty Room';
    }
    if(!roomItem.avatarUrl) {
      roomItem.avatarUrl = DEFAULT_ROOM_AVATAR;
    }
    if(!roomItem.updated) {
      roomItem.updated = new Date().getTime();
    }
    myRooms.totalUnreadMessages += roomItem.unreadMessages;
    myRooms.rooms.push(roomItem);
  }
  myRooms.rooms.sort((roomOne, roomTwo) => {
    if(roomOne.updated && roomTwo.updated) {
      return roomOne.updated <= roomTwo.updated;
    } else if(roomOne.updated) {
      return -1;
    } else if (roomTwo.updated) {
      return 1;
    } else {
      return roomOne.name.localeCompare(roomTwo.name, undefined, {numeric: true, sensitivity: 'base'}); // Natural sorting using room names
    }
  });
  return myRooms;
}

export function getSpaceRoom(spaceId) {
  return fetch(`/matrix/rest/matrix/spaceRoom?spaceId=${spaceId}`, {
    method: 'GET',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Get room by space : Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}
export function getDMRoom(firstParticipant, secondParticipant, serverName) {
  return fetch(`/matrix/rest/matrix/dmRoom?firstParticipant=${firstParticipant}&secondParticipant=${secondParticipant}`, {
    method: 'GET',
  }).then(resp => {
    if (!resp || !resp.ok) {
      if(resp.status === 404) {
        return createMatrixDMRoom(firstParticipant, secondParticipant, serverName).then(data => {
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
              if (!createdRoom || !createdRoom.ok) {
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

export function openDMRoom(firstParticipant, secondParticipant, matrixServerName) {
  getDMRoom(firstParticipant, secondParticipant, matrixServerName).then(data => {
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
     if (!resp || !resp.ok) {
       throw new Error('Get Room by Room Id : Response code indicates a server error or space not found', resp);
     } else {
       return resp.json();
     }
   });
}

export async function longPollingSync(matrixFilterId) {
  const since = localStorage.getItem(MATRIX_SYNC_SINCE) || '';
  let response = await sync(matrixFilterId || '0', since, MATRIX_SYNC_TIMEOUT);

  if (response.status == 502) {
    // Status 502 is a connection timeout error, let's retry
    await longPollingSync();
  } else if (response.status != 200) {
    console.error(response.statusText);
    // Reconnect in five second
    await new Promise(resolve => setTimeout(resolve, 5000));
    await longPollingSync();
  } else {
    // Get and show the message
    let message = await response.json();
    processEvents(message);
    localStorage.setItem(MATRIX_SYNC_SINCE, message.next_batch);
    // call again to get the next batch of data
    await longPollingSync();
  }
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
    if (!resp || !resp.ok) {
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
    "profile_tag":"UserProfile",
    "pushkey": pushKey
  }
  return fetch(`/_matrix/client/v3/pushers/set`, {
    method: 'POST',
    headers: {
     'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
    },
    body: JSON.stringify(payload)
  }).then(resp => {
    if (!resp || !resp.ok) {
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
    if (!resp || !resp.ok) {
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
    if(resp.pushers && resp.pushers.length) {
      for(let i = 0; i < resp.pushers.length; i++) {
        let pusher = resp.pushers[i];
        if(pusher.app_id && pusher.app_id === PUSH_APP_ID) {
          found = true;
          if(pusher.pushkey !== token) {
            savePushGateway(null, pusher.pushkey).then(response => {
              savePushGateway('http', token);
            });
          }
        }
      }
      if(!found) {
        savePushGateway('http', token);
      }
    }
  });
}

export function getByRoomId(roomId) {
    return fetch(`/matrix/rest/matrix/byRoom?roomId=${roomId}`, {
      method: 'GET',
      credentials: 'include',
    }).then(resp => {
      if (!resp || !resp.ok) {
        throw new Error('Get Space by Room Id : Response code indicates a server error or space not found', resp);
      } else {
        return resp.json();
      }
    });
}
export function getUserPresence(userIdOnMatrix) {
    return fetch(`/_matrix/client/v3/presence/${userIdOnMatrix}/status`, {
      method: 'GET',
      headers: {
        'Authorization' : `Bearer ${localStorage.getItem('matrix_access_token')}`,
      }
    },).then(resp => {
      if (!resp || !resp.ok) {
        throw new Error('Get User Presence on Matrix : Response code indicates a server error', resp);
      } else {
        return resp.json();
      }
    }).then(status => {
      return status.presence;
    }).catch(e => {
      console.error(e);
      return 'offline';
    });
}

export function getUserByMatrixId(userIdOnMatrix) {
  let senderMatrixId = userIdOnMatrix;
  if(senderMatrixId.includes('@')) {
    senderMatrixId = userIdOnMatrix.substr(1, userIdOnMatrix.indexOf(":") - 1);
  }
  return fetch(`/matrix/rest/matrix/userByMatrixId?userMatrixId=${senderMatrixId}`, {
    method: 'GET',
    credentials: 'include',
  },).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Get User by Matrix ID : Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}

export async function loadRoomMessages(roomId, from, to) {
  const filter = {types:['m.room.message'],};
  const formData = new FormData();
  formData.append('limit', 50);
  if(from) {
    formData.append('from', from);
  }
  if(to) {
    formData.append('to', to);
  }
  formData.append('dir', 'f'); // f: chronological order, b: revers-chronological order
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