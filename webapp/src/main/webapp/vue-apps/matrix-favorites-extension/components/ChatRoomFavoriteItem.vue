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
  <v-list-item
    v-if="roomName"
    @click="open">
    <v-list-item-icon class="me-3 my-auto">
      <v-card
        :min-width="iconWidth"
        class="d-flex justify-center no-border-radius"
        color="transparent"
        flat>
        <v-icon :size="iconSize">fa-comments</v-icon>
      </v-card>
    </v-list-item-icon>
    <v-list-item-content>
      <v-list-item-title class="text-truncate text-color">
        {{ roomName }}
      </v-list-item-title>
      <v-list-item-subtitle
        v-if="expanded"
        class="d-flex align-center full-width overflow-hidden pt-2px">
        <v-icon class="me-1" size="12">fa-comments</v-icon>
        {{ $t('matrix.favorite.subtitle') }}
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action>
      <favorite-button
        :id="id"
        :favorite="isFavorite"
        :type-label="$t('matrix.favorite.typeLabel')"
        type="chatRoom"
        @removed="removed"
        @remove-error="removeError" />
    </v-list-item-action>
  </v-list-item>
</template>
<script>
export default {
  props: {
    id: {
      type: String,
      default: null,
    },
    clickCallback: {
      type: Function,
      default: null,
    },
    expanded: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    isFavorite: true,
    room: null,
  }),
  computed: {
    iconWidth() {
      return this.expanded ? 40 : 30;
    },
    iconSize() {
      return this.expanded ? 34 : 24;
    },
    roomName() {
      return this.room?.name || '';
    },
  },
  async created() {
    if (!this.id) {
      return;
    }
    // Reuse the chat's cached rooms so DMs and space rooms get their proper title.
    try {
      const cached = await this.$matrixService.retrieveCachedRooms();
      const rooms = cached ? JSON.parse(cached) : [];
      this.room = rooms.find(room => room.id === this.id) || null;
    } catch {
      this.room = null;
    }
  },
  methods: {
    open() {
      if (this.clickCallback) {
        this.clickCallback('chatRoom', this.id);
      }
      // The chat button lives in the top navigation on every page; ask it to open the room.
      document.dispatchEvent(new CustomEvent(this.$chatConstants.ACTION_OPEN_CHAT_ROOM, {
        detail: {room: this.room}
      }));
    },
    removed() {
      this.isFavorite = false;
      this.$root.$emit('alert-message', this.$t('matrix.favorite.removed'), 'success');
      this.$emit('removed');
      this.$root.$emit('refresh-favorite-list');
    },
    removeError() {
      this.$root.$emit('alert-message', this.$t('matrix.favorite.removeError'), 'error');
    },
  },
};
</script>
