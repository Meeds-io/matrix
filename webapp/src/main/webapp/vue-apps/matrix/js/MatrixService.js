import * as chatConstants from './Constants.js';

// variables that will be get from the server
const MATRIX_SERVER_URL='http://localhost:8008';
const JWT_COOKIE_NAME = 'matrix_jwt_token';


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
    console.log(resp.rooms);
    return toRoomObject(resp.rooms.join, currentMemberId);
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

export function getDMRoom(matrixIDUserOne, matrixIdUserTwo) {
  const payLoad = {
                     "preset": "trusted_private_chat",
                     "visibility": "private",
                     "invite": [
                       "@jdubois:matrix-dev.exoplatform.org"
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
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    } else {
      return resp.json();
    }
  });
}
export function toRoomObject(rooms, currentMemberId) {
  let myRooms = [];
  for (const property in rooms) {
    let roomItem = {};
    let members = [];
    const roomEvents = rooms[property].state.events.length ? rooms[property].state.events : rooms[property].timeline.events;
    roomEvents.forEach(e => {
      if(e.type === 'm.room.topic'){
        roomItem.topic = e.content.topic;
      }
      if(e.type === 'm.room.name'){
        roomItem.name = e.content.name;
      }
      if(e.type === 'm.room.member' && e.content.membership === 'join'){
        let member = {};
        member.id = e.sender;
        member.name = e.content.displayname;
        members.push(member)
      }
    });
    roomItem.members = members;
    if(!roomItem.name && members.length == 2) {
      roomItem.name = members.find(m => m.id !== currentMemberId).name;
    }
    roomItem.id = property;
    roomItem.type = 'space';
    roomItem.isEnabledUser = true;
    roomItem.isExternal = false;
    roomItem.isFavorite = false;
    roomItem.lastMessage = '';
    roomItem.unreadTotal = 0;
    roomItem.avatarUrl = chatConstants.chatConstants.DEFAULT_ROOM_AVATAR;
    myRooms.push(roomItem);
  }
  return myRooms;
}

export function getRedirectURLOfRoom(roomId, serverName) {
  return 'https://matrix.to/#/' + roomId + '?via=${serverName}';
}
