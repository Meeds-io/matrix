<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2026 Meeds Association contact@meeds.io

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
 Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, U
-->

<template>
  <div v-if="!loading">
    <div class="d-flex">
      <div class="flex-grow-1">
        <div class="d-flex flex-column">
          <span class="text-truncate font-weight-bold mt-2">
            {{ $t('matrix.chat.room.authorized') }}
          </span>
          <span class="text-truncate text-subtitle mt-1">
            {{ $t('matrix.chat.room.authorized.description') }}
          </span>
        </div>
      </div>
      <v-switch
        v-model="isChatAuthorized"
        :ripple="false"
        color="primary"
        class="mt-2"
        hide-details
        @change="authorizeChat"/>
    </div>
  </div>
</template>

<script>

export default {
  props: {
    space: {
      type: Object,
      default: null
    },
    spaces: {
      type: Object,
      default: null
    },
    extendedPermissions: {
      type: Object,
      default: null
    },
  },
  data: () => ({
    isChatAuthorized: true,
    initialChatState: false,
    chatAuthorizedLabel: 'meeds.chat.authorized',
    loading: true,
  }),
  created() {
    if (this.space) {
      this.loading = true;
      this.$matrixService.getSpaceRoom(this.space.id).then(room => {
        if (room) {
          this.isChatAuthorized = !this.extendedPermissions || this.extendedPermissions[this.chatAuthorizedLabel] === 'true';
        } else {
          this.isChatAuthorized = false;
        }
        this.initialChatState = this.isChatAuthorized;
      }).finally(() => this.loading = false);
    }
    // in case of multiple selection, we need to set a default value that will be used if the switch button is not updated
    if (this.spaces) {
      this.$root.$emit('space-administration-permissions-drawer-extended-field-updated', {'key': this.chatAuthorizedLabel, 'value': {[this.chatAuthorizedLabel]: this.isChatAuthorized}});
    }
  },
  methods: {
    authorizeChat() {
      if (this.space && this.isChatAuthorized === this.initialChatState) {
        this.$root.$emit('space-administration-permissions-drawer-extended-field-restored', {'key': this.chatAuthorizedLabel, 'value': {[this.chatAuthorizedLabel]: this.isChatAuthorized}});
      } else {
        this.$root.$emit('space-administration-permissions-drawer-extended-field-updated', {'key': this.chatAuthorizedLabel, 'value': {[this.chatAuthorizedLabel]: this.isChatAuthorized}});
      }
    },
  }
};
</script>
