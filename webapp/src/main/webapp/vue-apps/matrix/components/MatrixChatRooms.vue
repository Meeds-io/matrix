<template>
  <div
    v-if="rooms?.length"
    class="d-flex flex-column">
    <matrix-chat-room
      v-for="(room, i) in rooms"
      :key="i"
      :id="'room-'+i"
      :room="room" />
  </div>
  <div v-else-if="!loading" class="d-flex full-height align-center justify-center full-width">
    <div class="noRoomsContent">
      <v-icon class="mx-auto disabled--text mb-3" size="100">fas fa-comments</v-icon>
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
      document.addEventListener('matrix-joined-room',e => this.addJoinedRoom(e));
    },
    beforeDestroy() {
      document.removeEventListener('matrix-joined-room',e => this.addJoinedRoom(e));
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
