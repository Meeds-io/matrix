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
  <v-text-field
    ref="filter"
    v-if="showFilter"
    v-model="localFilterText"
    :placeholder="$t('matrix.rooms.filter.placeholder')"
    class="my-0 ms-0 me-5 pa-0 filter"
    hide-details
    @focus="filterFocused = true"
    @blur="filterFocused = false">
    <template #prepend-inner>
      <v-icon
        :class="{'primary--text': !!filterText || filterFocused }"
        class="mt-1"
        size="16">
        fa-filter
      </v-icon>
    </template>
    <template #prepend>
      <v-btn
        icon
        class="pa-0 mb-n1 mx-0 mt-0"
        @click="toggleFilter">
        <v-icon
          class="icon-default-color"
          size="20">
          fa-arrow-left
        </v-icon>
      </v-btn>
    </template>
    <template
      v-if="!!filterText"
      #append>
      <v-btn
        class="pa-0 mt-1 mx-0 mb-0"
        width="24"
        height="24"
        icon
        @click="resetFilterText">
        <v-icon
          class="primary--text"
          size="16">
          fa-times
        </v-icon>
      </v-btn>
    </template>
  </v-text-field>
</template>

<script>
export default {
  data() {
    return {
      filterFocused: false,
    };
  },
  props: {
    showFilter: {
      type: Boolean,
      default: false,
    },
    filterText: {
      type: String,
      default: '',
    }
  },
  computed: {
    localFilterText: {
      get() {
        return this.filterText;
      },
      set(value) {
        this.emitFilterText(value);
      }
    }
  },
  methods: {
    emitShowFilter(value) {
      this.$emit('update:showFilter', value);
    },
    emitFilterText(text) {
      this.$emit('update:filterText', text);
    },
    resetFilterText() {
      this.emitFilterText('');
    },
    toggleFilter() {
      this.emitShowFilter(!this.showFilter);
      this.$nextTick(() => {
        this.$refs?.filter?.focus?.();
      });
    },
    openFilter() {
      this.emitShowFilter(true);
      this.$nextTick(() => {
        this.$refs?.filter?.focus?.();
      });
    }
  }
};
</script>