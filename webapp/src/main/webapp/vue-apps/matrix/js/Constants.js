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

  /**
   * Asks whichever chat instance is present on the page to open its rooms
   * drawer. Dispatched on `document` so a caller outside the chat application —
   * the Application Center quick action, for instance — can open it without
   * holding a reference to the component.
   */
  ACTION_OPEN_CHAT_DRAWER: 'meeds-chat-open-drawer',

  /**
   * Announces the total unread messages count on `document`, so a display
   * outside the chat application can mirror it. The count is authoritative in
   * the browser: it is derived from the Matrix client sync state and never
   * computed server side.
   */
  CHAT_TOTAL_UNREAD_CHANGED: 'meeds-chat-total-unread-changed',

  /**
   * Asks the chat instance to re-announce its current total unread count. A
   * display mounted after the last change — the Application Center badge on a
   * tile rendered when the launcher opens, typically — would otherwise stay
   * empty until the next message arrives.
   */
  CHAT_TOTAL_UNREAD_REQUEST: 'meeds-chat-total-unread-request',

  /**
   * Announced on `document` once a chat instance has registered its own
   * document listeners, so a caller outside the chat application knows its
   * events will actually be heard. The topbar container is a static JSP element
   * present from HTML parse, long before the AMD bundle and its i18n have
   * loaded: finding that element is not evidence that anyone is listening yet.
   */
  CHAT_READY: 'meeds-chat-ready',

  /**
   * Asks a chat instance to re-announce its readiness, for a caller that
   * started listening after the announcement was made.
   */
  CHAT_READY_REQUEST: 'meeds-chat-ready-request',

  ENTER_CODE_KEY: 13,

  MESSAGES_LOAD_LIMIT: 25,

  // IndexedDB configuration
  DB_SETTINGS: {
    DB_NAME: 'CHAT',
    DB_VERSION: 6,
    DB_STORES: {
      SETTINGS: 'SETTINGS',
      READ_RECEIPTS: 'READ_RECEIPTS',
      UNSEEN_MESSAGES: 'UNSEEN_MESSAGES',
      CACHE: 'CACHE'
    }
  }

};
