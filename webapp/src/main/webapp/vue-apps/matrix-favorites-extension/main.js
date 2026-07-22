/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2025 Meeds Association contact@meeds.io
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
import * as matrixService from '../matrix/js/MatrixService.js';
import { chatConstants } from '../matrix/js/Constants.js';
import ChatRoomFavoriteItem from './components/ChatRoomFavoriteItem.vue';

// This bundle loads site-wide (top-bar Favorites drawer), where the chat portlet's
// main.js has not run, so wire the services the favorite item needs ourselves.
if (!Vue.prototype.$matrixService) {
  window.Object.defineProperty(Vue.prototype, '$matrixService', { value: matrixService });
}
if (!Vue.prototype.$chatConstants) {
  window.Object.defineProperty(Vue.prototype, '$chatConstants', { value: chatConstants });
}

Vue.component('chat-room-favorite-item', ChatRoomFavoriteItem);

extensionRegistry.registerExtension('favorite', 'favorite-type', {
  rank: 70,
  id: 'chatRoom',
  name: 'Chat',
  icon: 'fa-comments',
});

extensionRegistry.registerComponent('favorite-chatRoom', 'favorite-drawer-item', {
  id: 'chatRoom',
  vueComponent: Vue.options.components['chat-room-favorite-item'],
});
