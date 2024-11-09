<template>
  <div class="room-list full-width">
    <div
        v-if="rooms"
        v-for="(room, i) in rooms"
        :key="i">
      <matrix-chat-room
          :id="'room-'+i"
          :room="room" />
    </div>
    <div v-else>
      {{ $t('chat.no.discussions') }}
    </div>

  </div>
</template>
<script>
  export default {
    data: () => ({
      rooms: Array
    }),
    watch : {
    },
    created() {
      this.loadRooms();
    },
    methods: {
      loadRooms () {
        this.$matrixService.loadChatRooms(localStorage.getItem('matrix_user_id')).then(rooms => this.rooms = rooms);
      }
    }
  }
</script>
