/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
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
import MatrixSendByChatMenuAction from './components/MatrixSendByChatMenuAction.vue';

// Sending a document to a conversation belongs to the chat, not to whichever
// add-on happens to be installed beside it: contributed from here, the action is
// present exactly when chat is, and needs no change in Documents. It sits in the
// same "Actions" group as the email connector's "Send by email" — the two read as
// a pair because they are one: hand this file to someone.
const components = {
  'matrix-send-by-chat-menu-action': MatrixSendByChatMenuAction,
};

for (const key in components) {
  Vue.component(key, components[key]);
}

if (extensionRegistry) {
  extensionRegistry.registerExtension('DocumentMenu', 'menuActionMenu', {
    id: 'send-by-chat',
    labelKey: 'documents.label.sendByChat',
    align: 'center',
    sortable: true,
    cssClass: 'text-truncate',
    width: '190px',
    // Right after "Send by email" (91/92): the two ways of handing a file to a
    // person belong next to each other.
    rank: 93,
    parent: 'actionsGroup',
    enabled: (file) => {
      return file && !file.folder && !file.cloudDriveFolder;
    },
    enabledForMultiSelection: () => false,
    // Nested under componentOptions, which is where the Documents menu looks —
    // a top-level vueComponent registers without error and never renders.
    componentOptions: {
      vueComponent: Vue.options.components['matrix-send-by-chat-menu-action'],
    },
  });
}
