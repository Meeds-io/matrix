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
    v-if="quoted"
    class="d-flex">
    <message-sender-name
      class="text-color font-weight-bold"
      :sender="sender" />
  </div>
  <div v-else>
    <a :href="profileUrl">
      <div class="d-flex">
        <v-img
          :src="avatarUrl"
          :lazy-src="avatarUrl"
          class="meeds-chat-contact-avatar z-index-two ma-0 size-8 d-flex rounded-circle overflow-hidden"
          :alt="displayName"
          cover>
          <template #placeholder>
            <v-skeleton-loader
              type="avatar"
              class="mt-n2 w-100 h-100"
              :loading="true"
              boilerplate />
          </template>
        </v-img>
        <message-sender-name
          class="align-content-start line-height-1 mx-1 text-title text-subtitle-1 text-truncate"
          :style="userNameColor"
          :sender="sender" />
      </div>
    </a>
  </div>
</template>

<script>

export default {
  data() {
    return {
      sender: null,
    }
  },
  props: {
    room: {
      type: Object,
      default: null
    },
    senderId: {
      type: String,
      default: null
    },
    quoted: {
      type: Boolean,
      default: false
    }
  },
  watch: {
    'memberInfo.userId': {
      immediate: true,
      handler: 'getUserInfo',
    }
  },
  computed: {
    senderMatrixId() {
      return this.senderId?.slice(1, this.senderId?.indexOf(':'));
    },
    memberInfo() {
      if (!this.room.spaceId) {
        return this.room
      }
      return this.room?.members?.find?.(member => member.matrixId === this.senderMatrixId);
    },
    profileUrl() {
      return `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/profile/${this.sender?.remoteId}`;
    },
    userNameColor() {
      return this.sender && this.$matrixService.getUserDisplayNameFontColor(this.sender.id);
    },
    isExternal() {
      return this.sender?.profile?.dataEntity?.external === 'true';
    },
    avatarUrl() {
      return this.sender?.profile?.avatar;
    },
    displayName() {
      return this.sender?.profile?.displayName
    },
    fullName() {
      return this.sender?.profile?.fullname;
    }
  },
  created() {
    this.getUserInfo();
  },
  methods: {
    getUserInfo() {
      if (this.sender || !this.memberInfo) {
        return;
      }
      this.$matrixService.getUserIdentity(this.memberInfo.userId).then(identity => {
        this.sender = identity;
      });
    }
  }
};
</script>
