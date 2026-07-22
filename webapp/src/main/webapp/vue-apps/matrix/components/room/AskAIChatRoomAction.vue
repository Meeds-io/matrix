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
    class="ps-2 pe-3 height-auto"
    @click="askAi">
    <v-sheet
      class="d-flex"
      width="28"
      height="36">
      <v-progress-circular
        v-if="loading"
        indeterminate
        size="16"
        width="2"
        color="primary"
        class="ma-auto" />
      <v-icon
        v-else
        class="icon-default-color mx-auto"
        size="16">
        fa-concierge-bell
      </v-icon>
    </v-sheet>
    <span>{{ $t('matrix.chat.ai.askAgent') }}</span>
  </v-list-item>
</template>
<script>
export default {
  props: {
    room: {
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
      // Only the AI addon, when deployed, exposes $aiUxBindingService.
      if (!eXo.env.portal.aiConciergeEnabled) {
        return;
      }
      await new Promise(resolve => window.require(['SHARED/AiAgentCommon'], resolve));
      this.uxBindings = await this.$aiUxBindingService.getUxBindings('chat', 'chatRoom');
    },
    askAi() {
      this.loading = true;
      // Tool-based (no inline content): pass the room id as {content_id}; the agent
      // reads this conversation's messages itself via the get_chat_messages MCP tool.
      window.require(['SHARED/AiAgentChat'], drawer => {
        Promise.resolve(drawer.openDrawer({
          uxBindings: this.uxBindings,
          properties: {
            objectId: this.room?.id,
            objectTitle: this.room?.name,
          },
        })).finally(() => this.loading = false);
      });
    },
  },
};
</script>
