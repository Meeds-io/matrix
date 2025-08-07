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
  <exo-drawer
    id="messageReadReceiptListDrawer"
    ref="messageReadReceiptListDrawer"
    v-model="drawer"
    :right="!$vuetify.rtl"
    allow-expand>
    <template slot="title">
      <div class="d-flex my-auto text-header font-weight-bold text-color">
        {{ $t('matrix.message.view.label') }}
      </div>
    </template>
    <template slot="content">
      <div class="pa-5">
        <message-read-receipt
          v-for="receipt in readReceipts"
          :key="receipt"
          :receipt="receipt"
          :chat-room="room"
          :size="42"
          :avatar="false"
          extra-class="pb-2"
          class="position-relative" />
      </div>
    </template>
  </exo-drawer>
</template>

<script>

export default {
  data() {
    return {
      drawer: false,
      readReceipts: [],
      room: null
    }
  },
  created() {
    this.$root.$on('open-message-read-receipts-drawer', this.open);
  },
  methods: {
    open(room, receipts) {
      this.readReceipts = receipts;
      this.room = room;
      this.$refs.messageReadReceiptListDrawer.open();
    },
    close() {
      this.$refs.messageReadReceiptListDrawer.close();
    }
  }
};
</script>
