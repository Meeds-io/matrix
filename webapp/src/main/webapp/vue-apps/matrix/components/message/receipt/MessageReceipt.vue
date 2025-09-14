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
  <exo-user-avatar
    :profile-id="receiptUserId"
    :avatar="avatar"
    :size="size"
    :allow-animation="allowAnimation"
    :extra-class="extraClass"
    popover
    compact>
    <template slot="subTitle">
      <span
        v-if="!sameUser && inCommonConnections"
        class="caption text-bold">
        {{ inCommonConnections }} {{ $t('matrix.user.connections.in.commons') }}
      </span>
    </template>
  </exo-user-avatar>
</template>

<script>

export default {
  data() {
    return {
      user: null
    };
  },
  props: {
    receipt: {
      type: String,
      default: null
    },
    avatar: {
      type: Boolean,
      default: true
    },
    chatRoom: {
      type: Object,
      default: null
    },
    allowAnimation: {
      type: Boolean,
      default: false
    },
    size: {
      type: Number,
      default: 20
    },
    extraClass: {
      type: String,
      default: null
    }
  },
  created() {
    this.getUserInfo()
  },
  computed: {
    receiptUserId() {
      const {receiptInfo, receiptMatrixId} = this;
      if (!receiptInfo) {
        return null
      }
      return receiptInfo.matrixId === receiptMatrixId ? receiptInfo.userId
        : eXo.env.portal.userName;
    },
    receiptMatrixId() {
      return this.receipt?.slice(1, this.receipt?.indexOf(':'));
    },
    room() {
      return this.$parent?.room || this.chatRoom;
    },
    receiptInfo() {
      if (!this.room.spaceId) {
        return this.room
      }
      return this.room?.members?.find?.(member => member.matrixId === this.receiptMatrixId);
    },
    inCommonConnections() {
      return this.user?.profile?.connectionsInCommonCount || 0;
    },
    sameUser() {
      return this.user?.remoteId === eXo.env.portal.userName;
    }
  },
  methods: {
    getUserInfo() {
      if (this.user || this.avatar) {
        return;
      }
      return this.$matrixService.getUserByMatrixId(this.receipt, this.room)
        .then(user => {
          this.user = user;
        })
        .catch((e) => {
          console.error('Error while getting user details', e);
        });
    },
  }
};
</script>
