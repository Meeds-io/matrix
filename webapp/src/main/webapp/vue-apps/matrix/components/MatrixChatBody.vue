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
        <matrix-room-messages
          ref="roomMessages"
          :room="selectedRoom"
          :expanded="fullPageMode"
          :is-input-focused="isInputFocused"
          @loading="$emit('loading', $event)"
          class="flex-grow-1 overflow-x-hidden" />
      </div>
      <div class="flex-shrink-0 px-4 py-2">
        <matrix-message-composer
          v-if="selectedRoom"
          ref="messageComposer"
          :room="selectedRoom"
          @mark-room-as-read="markRoomAsRead"
          @scroll-to-end="scrollToEnd"
          @composer-resize="onComposerResize"
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
    },
    parentExpanded: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    fullPageMode() {
      return this.$root.fullPageMode;
    },
    showMessages() {
      return this.selectedRoom && (!this.fromRoomList || this.parentExpanded);
    },
    showRoomList() {
      return this.fromRoomList || this.parentExpanded;
    }
  },
  async mounted() {
    await this.openDiscussion();
  },
  watch: {
    isInputFocused() {
      this.$emit('room-active-changed',
        this.selectedRoom?.id, this.isInputFocused);
    }
  },
  methods: {
    async openDiscussion() {
      setTimeout(() => {
        this.$refs?.messageComposer?.setInputFocus?.();
      }, 500);

      await this.$refs?.roomMessages?.initDiscussion();
      await this.$refs?.roomMessages?.loadAndProcessMessages();
      await this.$refs?.roomMessages?.loadUnseenMessagesData();
    },
    markRoomAsRead(roomId) {
      this.$refs.roomMessages.markRoomAsRead(roomId);
    },
    scrollToEnd() {
      setTimeout(() => {
        this.$refs?.roomMessages?.scrollToEnd?.();
      }, 200);
    },
    onComposerResize() {
      requestAnimationFrame(() => {
        this.$refs?.roomMessages?.keepScrollAtBottom?.();
      });
    },
    reset() {
      this.$refs?.roomMessages?.reset();
      this.$refs?.messageComposer?.resetComposer();
      this.$refs?.roomMessages?.clearUnseenData();
    }
  }
};
</script>