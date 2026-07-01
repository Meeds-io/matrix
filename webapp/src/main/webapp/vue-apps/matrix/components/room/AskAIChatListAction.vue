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
  <v-btn
    v-if="uxBindings && uxBindings.length"
    :loading="loading"
    :title="$t('matrix.chat.ai.askAgent')"
    :aria-label="$t('matrix.chat.ai.askAgent')"
    icon
    @click="askAi">
    <v-icon
      class="icon-default-color"
      size="20">
      fa-concierge-bell
    </v-icon>
  </v-btn>
</template>
<script>
export default {
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
      await new Promise(resolve => window.require(['SHARED/AiAgentCommon'], resolve));
      this.uxBindings = await this.$aiUxBindingService.getUxBindings('chat', 'chatList');
    },
    askAi() {
      this.loading = true;
      // Tool-based (no inline content): the agent retrieves my unread conversations
      // itself via the get_unread_chat_messages MCP tool.
      window.require(['SHARED/AiAgentChat'], drawer => {
        Promise.resolve(drawer.openDrawer({
          uxBindings: this.uxBindings,
          properties: {
            objectTitle: this.$t('matrix.chat.ai.askAgent'),
          },
        })).finally(() => this.loading = false);
      });
    },
  },
};
</script>
