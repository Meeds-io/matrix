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
  <div>
    <message-receipt
      v-for="receipt in filteredReadReceipts.slice(0, receiptsToDisplay)"
      :key="receipt"
      :receipt="receipt"
      :class=" filteredReadReceipts?.length > 1 ? 'ms-n4' : ''"
      :allow-animation="true"
      extra-class="transition-2s"
      class="position-relative" />
    <v-avatar
      v-if="showMore"
      size="24"
      :class="{'mt-n1 z-index-two': hover}"
      class="white--text ms-n4 border-white grey-lighten1-background"
      @mouseenter="hover = true"
      @mouseleave="hover = false"
      @click="openListReceiptsDrawer">
      <span class="text-font-small-size clickable">
        +{{ filteredReadReceipts.length - receiptsToDisplay }}
      </span>
    </v-avatar>
  </div>
</template>

<script>

export default {
  data() {
    return {
      receiptsToDisplay: 3,
      hover: false
    }
  },
  props: {
    room: {
      type: Object,
      default: null
    },
    readReceipts: {
      type: Array,
      default: null
    }
  },
  computed: {
    filteredReadReceipts() {
      return this.readReceipts.filter(user => user !== matrixUserId);
    },
    showMore() {
      return this.filteredReadReceipts.length > this.receiptsToDisplay
    }
  },
  methods: {
    openListReceiptsDrawer() {
      this.$root.$emit('open-message-read-receipts-drawer', this.room, this.filteredReadReceipts)
    }
  }
};
</script>

