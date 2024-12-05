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
    <div v-else class="d-flex full-height disabled-background align-center justify-center full-width">
      <div class="noRoomsContent">
        <v-icon class="mx-auto disabled--text mb-3" size="100">fas fa-comments</v-icon>
        <p class="text-subtitle">{{ $t('matrix.chat.no.rooms') }}</p>
      </div>
    </div>


  </div>
</template>
<script>
  export default {
    data: () => ({
      rooms: []
    }),
    watch : {
      rooms() {
        console.log(this.rooms);
      }
    },
    created() {
      this.loadRooms();
      document.addEventListener(this.$matrixService.MATRIX_ACTION_MESSAGE_RECEIVED, event => this.messageReceived(event));
    },
    methods: {
      loadRooms() {
        this.$matrixService.loadChatRooms(localStorage.getItem('matrix_user_id')).then(matrixRoomsObject => {
          this.rooms = matrixRoomsObject.rooms || [];
          this.$root.$emit('chat-event-total-unread-updated', matrixRoomsObject.totalUnreadMessages)
        });
      },
      messageReceived(event) {
        console.log('Message received');
        console.log(event.roomId);
        console.log(event.message);
        console.log('End message received');
      }
    }
  }
</script>
