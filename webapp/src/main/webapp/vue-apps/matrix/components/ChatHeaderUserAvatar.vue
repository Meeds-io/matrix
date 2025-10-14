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
    <v-badge
      :color="presenceColor"
      :value="true"
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
        width="36"
        min-width="36"
        height="36">
        <v-img
          :src="avatarUrl"
          :lazy-src="avatarUrl"
          :alt="fullName" />
      </v-avatar>
    </v-badge>
    <span class="mx-5 content-align"> {{ $t('matrix.chat.discussions') }} </span>
  </div>
</template>

<script>
export default {
  props: {
    presence: {
      type: String,
      default: 'available'
    }
  },
  computed: {
    avatarUrl() {
      return this.$currentUserIdentity.profile.avatar;
    },
    fullName() {
      return this.$currentUserIdentity?.profile?.fullname;
    },
    presenceColor() {
      return this.presence && this.$root.statusMap[this.presence];
    },
  }
};
</script>