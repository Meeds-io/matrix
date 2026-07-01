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
  <v-chip-group
    v-model="selected"
    multiple
    column
    class="px-4 py-1"
    @change="emitChange">
    <v-chip
      v-for="filter in filters"
      :key="filter.key"
      :value="filter.key"
      filter
      outlined
      small>
      {{ $t(filter.label) }}
    </v-chip>
  </v-chip-group>
</template>

<script>
export default {
  data() {
    return {
      selected: [],
      filters: [
        { key: 'favorite', label: 'matrix.rooms.filter.favorite' },
        { key: 'unread', label: 'matrix.rooms.filter.unread' },
        { key: 'dm', label: 'matrix.rooms.filter.conversations' },
        { key: 'space', label: 'matrix.rooms.filter.spaces' },
      ],
    };
  },
  methods: {
    emitChange() {
      this.$root.$emit('chat-rooms-filter-changed', this.selected);
    }
  }
};
</script>
