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
  <div
    v-if="rooms?.length"
    class="d-flex flex-column">
    <div id="initialRoomsElement">
      <matrix-chat-room
        v-for="room in initialRooms"
        :key="room.id"
        :selectedRoom="selectedRoom"
        :room="room" />
    </div>
    <div id="remainingRoomsElement"
      v-intersect="onIntersect">
      <matrix-chat-room
        v-if="rooms?.length > limit && displayRemainingRooms"
        v-for="room in remainingRooms"
        :key="room.id"
        :selectedRoom="selectedRoom"
        :room="room" />
    </div>
  </div>
  <div v-else-if="!loading" class="d-flex full-height align-center justify-center full-width">
    <div>
      <v-icon
        class="mx-auto mb-5"
        size="60">
        far fa-comments
      </v-icon>
      <p class="text-subtitle">{{ $t('matrix.chat.no.rooms') }}</p>
    </div>
  </div>
</template>
<script>
  export default {
    props: {
      rooms: {
        type: Array,
        default: () => []
      },
      loading: {
        type: Boolean,
        default: false
      },
      selectedRoom: {
        type: Object,
        default: null
      }
    },
    data() {
      return {
        limit: 20,
        displayRemainingRooms: false,
      }
    },
    created() {
      document.addEventListener('matrix-joined-room', this.addJoinedRoom);
    },
    beforeDestroy() {
      document.removeEventListener('matrix-joined-room', this.addJoinedRoom);
    },
    computed: {
      initialRooms() {
        return this.rooms.slice(0, this.limit);
      },
      remainingRooms() {
        return this.rooms.slice(this.limit, this.rooms.length);
      }
    },
    methods: {
      addJoinedRoom(event) {
        const roomExistsIndex = this.rooms.findIndex(room => room.id === event.detail.id);
        if(roomExistsIndex < 0) {
          this.rooms.unshift(event.detail);
        } else if (this.rooms[roomExistsIndex]) {
          this.rooms[roomExistsIndex].name = event.detail.name || this.rooms[roomExistsIndex].name;
          this.rooms[roomExistsIndex].avatarUrl = event.detail.avatarUrl || this.rooms[roomExistsIndex].avatarUrl;
          this.rooms[roomExistsIndex].members.unshift(event.detail.members);
        }
      },
      onIntersect(entries) {
        entries.forEach((entry) => {
          if (entry.isIntersecting) {
            this.displayRemainingRooms = true;
          }
        });
      }
    }
  }
</script>
