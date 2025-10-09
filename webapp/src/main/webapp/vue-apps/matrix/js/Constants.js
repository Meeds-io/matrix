/*
 * This file is part of the Meeds project (https://meeds.io/).
 * 
 * Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

export const chatConstants = {
  DEFAULT_ROOM_AVATAR: '/matrix/img/room-default.jpg',

  // Static String for action names
  ACTION_OPEN_CHAT_ROOM: 'meeds-chat-open-room',

  ACTION_CHAT_OPEN_QUICK_CREATE_DISCUSSION_DRAWER: 'meeds-open-quick-create-discussion-drawer',

  ACTION_CHAT_OPEN_DISCUSSION_DRAWER: 'open-discussion-drawer',

  ENTER_CODE_KEY: 13,

  MESSAGES_LOAD_LIMIT: 30,

  // IndexedDB configuration
  DB_SETTINGS: {
    DB_NAME: 'CHAT',
    DB_VERSION: 5,
    DB_STORES: {
      SETTINGS: 'SETTINGS',
      READ_RECEIPTS: 'READ_RECEIPTS',
      UNSEEN_MESSAGES: 'UNSEEN_MESSAGES'
    }
  }

};
