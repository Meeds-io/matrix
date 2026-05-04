<!--

  This file is part of the Meeds project (https://meeds.io/).

  Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io

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
  <div>
    <div v-if="selecting" class="pa-5">
      <div class="mb-5 font-weight-bold text-color">
        {{ $t('matrix.chat.quick.create.select.parent.space') }}
      </div>
      <v-list-item
        v-for="space in parentSpaces"
        :key="space.id"
        class="px-0"
        dense
        @click="selectSpace(space)">
        <v-list-item-content>
          <space-avatar
            :space="space"
            class="not-clickable-link text-truncate"
            list-style />
        </v-list-item-content>
      </v-list-item>
      <div
        v-if="hasMore"
        id="parentSpacesListFooter"
        class="flex-grow-0 flex-shrink-0 pb-5 border-box-sizing">
        <v-btn
          :loading="loadingMore"
          class="loadMoreButton border-color elevation-0 ma-auto"
          block
          @click="loadMore">
          {{ $t('spacesList.button.showMore') }}
        </v-btn>
      </div>
    </div>
    <div v-else-if="canEdit">
      <div
        class="d-flex align-center justify-space-between px-4">
        <span class="text-body font-weight-bold">
          {{ $t('matrix.chat.quick.create.select.parent.space') }}
        </span>
        <v-btn
          icon
          @click="startSelection">
          <v-icon size="20">
            fa-edit
          </v-icon>
        </v-btn>
      </div>
      <div v-if="value" class="px-4">
        <space-avatar
          :space="value"
          class="not-clickable-link text-truncate"
          list-style />
      </div>
    </div>
  </div>
</template>
<script>

export default {
  props: {
    value: {
      type: Object,
      default: null
    },
    parentSpaces: {
      type: Array,
      default: () => []
    },
    canEdit: {
      type: Boolean,
      default: true
    },
    hasMore: {
      type: Boolean,
      default: false
    },
    loadingMore: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      selecting: false
    };
  },
  watch: {
    selecting() {
      this.$emit('selecting', this.selecting);
    }
  },
  methods: {
    selectSpace(space) {
      this.$emit('input', space);
      this.selecting = false;
    },
    startSelection() {
      this.selecting = true;
    },
    loadMore() {
      this.$emit('load-more');
    },
  }
};
</script>
