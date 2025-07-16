<template>
  <v-hover v-slot="{ hover }">
    <div
      :class="{'background-grey-primary': hover}"
      class="d-flex chat-room-item py-3 px-5 clickable"
      @click="openRoom">
      <div
        :style="`backgroundImage: url(${room.avatarUrl})`"
        :class="avatarBorderClass"
        class="meeds-chat-contact-avatar no-border size-13 d-flex">
        <div
          v-if="room.directChat"
          :class="presenceClass"
          class="matrix-user-status size-3" />
      </div>
      <div class="overflow-hidden ps-2 flex-grow-1">
        <div
          :id="`room-name-${room.id}`"
          class="chat-room-name text-truncate text-title text-subtitle-1">
          {{ room.name }}
          <span v-if="room.external">
            {{ externalTag }}
          </span>
        </div>
        <room-last-message :room="room" />
      </div>
      <div class="ps-3">
        <div class="last-message-timestamp text-subtitle">
          {{ getUpdateTime(room) }}
        </div>
        <div class="pull-right text-font-small-size d-flex">
          <v-icon
            v-if="room.isMuted"
            size="16">
            fas fa-bell-slash
          </v-icon>
          <div
            v-if="room.unreadMessages"
            class="unread-messages align-center border-radius-circle error-color-background
            white--text text-font-small-size align-content-center">
            {{ room.unreadMessages <= 99 ? room.unreadMessages : '99+' }}
          </div>
        </div>
      </div>
    </div>
  </v-hover>
</template>
<script>

  export default {
    props: {
      room: {
        type: Object,
        default: null,
      }
    },
    computed : {
      avatarBorderClass() {
        return this.room.directChat ? 'rounded-circle' : 'rounded-lg';
      },
      presenceClass() {
        return `matrix-status-${this.room.presence}`;
      },
      externalTag() {
        return `( ${this.$t('matrix.chat.user.external')} )`;
      }
    },
    methods: {
      openRoom() {
        document.dispatchEvent(new CustomEvent(this.$chatConstants.ACTION_OPEN_CHAT_ROOM, { detail: this.room }));
      },
      getUpdateTime(room) {
        return this.$matrixService.formatDate(room.updated);
      },
      getUserPresence() {
        return this.$matrixService.getUserPresence(this.room.dmMemberId).then(status => {
          this.presenceClass = `matrix-status-${status.presence}`;
        });
      },
    }
  }
</script>
