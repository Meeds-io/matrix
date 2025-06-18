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
        v-if="displayed">
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
                v-model="spaceChatEnabled"
                @change="enableDisableChat"
                class="pt-5"
                :aria-label="this.$t(`matrix.chat.spaceSettings.switch.label.${this.switchAriaLabel}`)" />
            </v-list-item-action>
          </v-list-item>
        </v-list>
      </v-card>
    </template>
  </v-app>
</template>
<script>
export default {
  data: () => ({
    id: `ChatApp${parseInt(Math.random() * 10000)}`,
    spaceChatEnabled: false,
    displayed: true,
  }),
  created() {
    //check if space's chat is enabled
    document.addEventListener('hideSettingsApps', (event) => {
      if (event?.detail && this.id !== event.detail) {
        this.displayed = false;
      }
    });
    document.addEventListener('showSettingsApps', () => this.displayed = true);
  },
  computed: {
    switchAriaLabel() {
      return this.spaceChatEnabled && 'disable' || 'enable';
    },
  },
  methods: {
    enableOrDisableChat() {
      console.log('enable or disable Matrix chat in the space');
    },
  }
};
</script>
