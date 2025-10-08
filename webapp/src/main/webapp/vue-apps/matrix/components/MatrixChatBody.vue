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
    :class="{'d-flex overflow-hidden': fullPageMode}"
    class="fill-height">
    <v-sheet
      v-if="showRoomList"
      :max-width="420"
      :min-width="fullPageMode ? 420 : undefined"
      :class="{
        'disabled-background': !rooms?.length,
        'background-grey-primary ': fullPageMode
      }"
      class="fill-height overflow-y-auto flex-shrink-1 flex-grow-1 overflow-x-hidden specific-scrollbar">
      <matrix-chat-rooms
        :rooms="rooms"
        :selected-room="selectedRoom"
        :from-room-list="fromRoomList"
        :loading="loading" />
    </v-sheet>
    <div
      v-if="showMessages"
      class="d-flex flex-column flex-grow-1 fill-height">
      <div class="flex-grow-1 d-flex flex-column overflow-hidden">
        <room-messages
          v-if="selectedRoom"
          ref="roomMessages"
          :room="selectedRoom"
          :expanded="fullPageMode"
          :is-input-focused="isInputFocused"
          @loading="$emit('loading', $event)"
          class="flex-grow-1 overflow-x-hidden" />
      </div>
      <div class="flex-shrink-0 px-4 py-2">
        <message-composer
          v-if="selectedRoom"
          ref="messageComposer"
          :room="selectedRoom"
          @mark-room-as-read="markRoomAsRead"
          @scroll-to-end="scrollToEnd"
          @input-focus="isInputFocused = $event" />
      </div>
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      isInputFocused: false,
    };
  },
  props: {
    rooms: {
      type: Array,
      default: null
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
    }
  },
  computed: {
    fullPageMode() {
      return this.$root.fullPageMode;
    },
    showMessages() {
      return (this.fromRoomList && this.fullPageMode) || !this.fromRoomList;
    },
    showRoomList() {
      return (!this.fromRoomList && this.fullPageMode) || this.fromRoomList;
    }
  },
  async mounted() {
    await this.openDiscussion();
  },
  methods: {
    async openDiscussion() {
      setTimeout(() => {
        this.$refs?.messageComposer?.setInputFocus?.();
      }, 200);

      await this.$refs?.roomMessages?.initDiscussion();
      await this.$refs?.roomMessages?.loadAndProcessMessages();

      setTimeout(async () => {
        await this.$refs?.roomMessages?.loadUnseenMessagesData();
      }, 500);
    },
    markRoomAsRead(roomId) {
      this.$refs.roomMessages.markRoomAsRead(roomId);
    },
    scrollToEnd() {
      this.$refs?.roomMessages?.scrollToEnd?.();
    },
    reset() {
      this.$refs?.roomMessages?.reset();
      this.$refs?.messageComposer?.resetComposer();
    }
  }
};
</script>