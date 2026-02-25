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
    :id="roomItemTagId"
    :class="{
      'background-grey-primary': isActive && !$root?.fullPageMode,
      'grey-lighten1-background-opacity-3': isActive && $root?.fullPageMode,
      'grey-lighten1-background-opacity-4': isSelected && $root?.fullPageMode,
      'no-select': isMobile}"
    class="d-flex chat-room-item position-relative py-3 px-5 clickable"
    v-touch-hold="openMenu"
    @click="openRoom"
    @mouseenter="hover = true"
    @mouseleave="hover = false;">
    <v-badge
      :color="presenceColor"
      :value="isPrivateRoom"
      class="ma-0 pa-0"
      content=""
      offset-x="13"
      offset-y="10"
      width="12"
      height="12"
      bordered
      bottom
      overlap
      dot>
      <v-avatar
        :tile="!isPrivateRoom"
        :class="{'rounded-lg': !isPrivateRoom}"
        width="52"
        min-width="52"
        height="52">
        <img
          :src="avatarUrl"
          loading="lazy"
          alt="">
      </v-avatar>
    </v-badge>
    <div
      class="overflow-hidden ps-2 flex-grow-1">
      <div
        :id="`room-name-${room.id}`"
        class="chat-room-name text-truncate text-title text-subtitle-1">
        {{ room.name }}
        <span v-if="room.external">
          {{ externalTag }}
        </span>
      </div>
      <matrix-room-last-message :room="room" />
    </div>
    <div class="ps-3">
      <div class="last-message-timestamp text-subtitle">
        {{ getUpdateTime(room) }}
      </div>
      <div class="pull-right d-flex">
        <v-avatar
          v-if="showMessageBadge"
          size="24"
          class="align-center align-content-center error-color-background white--text text-font-small-size">
          {{ room.unreadMessages <= 99 ? room.unreadMessages : '99+' }}
        </v-avatar>
        <matrix-room-action-menu
          v-else-if="isActive && !isMobile"
          ref="menu"
          :room="room"
          :attached-id="roomItemTagId"
          @open="menuOpen = true"
          @close="menuOpen = false" />
        <v-badge
          v-if="!isActive && room.muted"
          color="#bc4343"
          :value="hasUnreadMessages"
          class="ma-0 pa-0"
          content=""
          offset-x="8"
          offset-y="8"
          width="12"
          height="12"
          bordered
          top
          overlap
          dot>
          <v-icon size="16">
            fas fa-bell-slash
          </v-icon>
        </v-badge>
      </div>
    </div>
  </div>
</template>
<script>

export default {
  data() {
    return {
      menu: false,
      externalTag: `( ${this.$t('matrix.chat.user.external')} )`,
      menuOpen: false,
      hover: false,
      pressTimer: null
    };
  },
  props: {
    room: {
      type: Object,
      default: null,
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
  created() {
    this.$nextTick().then(() => {
      if (this.room?.directChat) {
        this.getUserStatus();
      }
    });
  },
  computed: {
    roomItemTagId() {
      const rawId = `room${this.room.spaceId || this.room.dmMemberId}${this.fromRoomList ? 'fromRoomList' : ''}`;
      return rawId.replaceAll(/[^A-Za-z0-9_-]/g, '_');
    },
    isSelected() {
      return this.selectedRoom?.id === this.room?.id;
    },
    isActive() {
      return this.hover || this.menuOpen;
    },
    showMessageBadge() {
      return this.hasUnreadMessages && !this.isActive && !this.room.muted;
    },
    isMobile() {
      return this.$root.isMobile;
    },
    isPrivateRoom() {
      return this.room?.directChat;
    },
    avatarUrl() {
      return this.room?.avatarUrl;
    },
    presence() {
      return this.room?.presence;
    },
    presenceColor() {
      return this.presence && this.$root.statusMap[this.presence];
    },
    hasUnreadMessages() {
      return this.room.unreadMessages > 0;
    }
  },
  methods: {
    getUserStatus() {
      return this.$userStateService.getUserStatus(this.room.dmMemberId).then(data => {
        this.room.presence = data?.status;
      });
    },
    openRoom() {
      document.dispatchEvent(new CustomEvent(this.$chatConstants.ACTION_OPEN_CHAT_ROOM, 
        {
          detail: {
            room: this.room,
            fromRoomList: this.fromRoomList}
        }));
      localStorage.setItem('lastOpenedRoomId', this.room.id);
    },
    getUpdateTime(room) {
      return this.$matrixService.formatDate(room.updated);
    },
    openMenu() {
      this.$root.$emit('open-room-action-menu', this.room);
    }
  }
};
</script>
