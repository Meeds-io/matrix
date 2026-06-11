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
  <div>
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
    <div v-if="isChatAuthorized" class="d-flex">
      <div class="flex-grow-1">
        <div class="d-flex flex-column">
          <span class="text-truncate font-weight-bold mt-2">
            {{ $t('matrix.chat.room.enabled.default') }}
          </span>
          <span class="text-truncate text-subtitle mt-1 mb-2">
            {{ $t('matrix.chat.room.enabled.default.description') }}
          </span>
        </div>
      </div>
      <v-switch
        v-model="isChatEnabledByDefault"
        :ripple="false"
        color="primary"
        class="mt-2"
        hide-details
        @change="enableChatByDefault"/>
    </div>
  </div>
</template>

<script>

export default {
  props: {
    spaceTemplate: {
      type: Object,
      default: null
    }
  },
  data: () => ({
    isChatAuthorized: true,
    isChatEnabledByDefault: true,
    chatAuthorizedLabel: 'meeds.chat.authorized',
    chatEnabledByDefaultLabel: 'meeds.chat.enabledByDefault',
  }),
  created() {
    this.initializeProperties();
    console.log('Initiation of space template settings !', this.spaceTemplate);
  },
  methods: {
    initializeProperties() {
      if (!this.spaceTemplate.extendedProperties) {
        this.spaceTemplate.extendedProperties = {};
        this.spaceTemplate.extendedProperties[this.chatAuthorizedLabel] = true;
        this.spaceTemplate.extendedProperties[this.chatEnabledByDefaultLabel] = true;
      } else {
        this.isChatAuthorized = !this.spaceTemplate.extendedProperties[this.chatAuthorizedLabel] || this.spaceTemplate.extendedProperties[this.chatAuthorizedLabel] === 'true';
        this.isChatEnabledByDefault = !this.spaceTemplate.extendedProperties[this.chatEnabledByDefaultLabel] || this.spaceTemplate.extendedProperties[this.chatEnabledByDefaultLabel] === 'true';
        this.spaceTemplate.extendedProperties[this.chatAuthorizedLabel] = this.isChatAuthorized;
        this.spaceTemplate.extendedProperties[this.chatEnabledByDefaultLabel] = this.isChatEnabledByDefault;
      }
    },
    authorizeChat() {
      console.log('authorize chat', this.isChatAuthorized);
      this.spaceTemplate.extendedProperties[this.chatAuthorizedLabel] = this.isChatAuthorized;
    },
    enableChatByDefault() {
      console.log('Enable chat by default', this.isChatEnabledByDefault);
      this.spaceTemplate.extendedProperties[this.chatEnabledByDefaultLabel] = this.isChatEnabledByDefault;
    },
  }
};
</script>
