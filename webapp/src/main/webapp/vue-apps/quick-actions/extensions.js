/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
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

import {chatConstants} from '../matrix/js/Constants.js';
import {ensureChatApp} from './js/chatApp.js';

/**
 * Renders the chat unread counter on the Chat tile in the Application Center.
 * <p>
 * `badgeName` is what App Center matches on to know this badge has a custom
 * rendering: without it, every other application would fall back to its own
 * default rendering being replaced.
 */
extensionRegistry.registerComponent('AppCenterAppBadge', 'badge', {
  id: 'chatUnreadBadge',
  badgeName: 'chatUnread',
  vueComponent: Vue.options.components['matrix-chat-app-badge'],
  rank: 10,
  isEnabled: params => params?.badgeName === 'chatUnread',
});

extensionRegistry.registerExtension('QuickAction', 'Extension', {
  id: 'chat',
  icon: 'fa-comments',
  name: 'quickActions.chat.name',
  description: 'quickActions.chat.description',
  // The catalog entry is authorized for every platform user, but a deployment
  // can disable both room types or restrict chat to some groups. App Center
  // hides a Drawer application whose quick action reports itself disabled, so
  // this is what keeps the tile — and the Matrix client its click would mount —
  // away from a user who has no usable chat.
  enabled: () => typeof meedsChat !== 'undefined' && !!meedsChat.chatEnabled,
  click: () => ensureChatApp()
    .then(() => document.dispatchEvent(new CustomEvent(chatConstants.ACTION_OPEN_CHAT_DRAWER))),
});
