<template>
  <div class="chat-room-item d-flex px-5">
    <div
      :style="`backgroundImage: url(${room.avatarUrl})`"
      :class="avatarBorderClass"
      class="meeds-chat-contact-avatar size-13 d-flex">
      <div v-if="room.directChat" class="matrix-user-status size-3" :class="presenceClass"></div>
    </div>
    <div class="clickable overflow-hidden ps-2 flex-grow-1 my-2"
       @click="openRoom">
      <div :id="`room-name-${room.id}`" class="chat-room-name text-truncate text-title text-subtitle-1" :style="roomNameStyle">
        {{ room.name }}
      </div>
      <div v-if="room.lastMessage" class="chat-room-last-message text-capitalize-first-letter text-truncate mt-2" :class="lastMessageStyle">
        {{ room.lastMessage.content }}
      </div>
      <div v-else class="text-subtitle text-truncate mt-2">
        {{ $t('matrix.chat.start.conversation') }}
      </div>
    </div>
    <div class="ps-3 my-3">
      <div class="last-message-timestamp text-subtitle">
        {{ getLastMessageTime(room) }}
      </div>
      <div class="pull-right text-font-small-size d-flex">
        <v-icon v-if="room.isMuted" size="16">fas fa-bell-slash</v-icon>
        <div v-if="room.unreadMessages" class="unread-messages text-font-small-size content-align">
          {{ room.unreadMessages <= 99 ? room.unreadMessages : '99+' }}
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
        return this.room.directChat ? 'rounded-circle' : 'rounded-lg';
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
        document.dispatchEvent(new CustomEvent(this.$chatConstants.ACTION_CHAT_OPEN_DISCUSSION_DRAWER, { detail: this.room }));
      },
      getLastMessageTime(room) {
        return this.$matrixService.formatDate(room.updated);
      },
      getUserPresence() {
        this.$matrixService.getUserPresence(this.room.dmMemberId).then(status => {
          this.presenceClass = `matrix-status-${status.presence}`;
        });
      }
    }
  }
</script>
