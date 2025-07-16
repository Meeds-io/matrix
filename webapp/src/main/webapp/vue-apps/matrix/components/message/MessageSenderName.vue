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
  <span v-if="sender">
    {{ fullName }}
    <span v-if="isExternal">{{ externalTag }}</span>
  </span>
  <span v-else>
   <v-skeleton-loader
     v-if="!sender"
     type="text"
     height="24"
     width="100"/>
  </span>
</template>

<script>

export default {
  data() {
    return {
      externalTag: `( ${this.$t('matrix.chat.user.external')} )`
    }
  },
  props: {
    sender: {
      type: String,
      default: null
    }
  },
  computed: {
    isExternal() {
      return this.sender?.profile?.dataEntity?.external === 'true';
    },
    fullName() {
      return this.sender?.profile?.fullname;
    }
  }
};
</script>
