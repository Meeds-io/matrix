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
  <v-menu
    ref="menu"
    v-model="menu"
    :attach="`#room${room.spaceId || room.dmMemberId}`"
    :nudge-top="-1"
    content-class="no-min-width border-radius overflow-hidden"
    close-on-content-click
    offset-y
    left
    bottom>
    <template #activator="{ on, attrs }">
      <v-btn
        v-bind="attrs"
        class="pa-0"
        width="24"
        min-width="24"
        height="24"
        icon
        v-on="on"
        @click.stop.prevent>
        <v-icon
          size="16"
          class="icon-default-color">
          fa-ellipsis-v
        </v-icon>
      </v-btn>
    </template>
    <matrix-room-action-list-items
      :room="room"
      @close="menu = false"/>
  </v-menu>
</template>

<script>

export default {
  data() {
    return {
      menu: false,
    }
  },
  props: {
    room: {
      type: Object,
      default: null
    }
  },
  watch: {
    menu() {
      if (this.menu) {
        this.$emit('open')
      } else {
        this.$emit('close')
      }
    }
  }
};
</script>
