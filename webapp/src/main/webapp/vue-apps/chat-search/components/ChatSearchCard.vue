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
  <div
    class="chat-search-card d-flex align-center clickable text-start pa-3"
    style="border: 1px solid var(--allPagesBorderColor, #E1E8EE); border-radius: 8px; min-height: 76px;"
    @click="open">
    <v-avatar
      :tile="!result.directChat"
      :class="{'rounded-lg': !result.directChat}"
      size="44"
      min-width="44"
      class="me-3">
      <img
        v-if="result.avatarUrl"
        :src="result.avatarUrl"
        loading="lazy"
        alt="">
      <v-icon v-else color="primary" size="22">fa-comment-dots</v-icon>
    </v-avatar>
    <div class="flex-grow-1 overflow-hidden text-start">
      <div class="d-flex align-center">
        <span class="text-truncate text-title text-subtitle-1 flex-grow-1">
          {{ conversationTitle }}
        </span>
        <span
          v-if="formattedDate"
          class="text-caption text-sub-title text-no-wrap ms-2 flex-shrink-0">
          {{ formattedDate }}
        </span>
      </div>
      <div class="d-flex align-center">
        <div
          class="text-truncate text-sub-title text-body-2 flex-grow-1"
          v-html="highlightedSnippet">
        </div>
        <v-avatar
          v-if="matchCount > 1"
          size="20"
          class="ms-2 flex-shrink-0 align-center align-content-center grey-lighten1-background white--text text-font-small-size">
          {{ matchCount }}
        </v-avatar>
      </div>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    id: {
      type: String,
      default: null,
    },
    result: {
      type: Object,
      default: () => ({}),
    },
    term: {
      type: String,
      default: '',
    },
  },
  computed: {
    conversationTitle() {
      return this.result.conversationTitle || this.result.conversationId;
    },
    matchCount() {
      return this.result.matchCount || 1;
    },
    formattedDate() {
      if (!this.result.timestamp) {
        return '';
      }
      return new Date(this.result.timestamp).toLocaleDateString(
        eXo?.env?.portal?.language || undefined,
        { day: 'numeric', month: 'short', year: 'numeric' });
    },
    highlightedSnippet() {
      const escaped = (this.result.text || '').replace(/[&<>"]/g, character => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;'
      }[character]));
      const term = (this.term || '').trim();
      if (!term) {
        return escaped;
      }
      const escapedTerm = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      return escaped.replace(new RegExp(`(${escapedTerm})`, 'gi'), '<span class="primary--text font-weight-bold">$1</span>');
    },
  },
  methods: {
    open() {
      const roomId = this.result.conversationId;
      if (!roomId) {
        return;
      }
      // The chat button (present in the top navigation on every page) listens for this
      // document event and opens the conversation drawer. We fetch the full room first
      // so the drawer has everything it needs (avatar, members, direct-chat flag…).
      fetch(`/matrix/rest/matrix/byRoomId?roomId=${encodeURIComponent(roomId)}`, { credentials: 'include' })
        .then(resp => resp.ok ? resp.json() : null)
        .then(room => {
          if (room) {
            document.dispatchEvent(new CustomEvent('meeds-chat-open-room', { detail: { room } }));
          }
        })
        .catch(error => console.error('Failed to open chat room from search:', error));
    },
  },
};
</script>
