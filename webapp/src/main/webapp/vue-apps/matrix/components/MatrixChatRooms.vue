<template>
  <div
    v-if="rooms?.length"
    class="d-flex flex-column">
    <matrix-chat-room
      v-for="room in rooms"
      :key="room.id"
      :room="room" />
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
      }
    },
    created() {
      document.addEventListener('matrix-joined-room', this.addJoinedRoom);
    },
    beforeDestroy() {
      document.removeEventListener('matrix-joined-room', this.addJoinedRoom);
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
    }
  }
</script>
