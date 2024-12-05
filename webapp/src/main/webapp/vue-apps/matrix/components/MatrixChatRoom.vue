<template>
  <div class="chat-room-item d-flex">
    <div
        :style="`backgroundImage: url(${room.avatarUrl})`"
        :class="statusStyle"
        class="chat-contact-avatar">
      <i v-if="type=='user' && isEnabled" class="uiIconStatus"></i>
    </div>
    <div class="contact-name overflow-hidden ps-3 flex-grow-1 my-2"
         @click="openRoom">
      {{ room.name }}
        <div v-if="room.lastMessage" class="text-capitalize-first-letter text-subtitle text-truncate">
          {{ room.lastMessage }}
        </div>
    </div>
    <div class="last-message-timestamp flex-row align-end my-2">
      {{ getLastMessageTime(room) }}
    </div>
    <div v-if="room.unreadMessages" class="unread-messages my-2">
      {{ room.unreadMessages }}
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
      },
      getLastMessageTime(room) {
        const timestamp = room.updated;
        if (timestamp) {
          if (this.$timeUtils.isSameDay(timestamp, new Date().getTime())) {
            return this.$timeUtils.getTimeString(timestamp);
          } else if (timestamp === -1){
            return '';
          } else {
            return this.$timeUtils.getDayDateString(timestamp);
          }
        }
        return '';
      },
    }
  }
</script>
