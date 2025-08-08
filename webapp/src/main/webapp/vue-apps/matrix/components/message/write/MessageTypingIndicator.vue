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
  <div class="mx-8 d-flex">
    <message-receipt
      v-for="(user, index) in typingUsers"
      :key="user"
      :receipt="user"
      :chat-room="room"
      :size="24"
      avatar
      :extra-class="index > 0 ? 'ms-n4' : ''"
      class="align-center" />
    <div class="d-flex align-center px-1 py-2 border-radius-16 background-grey-primary width-fit-content">
      <v-sheet
        v-for="i in 3"
        :key="i"
        width="5"
        height="5"
        class="align-center writing-dot border-radius-circle mx-1 grey-lighten1-background" />
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      isAtBottom: false,
      containerId: 'chatMessagesContainer'
    };
  },
  props: {
    room: {
      type: Object,
      default: null
    },
    typingUsers: {
      type: Array,
      default: null
    }
  },
  watch: {
    typingUsers() {
      this.checkIfAtBottom();
    }
  },
  mounted() {
    this.checkIfAtBottom();
  },
  methods: {
    checkIfAtBottom() {
      const container = document.getElementById(this.containerId);
      if (!container) {
        return;
      }
      const threshold = 40;
      this.isAtBottom =
          container.scrollHeight - container.scrollTop - container.clientHeight <= threshold;
      if (this.isAtBottom) {
        this.$emit('scroll');
      }
    }
  }

};
</script>
