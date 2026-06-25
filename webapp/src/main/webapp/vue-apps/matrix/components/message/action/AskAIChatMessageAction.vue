<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2025 Meeds Association contact@meeds.io
 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 3 of the License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.
 You should have received a copy of the GNU Lesser General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
-->
<template>
  <v-list-item
    v-if="uxBindings && uxBindings.length"
    class="chat-action-menu-item"
    :title="$t('matrix.chat.ai.askAgent')"
    :aria-label="$t('matrix.chat.ai.askAgent')"
    @click="askAi">
    <v-icon
      class="me-1"
      size="16">
      fa-concierge-bell
    </v-icon>
    {{ $t('matrix.chat.ai.askAgent') }}
  </v-list-item>
</template>
<script>
// Shared across all per-message instances so the bindings are fetched only once.
let chatMessageUxBindingsPromise = null;

function loadChatMessageUxBindings() {
  if (!chatMessageUxBindingsPromise) {
    chatMessageUxBindingsPromise = new Promise(resolve => window.require(['SHARED/AiAgentCommon'], resolve))
      .then(() => Vue.prototype.$aiUxBindingService.getUxBindings('chat', 'chatMessage'));
  }
  return chatMessageUxBindingsPromise;
}

// Preload as soon as the chat bundle loads so the button is ready before any
// message menu is opened (avoids the button flashing in/out on the first open).
if (eXo.env.portal.aiConciergeEnabled) {
  loadChatMessageUxBindings();
}

export default {
  props: {
    message: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    uxBindings: [],
    loading: false,
  }),
  created() {
    this.init();
  },
  methods: {
    async init() {
      if (!eXo.env.portal.aiConciergeEnabled) {
        return;
      }
      this.uxBindings = await loadChatMessageUxBindings();
    },
    askAi() {
      this.loading = true;
      // Pass the message text inline as objectContent (and NO objectId) so the AI
      // drawer uses {content_body} directly instead of fetching it server-side
      // (there is no chat content-plugin).
      window.require(['SHARED/AiAgentChat'], drawer => {
        Promise.resolve(drawer.openDrawer({
          uxBindings: this.uxBindings,
          properties: {
            objectType: 'chatMessage',
            objectTitle: this.message?.content?.body,
            objectContent: this.message?.content?.body || '',
          },
        })).finally(() => this.loading = false);
      });
      this.$emit('close');
    },
  },
};
</script>
