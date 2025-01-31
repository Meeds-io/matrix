<template>
  <div class="chat-room-item d-flex">
    <div
        :style="`backgroundImage: url(${room.avatarUrl})`"
        :class="avatarBorderClass"
        class="chat-contact-avatar d-flex">
      <i v-if="room.isDirectChat" class="uiIconStatus matrix-user-status" :class="presenceClass"></i>
    </div>
    <div class="clickable overflow-hidden ps-3 flex-grow-1 my-2"
         @click="openRoom">
        <div :id="`room-name-${room.id}`" class="text-title text-subtitle-1">
          {{ room.name }}
        </div>
        <div v-if="room.lastMessage" class="text-capitalize-first-letter text-truncate" :class="lastMessageStyle">
          {{ room.lastMessage }}
        </div>
    </div>
    <div class="ps-3 my-2">
      <div class="last-message-timestamp text-subtitle">
        {{ getLastMessageTime(room) }}
      </div>
      <div class="pull-right text-font-small-size d-flex">
        <v-icon v-if="room.isMuted" size="18">fas fa-bell-slash</v-icon>
        <div v-if="room.unreadMessages" class="unread-messages text-font-small-size">
          {{ room.unreadMessages }}
        </div>
      </div>
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
    mounted() {
    },
    computed : {
      roomURL() {
        return 'https://matrix.to/#/' + this.room.id + '?via=' + this.$root.$data.serverName;
      },
      avatarBorderClass() {
        return this.room.isDirectChat ? 'rounded-circle' : 'rounded-lg';
      },
      lastMessageStyle() {
        return this.room.unreadMessages > 0 ? 'text-subtitle-2 text-bold':'text-subtitle';
      },
      presenceClass() {
        return `matrix-status-${this.room.presence}`;
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
      getUserPresence() {
        this.$matrixService.getUserPresence(this.room.dmMemberId).then(status => {
          this.presenceClass = `matrix-status-${status.presence}`;
        });
      }
    }
  }
</script>
