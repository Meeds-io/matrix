<template>
  <v-hover v-slot="{ hover }">
    <div
      :class="{'background-grey-primary': hover}"
      class="d-flex chat-room-item py-3 px-5 clickable">
      <div
        :style="`backgroundImage: url(${room.avatarUrl})`"
        :class="avatarBorderClass"
        class="meeds-chat-contact-avatar no-border size-13 d-flex">
        <div v-if="room.directChat" class="matrix-user-status size-3"
          :class="presenceClass"></div>
      </div>
      <div class="clickable overflow-hidden ps-2 flex-grow-1"
         @click="openRoom">
        <div :id="`room-name-${room.id}`"
          class="chat-room-name text-truncate text-title text-subtitle-1"
          :style="roomNameStyle">
          {{ room.name }} <span v-if="room.external">{{ externalTag }}</span>
        </div>
        <div
          v-if="lastMessageContent"
          class="chat-room-last-message text-truncate mt-1"
          :class="lastMessageStyle"
          v-sanitized-html="lastMessageContent">
        </div>
        <div v-else class="text-subtitle text-truncate mt-1">
          {{ $t('matrix.chat.start.conversation') }}
        </div>
      </div>
      <div class="ps-3">
        <div class="last-message-timestamp text-subtitle">
          {{ getUpdateTime(room) }}
        </div>
        <div class="pull-right text-font-small-size d-flex">
          <v-icon v-if="room.isMuted" size="16">fas fa-bell-slash</v-icon>
          <div v-if="room.unreadMessages" class="unread-messages align-center border-radius-circle error-color-background white--text text-font-small-size align-content-center">
            {{ room.unreadMessages <= 99 ? room.unreadMessages : '99+' }}
          </div>
        </div>
      </div>
    </div>
  </v-hover>
</template>
<script>

  export default {
    data: () => ({
      hasUpdatedLastMessageContent: false
    }),
    props: {
      room: {
        type: Object,
        default: null,
      }
    },
    async mounted() {
      await this.updateLastMessageContent();
    },
    computed : {
      avatarBorderClass() {
        return this.room.directChat ? 'rounded-circle' : 'rounded-lg';
      },
      lastMessageStyle() {
        return this.room.unreadMessages > 0 ? 'text-subtitle font-weight-bold':'text-subtitle';
      },
      presenceClass() {
        return `matrix-status-${this.room.presence}`;
      },
      externalTag() {
        return `( ${this.$t('matrix.chat.user.external')} )`;
      },
      lastMessageSender() {
        return this.room?.lastMessage?.sender;
      },
      lastMessageSenderId() {
        return this.lastMessageSender?.slice(1, this.lastMessageSender?.indexOf(':'));
      },
      isLastMessageSenderCurrentUser() {
        return this.lastMessageSenderId === eXo.env.portal.userName;
      },
      lastMessageContent() {
        return this.room?.lastMessage?.content;
      }
    },
    methods: {
      async updateLastMessageContent() {
        if (this.hasUpdatedLastMessageContent) {
          return;
        }
        const senderLabel = this.isLastMessageSenderCurrentUser
            ? this.$t('matrix.words.you')
            : (await this.$matrixService.getUserByMatrixId(this.lastMessageSender, this.room))?.profile?.fullname || this.lastMessageSender;

        const content = this.room.lastMessage.content || '';
        this.room.lastMessage.content = this.$t('matrix.chat.lastMessage.pattern', {
          0: senderLabel,
          1: content,
        });
        this.hasUpdatedLastMessageContent = true;
      },
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
      }
    }
  }
</script>
