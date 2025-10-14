<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io

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
  <a :href="url">
    <div class="d-flex">
      <v-badge
        :color="presenceColor"
        :value="room.directChat"
        class="my-auto mx-0 pa-0"
        content=""
        offset-x="10"
        offset-y="10"
        width="12"
        height="12"
        bordered
        bottom
        overlap
        dot>
      <v-avatar
        :tile="!room.directChat"
        :class="{'rounded-lg': !room.directChat}"
        width="36"
        min-width="36"
        height="36">
        <v-img
        :src="room.avatarUrl"
        :lazy-src="room.avatarUrl"
        :alt="room?.name" />
      </v-avatar>
      </v-badge>   
    <span class="mx-3 text-title text-truncate content-align">
      {{room.name}}
      <span v-if="room.external"> {{ externalTag }}</span>
    </span>
   </div>
  </a>
</template>
<script>
export default {
  props: {
    room: {
      type: Object,
      required: true
    }
  },
  computed: {
    presence() {
      return this.room?.presence
    },
    presenceColor() {
      return this.presence && this.$root.statusMap[this.presence];
    },
    url() {
      if(this.room?.directChat && this.room?.userId) {
        return `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/profile/${this.room.userId}`;
      } else if(this.room?.spaceId) {
        return `${eXo.env.portal.context}/s/${this.room.spaceId}`;
      } else {
        return '#';
      }
    },
    externalTag() {
      return `( ${this.$t('matrix.chat.user.external')} )`;
    }
  },
};
</script>