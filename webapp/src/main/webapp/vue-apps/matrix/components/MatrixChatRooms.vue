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
    v-if="rooms?.length || hasMessageResults"
    class="d-flex flex-column">
    <div
      id="initialRoomsElement"
      ref="initialRoomsElement">
      <matrix-chat-room
        v-for="room in initialRooms"
        :key="room.id"
        :selected-room="selectedRoom"
        :from-room-list="fromRoomList"
        :room="room" />
    </div>
    <div
      id="remainingRoomsElement"
      v-if="rooms?.length > limit"
      v-intersect="onIntersect">
      <matrix-chat-room
        v-if="displayRemainingRooms"
        v-for="room in remainingRooms"
        :key="room.id"
        :selected-room="selectedRoom"
        :from-room-list="fromRoomList"
        :room="room" />
    </div>
    <!-- WhatsApp-style: conversations whose messages contain the typed word. -->
    <template v-if="hasMessageResults">
      <div class="px-5 pt-4 pb-1 text-uppercase text-caption text-sub-title font-weight-bold">
        {{ $t('matrix.chat.search.messagesSection') }}
      </div>
      <div
        v-for="result in messageResults"
        :key="`message-result-${result.room.id}`"
        class="d-flex chat-room-item position-relative py-3 px-5 clickable"
        @click="openMessageResult(result)">
        <v-avatar
          :tile="!result.room.directChat"
          :class="{'rounded-lg': !result.room.directChat}"
          width="52"
          min-width="52"
          height="52">
          <img
            :src="result.room.avatarUrl"
            loading="lazy"
            alt="">
        </v-avatar>
        <div class="overflow-hidden ps-2 flex-grow-1">
          <div class="chat-room-name text-truncate text-title text-subtitle-1">
            {{ result.room.name }}
          </div>
          <div
            class="chat-room-last-message text-truncate text-sub-title text-caption"
            v-html="highlightSnippet(result.snippet)">
          </div>
        </div>
        <div
          v-if="result.count > 1"
          class="ps-3 align-self-center">
          <v-avatar
            size="24"
            class="align-center align-content-center grey-lighten1-background white--text text-font-small-size">
            {{ result.count <= 99 ? result.count : '99+' }}
          </v-avatar>
        </div>
      </div>
    </template>
  </div>
  <div v-else-if="!loading" class="d-flex full-height align-center justify-center full-width">
    <div>
      <v-icon
        class="mx-auto mb-5"
        size="60">
        far fa-comments
      </v-icon>
      <p class="text-subtitle">{{ $t('matrix.chat.no.rooms') }}</p>
    </div>
  </div>
</template>
<script>

export default {
  data() {
    return {
      limit: 30,
      displayRemainingRooms: false,
    };
  },
  props: {
    rooms: {
      type: Array,
      default: () => []
    },
    loading: {
      type: Boolean,
      default: false
    },
    selectedRoom: {
      type: Object,
      default: null
    },
    fromRoomList: {
      type: Boolean,
      default: false
    },
    searchTerm: {
      type: String,
      default: null
    },
    messageResults: {
      type: Array,
      default: () => []
    }
  },
  created() {
    document.addEventListener('matrix-joined-room', this.addJoinedRoom);
  },
  beforeDestroy() {
    document.removeEventListener('matrix-joined-room', this.addJoinedRoom);
  },
  computed: {
    initialRooms() {
      return this.rooms.slice(0, this.limit);
    },
    remainingRooms() {
      return this.rooms.slice(this.limit, this.rooms.length);
    },
    hasMessageResults() {
      return !!this.searchTerm && this.messageResults?.length > 0;
    }
  },
  methods: {
    openMessageResult(result) {
      document.dispatchEvent(new CustomEvent(this.$chatConstants.ACTION_OPEN_CHAT_ROOM,
        {
          detail: {
            room: result.room,
            fromRoomList: this.fromRoomList
          }
        }));
      localStorage.setItem('lastOpenedRoomId', result.room.id);
    },
    highlightSnippet(text) {
      const escaped = (text || '').replace(/[&<>"]/g, character => ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;'
      }[character]));
      const term = (this.searchTerm || '').trim();
      if (!term) {
        return escaped;
      }
      const escapedTerm = term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      return escaped.replace(new RegExp(`(${escapedTerm})`, 'gi'), '<span class="primary--text font-weight-bold">$1</span>');
    },
    addJoinedRoom(event) {
      const roomExistsIndex = this.rooms.findIndex(room => room.id === event.detail.id);
      if (roomExistsIndex < 0) {
        this.rooms.unshift(event.detail);
      } else if (this.rooms[roomExistsIndex]) {
        this.rooms[roomExistsIndex].name = event.detail.name || this.rooms[roomExistsIndex].name;
        this.rooms[roomExistsIndex].avatarUrl = event.detail.avatarUrl || this.rooms[roomExistsIndex].avatarUrl;
        if (!this.rooms[roomExistsIndex].directChat) {
          this.rooms[roomExistsIndex].members?.unshift(event.detail.members);
        }
      }
    },
    onIntersect(entries) {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          this.displayRemainingRooms = true;
        }
      });
    }
  }
};
</script>
