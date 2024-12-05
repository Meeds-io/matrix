import * as chatConstants from './Constants.js';

// variables that will be get from the server
const MATRIX_SERVER_URL='http://localhost:8008';
const JWT_COOKIE_NAME = 'matrix_jwt_token';
const DEFAULT_ROOM_AVATAR = '/matrix/img/room-default.jpg';
const MATRIX_SYNC_SINCE = 'matrix-sync-since';
const MATRIX_SYNC_TIMEOUT = 30000;
const MATRIX_EMPTY_FILTER = 0;
const MATRIX_ACTION_MESSAGE_RECEIVED = 'matrix-message-received';


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

// change this function and make it aAsync
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
    formData.append('filter', JSON.stringify(filter));
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
        if(e.type === 'm.room.message') {
          if(e.content.msgtype === 'm.text') {
            console.log('event sent START');
            document.dispatchEvent(new CustomEvent('matrix-message-received', {roomId: roomId, message: e.content.body}));
            console.log('event sent END');
          }
        }
      });
    }
  }
}

export function toRoomObject(rooms, currentMemberId) {
  let myRooms = {};
  myRooms.rooms = [];
  myRooms.totalUnreadMessages = 0;
  for (const property in rooms) {
    let roomItem = {};
    let members = [];
    const roomEvents = [...rooms[property].timeline.events, ...rooms[property].state.events];
    roomEvents.forEach(e => {
      if(e.type === 'm.room.topic'){
        roomItem.topic = e.content.topic;
      }
      if(e.type === 'm.room.name'){
        roomItem.name = e.content.name;
      }
      if(e.type === 'm.room.avatar'){
        const avatarUrl = e.content.url ? e.content.url.substring(6) : chatConstants.DEFAULT_ROOM_AVATAR; // removes the 'mcx://' from the beginning of the URL sent by Matrix
        roomItem.avatarUrl = '/_matrix/media/v3/thumbnail/' + avatarUrl +'?width=32&height=32&method=crop&allow_redirect=true';
      }
      if(e.type === 'm.room.member'){
        if(e.content.membership === 'join') {
          if(!members.some(element => element.id == e.sender)) {
            let member = {};
            member.id = e.sender;
            member.name = e.content.displayname;
            members.push(member);
          }
        }
      }
      if(e.type === 'm.room.message') {;
        if(e.content.msgtype === 'm.text' && (!roomItem.updated || roomItem.updated <= e.origin_server_ts)) {
          roomItem.updated = e.origin_server_ts;
          roomItem.lastMessage = e.content.body;
        }
      }
    });
    roomItem.members = members;

    roomItem.id = property;
    roomItem.type = 'space';
    roomItem.isEnabledUser = true;
    roomItem.isExternal = false;
    roomItem.isFavorite = false;
    roomItem.unreadTotal = rooms[property].unread_notifications.notification_count;
    if(!roomItem.name && roomItem.members.length == 2) {
      roomItem.name = roomItem.members.filter(member => member.id !== matrixUserId)?.shift()?.name;
      roomItem.isDirectChat = true;
    }
    if(roomItem.members == 1) {
      roomItem.name = 'Empty Room';
    }
    if(!roomItem.avatarUrl) {
      roomItem.avatarUrl = DEFAULT_ROOM_AVATAR;
    }
    myRooms.totalUnreadMessages += roomItem.unreadTotal;
    myRooms.rooms.push(roomItem);
  }
  myRooms.rooms.sort((roomOne, roomTwo) => roomOne.updated <= roomTwo.updated);
  return myRooms;
}

export function getRedirectURLOfRoom(roomId, serverName) {
  return 'https://matrix.to/#/' + roomId + '?via=' + serverName;
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
                return getDMRoomsAccountData(firstParticipant).then(accountData => updateDMRoomsAccountData(`${localStorage.getItem('matrix_user_id')}`, accountData))
                                           .then(dataResp => {  return createdRoom.json(); });
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
    window.open('https://matrix.to/#/' + data.roomId + '?via=' + matrixServerName);
  }).catch(e => {
    console.log(e)
  });
}

export async function longPollingSync() {
  const since = localStorage.getItem(MATRIX_SYNC_SINCE) || '';
  let response = await sync(MATRIX_EMPTY_FILTER, since, MATRIX_SYNC_TIMEOUT);

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
    console.log(message);
    processEvents(message);
    localStorage.setItem(MATRIX_SYNC_SINCE, message.next_batch);
    // call again to get the next batch of data
    await longPollingSync();
  }
}