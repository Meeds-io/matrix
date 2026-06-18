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
  <v-app>
    <template>
      <v-card
        id="chatSpaceSetting"
        class="card-border-radius"
        flat
        v-if="displayed & !loading">
        <v-list class="pa-0">
          <v-list-item class="pa-0">
            <v-list-item-content>
              <v-list-item-title class="text-title">
                {{ $t('matrix.chat.spaceSettings.title') }}
              </v-list-item-title>
              <v-list-item-title class="pt-2">
                {{ $t('matrix.chat.spaceSettings.title') }}
              </v-list-item-title>
              <v-list-item-subtitle>
                {{ $t('matrix.chat.spaceSettings.description') }}
              </v-list-item-subtitle>
            </v-list-item-content>
            <v-list-item-action>
              <v-switch
                v-show="!displayLoading"
                v-model="spaceChatEnabled"
                @change="enableOrDisableChat(!spaceChatEnabled)"
                class="pt-5"
                :title="this.$t(`matrix.chat.spaceSettings.switch.label.${this.switchAriaLabel}`)"
                :aria-label="this.$t(`matrix.chat.spaceSettings.switch.label.${this.switchAriaLabel}`)" />
              <v-progress-circular
                v-show="displayLoading"
                color="primary"
                size="32"
                indeterminate
                class="my-auto" />
            </v-list-item-action>
          </v-list-item>
        </v-list>
      </v-card>
    </template>
  </v-app>
</template>
<script>
export default {
  props: {
    spaceId: {
      type: Object,
      default: {},
    }
  },
  data: () => ({
    spaceChatStatus: 'ENABLED',
    displayed: true,
    progress: true,
    updateInterval: 0,
    loading: false
  }),
  created() {
    this.loading = true;
    //check if space's chat is enabled
    this.$matrixService.getChatAuthorizationStatus(this.spaceId).then(spaceChatStatus => {
      if (spaceChatStatus.chatAuthorizedForSpace && spaceChatStatus.chatAuthorizedForSpaceTemplate) {
        this.$matrixService.getSpaceRoom(this.spaceId).then(room => {
          if (room) {
            this.spaceChatStatus = room.status;
            if (room.status === 'ENABLED' || room.status === 'DISABLED') {
              this.progress = false;
            } else {
              this.progress = true;
              this.startCheckingStatus();
            }
          } else {
            this.spaceChatStatus = 'DISABLED';
            this.progress = false;
          }
        }).catch(error => {
          if (error) {
            this.spaceChatStatus = 'DISABLED';
            this.displayed = false;
            this.progress = false;
          }
        });
      } else {
        this.displayed = false;
        this.progress = false;
      }
    }).finally(() => this.loading = false);

    document.addEventListener('hideSettingsApps', (event) => {
      if (event?.detail && this.id !== event.detail) {
        this.displayed = false;
      }
    });
    document.addEventListener('showSettingsApps', () => this.displayed = true);
  },
  computed: {
    switchAriaLabel() {
      return this.spaceChatStatus === 'ENABLED' && 'disable' || 'enable';
    },
    spaceChatEnabled() {
      return this.spaceChatStatus !== 'DISABLED';
    },
    roomEnabled() {
      return this.spaceChatStatus === 'ENABLED';
    },
    roomDisabled() {
      return this.spaceChatStatus === 'DISABLED';
    },
    displayLoading() {
      return this.progress || this.spaceChatStatus === 'DISABLE_IN_PROGRESS' || this.spaceChatStatus === 'ENABLE_IN_PROGRESS';
    }
  },
  methods: {
    enableOrDisableChat(status) {
      this.progress = true;
      this.$matrixService.enableOrDisableChat(this.spaceId, status).then(() => {
        this.startCheckingStatus();
      }).catch(e => {
        this.$root.$emit('alert-message', this.$t(`matrix.chat.${status}.error`, {0: e}), 'error');
        this.progress = false;
      });
    },
    startCheckingStatus() {
      this.updateInterval = setInterval(() => {
       this.$matrixService.getSpaceRoom(this.spaceId).then(room => {
         this.spaceChatStatus = room.status;
         if(room.status === 'ENABLED' || room.status === 'DISABLED') {
           this.progress = false;
           this.$root.$emit('alert-message', this.$t(`matrix.chat.${room.status === 'ENABLED' && 'enable' || 'disable'}.success`), 'success');
           clearInterval(this.updateInterval);
         }
       });
     }, 3000);
    }
  },
};
</script>
