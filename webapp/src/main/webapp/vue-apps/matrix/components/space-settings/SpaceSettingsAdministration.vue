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
      <!--
        One row, drawn as the rows of the user Settings page; Space settings
        draws the card around it. Its registration already read that the row
        shows, so it is drawn at once, its state line empty until read.
      -->
      <div
        id="chatSpaceSetting"
        v-if="displayed">
        <v-list-item two-line>
          <v-list-item-content>
            <v-list-item-title class="text-color">
              {{ $t('matrix.chat.spaceSettings.title') }}
            </v-list-item-title>
            <v-list-item-subtitle class="chat-space-settings-summary text-wrap">
              {{ summary }}
            </v-list-item-subtitle>
          </v-list-item-content>
          <v-list-item-action>
            <v-switch
              v-show="!displayLoading"
              v-model="spaceChatEnabled"
              @change="enableOrDisableChat(!spaceChatEnabled)"
              class="ma-0"
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
      </div>
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
    statusKnown: false,
    displayed: true,
    progress: true,
    updateInterval: 0,
  }),
  created() {
    this.readStatus();

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
    /**
     * @returns {String} whether the space has its chat room, or is creating or
     *          removing it, as one line; nothing until its status is read
     */
    summary() {
      if (!this.statusKnown) {
        return null;
      }
      switch (this.spaceChatStatus) {
      case 'ENABLE_IN_PROGRESS':
        return this.$t('matrix.chat.spaceSettings.summary.enabling');
      case 'DISABLED_IN_PROGRESS':
        return this.$t('matrix.chat.spaceSettings.summary.disabling');
      case 'DISABLED':
        return this.$t('matrix.chat.spaceSettings.summary.disabled');
      default:
        return this.$t('matrix.chat.spaceSettings.summary.enabled');
      }
    },
    displayLoading() {
      return this.progress || this.spaceChatStatus === 'DISABLED_IN_PROGRESS' || this.spaceChatStatus === 'ENABLE_IN_PROGRESS';
    }
  },
  methods: {
    /**
     * Reads whether the space may have a chat room, then the room's status.
     * The row hides itself when the space may not have one, or when either
     * read fails, as the registration's isEnabled decides; its state line
     * stays empty until the room is read.
     *
     * @returns {Promise} resolved once the status is read or the row hidden
     */
    async readStatus() {
      let authorized;
      try {
        const spaceChatStatus = await this.$matrixService.getChatAuthorizationStatus(this.spaceId);
        authorized = !!spaceChatStatus?.chatAuthorizedForSpace;
      } catch {
        // A permission that cannot be read hides the row, as the registration's isEnabled does
        authorized = false;
      }
      if (!authorized) {
        this.displayed = false;
        this.progress = false;
        return;
      }
      try {
        const room = await this.$matrixService.getSpaceRoom(this.spaceId);
        this.statusKnown = true;
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
      } catch {
        // A room that cannot be read hides the row, as the registration's isEnabled does
        this.spaceChatStatus = 'DISABLED';
        this.displayed = false;
        this.progress = false;
      }
    },
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
