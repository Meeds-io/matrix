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
  <v-list
    class="ma-0 py-0 text-no-wrap width-fit-content
    border-box-sizing border-radius"
    dense>
    <v-list-item class="ma-0 height-auto px-2 py-1">
      <div class="d-flex">
        <emoji-picker-button
          use-quick-emojis
          @select-emoji="$emit('reaction', $event)" />
        <v-divider
          class="mx-2"
          vertical />
        <v-btn
          width="28"
          height="28"
          min-width="28"
          class="pa-0"
          icon
          @click="$emit('reply')">
          <v-icon
            size="16"
            class="icon-default-color">
            fas fa-reply
          </v-icon>
        </v-btn>
        <v-menu
          v-if="displayEditMenu"
          open-on-click
          close-on-content-click
          offset-y>
          <template v-slot:activator="{ on }">
            <v-btn
              v-on="on"
              width="28"
              height="28"
              min-width="28"
              :title="$t('matrix.chat.openMessageMenu')"
              icon
              @touchstart.stop="0"
              @touchend.stop="0"
              @mousedown.stop="0"
              @mouseup.stop="0"
              @click.prevent.stop="openMenu">
              <v-icon
                size="16"
                class="icon-default-color">
                fa-ellipsis-v
              </v-icon>
            </v-btn>
          </template>
          <v-list>
            <v-list-item
              :title="$t('matrix.chat.label.editMessage')"
              :aria-label="$t('matrix.chat.label.editMessage')"
              @click="handleEditMessage">
              <v-icon
                class="ma-auto"
                size="16">
                fa-edit
              </v-icon>
              {{ $t('matrix.chat.label.editMessage') }}
            </v-list-item>
          </v-list>
        </v-menu>
      </div>
    </v-list-item>
  </v-list>
</template>

<script>

export default {
  props: {
    message: {
      type: Object,
      default: {},
    },
  },
  data() {
    return {
      showMoreActions: false,
    };
  },
  computed: {
    displayEditMenu() {
      return matrixUserId === this.message.sender && this.message.content.msgtype === 'm.text';
    }
  },
  methods: {
    openMenu() {
      if(!this.showMoreActions) {
        this.$root.$emit('open-message-child-menu');
      } else {
        this.$root.$emit('close-message-child-menu');
      }
      this.showMoreActions = !this.showMoreActions;
    },
    handleEditMessage() {
      this.$root.$emit('close-message-child-menu');
      this.$root.$emit('chat-edit-message', this.message);
    }
  },
};
</script>
