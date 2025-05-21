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
  <div class="d-flex">
    <span
      v-if="sender"
      class="text-color font-weight-bold">
      {{ displayName }}
      <span v-if="isExternalUser">{{ externalTag }}</span>
    </span>
    <span v-else>
     <v-skeleton-loader
       v-if="!sender"
       type="text"
       height="24"
       width="100" />
    </span>
  </div>
</template>

<script>
export default {
  props: {
    userId: {
      type: String,
      default: null
    }
  },
  data() {
    return {
      sender: null
    };
  },
  computed: {
    displayName() {
      return this.sender?.profile?.fullname ?? this.userId;
    },
    isExternalUser() {
      return this.sender?.profile?.dataEntity?.external === 'true';
    },
    externalTag() {
      return `( ${this.$t('matrix.chat.user.external')} )`;
    }
  },
  created() {
    if (!this.sender) {
      this.$matrixService.getUserByMatrixId(this.userId).then(user => {
        this.sender = user;
      });
    }
  }
};
</script>
