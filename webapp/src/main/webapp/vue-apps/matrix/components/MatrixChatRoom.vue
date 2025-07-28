<template>
  <v-hover v-slot="{ hover }">
    <div
      :class="{'background-grey-primary': hover}"
      class="d-flex chat-room-item py-3 px-5 clickable"
      @click="openRoom">
      <v-badge
        :color="presenceColor"
        :value="isPrivateRoom"
        class="ma-0 pa-0"
        content=""
        offset-x="13"
        offset-y="10"
        width="12"
        height="12"
        bordered
        bottom
        overlap
        dot>
        <v-avatar
          :tile="!isPrivateRoom"
          :class="{'rounded-lg': !isPrivateRoom}"
          width="52"
          min-width="52"
          height="52">
          <v-img
            :src="avatarUrl"
            :lazy-src="room.avatarUrl"
            :alt="room?.name" />
        </v-avatar>
      </v-badge>
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
  data() {
    return {
      externalTag: `( ${this.$t('matrix.chat.user.external')} )`
    }
  },
  props: {
    room: {
      type: Object,
      default: null,
    }
  },
  created() {
    if (this.room?.directChat) {
      this.getUserStatus();
    }
  },
  computed: {
    isPrivateRoom() {
      return this.room?.directChat;
    },
    avatarUrl() {
      return this.room?.avatarUrl;
    },
    presence() {
      return this.room?.presence
    },
    presenceColor() {
      return this.presence && this.$root.statusMap[this.presence];
    }
  },
  methods: {
    getUserStatus() {
      return this.$userStateService.getUserStatus(this.room.dmMemberId).then(data => {
        this.room.presence = data?.status;
      });
    },
    openRoom() {
      document.dispatchEvent(new CustomEvent(this.$chatConstants.ACTION_OPEN_CHAT_ROOM, {detail: this.room}));
    },
    getUpdateTime(room) {
      return this.$matrixService.formatDate(room.updated);
    }
  }
}
</script>
