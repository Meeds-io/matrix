<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2025 Meeds Association contact@meeds.io
 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 3 of the License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.
 You should have received a copy of the GNU Lesser General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
-->

<template>
  <div
    v-if="showLastMessageContent"
    class="chat-room-last-message text-truncate mt-1 text-subtitle"
    :class="{'font-weight-bold' : hasUnreadMessages && !isMuted}"
    v-sanitized-html="lastMessageContent">
  </div>
  <v-skeleton-loader
    v-else-if="showLoader"
    class="mt-1"
    type="text"
    height="18"
    width="250" />
  <div
    v-else-if="showFallback"
    class="text-subtitle text-truncate mt-1">
    {{ $t('matrix.chat.start.conversation') }}
  </div>
</template>

<script>

export default {
  data() {
    return {
      isUpdatingLastMessage: false,
      allowFallback: false
    };
  },
  props: {
    room: {
      type: Object,
      default: null
    },
  },
  created() {
    this.getAndBuildRoomLastMessage();
    document.addEventListener('matrix-message-reaction-removed', this.reactionRemoved);
  },
  beforeDestroy() {
    document.removeEventListener('matrix-message-reaction-removed', this.reactionRemoved);
  },
  mounted() {
    this.$nextTick().then(() => {
      this.isUpdatingLastMessage = true;
      return this.getAndBuildRoomLastMessage();
    }).then(() => this.isUpdatingLastMessage = false);
    setTimeout(() => {
      this.allowFallback = true;
    }, 200);
  },
  computed: {
    showLastMessageContent() {
      return !this.isUpdatingLastMessage && !!this.lastMessageContent;
    },
    showLoader() {
      return this.isUpdatingLastMessage;
    },
    lastMessageContent() {
      return this.room?.lastMessageContent;
    },
    showFallback() {
      return this.allowFallback && !this.isUpdatingLastMessage && !this.lastMessageContent;
    },
    lastMessageSender() {
      return this.room?.lastMessage?.sender;
    },
    isLastMessageSenderCurrentUser() {
      return this.lastMessageSender === matrixUserId;
    },
    hasUnreadMessages() {
      return this.room.unreadMessages > 0;
    },
    isMuted() {
      return this.room?.muted;
    }
  },
  methods: {
    reactionRemoved(event) {
      const {roomId} = event.detail;
      if (roomId !== this.room.id) {
        return;
      }
      this.getAndBuildRoomLastMessage();
    },
    async getAndBuildRoomLastMessage() {
      try {
        const message = await this.$matrixService.getRoomLastMessage(this.room.id);
        if (!message) {
          return;
        }
        if (!this.room.updated || this.room.updated <= message.origin_server_ts) {
          this.$set(this.room, 'updated', message.origin_server_ts);
        }
        const lastMessage = await this.$matrixService.buildRoomLastMessage(message, message.type, this.room);
        if (lastMessage) {
          this.$set(this.room, 'lastMessage', lastMessage);
          await this.$nextTick();
          await this.updateLastMessageContent();
        }
      } catch (error) {
        console.error(error);
      }
    },
    async updateLastMessageContent() {
      const content = this.room?.lastMessage?.content;
      if (!content) {
        this.$set(this.room, 'lastMessageContent', null);
        return;
      }

      let formattedContent;

      if (this.room?.lastMessage?.reaction) {
        formattedContent = await this.formatReactionLastMessageContent(this.room.lastMessage);
      } else {
        const senderLabel = await this.resolveLastMessageSenderLabel();
        formattedContent = this.$t('matrix.chat.lastMessage.pattern', {
          0: senderLabel,
          1: content
        });
      }

      if (this.room?.lastMessageContent !== formattedContent) {
        this.$set(this.room, 'lastMessageContent', formattedContent);
      }
    },
    async formatReactionLastMessageContent(lastMessage) {
      const {sender, reactionKey, content} = lastMessage;
      const isSelf = sender === matrixUserId;

      let reactedBy = sender;
      if (!isSelf) {
        const user = await this.$matrixService.getUserByMatrixId(sender, this.room);
        reactedBy = user?.profile?.fullname || sender;
      }

      return isSelf
        ? this.$root.$t('matrix.message.you.reacted.with', {0: reactionKey, 1: content})
        : this.$root.$t('matrix.message.user.reacted.with', {0: reactedBy, 1: reactionKey, 2: content});
    },
    async resolveLastMessageSenderLabel() {
      if (this.isLastMessageSenderCurrentUser) {
        return this.$t('matrix.words.you');
      }

      const user = await this.$matrixService.getUserByMatrixId(this.lastMessageSender, this.room);
      return user?.profile?.fullname || this.lastMessageSender;
    }
  }
};
</script>
