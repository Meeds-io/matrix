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
    props: {
      rooms: {
        type: Array,
        default: function() { return [];}
      }
    },
  }
</script>
