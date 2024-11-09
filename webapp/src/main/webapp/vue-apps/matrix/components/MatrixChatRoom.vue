<template>
  <div class="chat-room-item d-flex">
    <div
        :style="`backgroundImage: url(${room.avatarUrl})`"
        :class="statusStyle"
        class="chat-contact-avatar">
      <i v-if="type=='user' && isEnabled" class="uiIconStatus"></i>
    </div>
    <div class="overflow-hidden ps-3"
         @click="openRoom">
      {{ room.name }}
    </div>
    <div class="last-message-timestamp flex-row align-end">
      10:10
    </div>
  </div>
</template>
<script>
  export default {
    props: {
      room: {
        type: Object,
        default: null,
      }
    },
    data: () => ({
    }),
    created() {
    },
    computed : {
      roomURL() {
        return 'https://matrix.to/#/' + this.room.id + '?via=' + this.$root.$data.serverName;
      }
    },
    methods: {
      openRoom() {
        window.open(this.roomURL);
      }
    }
  }
</script>
