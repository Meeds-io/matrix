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
    id="chatMessagesContainer"
    ref="chatMessagesContainer"
    class="specific-scrollbar position-relative overflow-x-hidden"
    v-touch="{down: () => loadMoreMessages()}"
    @scroll="onMessagesContainerScroll">
    <div
      v-if="loadingNewMessages"
      class="application-background-color application-border application-border-radius flex d-flex flex-column">
      <v-progress-circular
        color="primary"
        size="20"
        indeterminate
        class="mx-auto my-5" />
    </div>
    <div
      id="roomChatMessages"
      v-show="messages && !loading"
      class="d-flex flex-column fill-height"
      @wheel="loadMoreMessages"
      @scroll="loadMoreMessages">
      <matrix-chat-message
        v-for="(message, i) in messages"
        :id="`chat-message-${i}`"
        :ref="`chat-message-${i}`"
        :key="message.event_id"
        :message="message"
        :previous-message="messages?.[i - 1]"
        :next-message="messages?.[i + 1]"
        :room="room"
        :unseen-messages-data="unSeenMessagesData"
        :is-input-focused="isInputFocused"
        :unseen-messages-count="unseenMessagesCount"
        class="transition-2s"
        @reply="replyToMessage"
        @reaction="reactToMessage"
        @reset-unseen="resetUnseenData" />
      <matrix-message-typing-indicator
        v-if="isTyping"
        :room="room"
        :typing-users="typingUsers"
        class="ms-4 mt-2"
        @scroll="scrollToEnd" />
    </div>
    <floating-arrow-button
      v-if="!loading && hasUnseenMessages"
      :show-badge="hasUnseenMessages"
      :closeable-tooltip="$t('matrix.messages.mark.as.read')"
      :button-tooltip="$t('matrix.messages.check.new')"
      scroll-target="unseenSeparator"
      class="mt-16 pt-2 me-5"
      top-position
      closeable
      up-arrow
      @closed="clearUnseenData" />
    <floating-arrow-button
      v-if="!loading && !isAtBottomMessages"
      :show-badge="hasUnseenNewReceivedMessage"
      :button-tooltip="$t('matrix.messages.jump.to.last')"
      class="mb-16 pb-2 me-5"
      @click="scrollToBottomMessages" />
  </div>
</template>

<script>

export default {
  data() {
    return {
      messages: [],
      loading: false,
      space: null,
      lastScrollTop: 0,
      hasUnseenNewReceivedMessage: false,
      loadingNewMessages: false,
      hasMoreMessages: true,
      composerDefaultHeight: 40,
      messageContainerScrollTop: 0,
      messageContainerScrollHeight: 0,
      messageContainerClientHeight: 0,
      unSeenMessagesData: {
        firstUnseenEventId: null,
        inViewport: {
          visibleTop: false,
          above: false,
          below: false
        }
      },
      typingCache: {},
      messagesContainerElement: null,
      messagesContainerId: 'chatMessagesContainer',
      roomLastReadReceipts: null,
      currentLoadToken: 0,
      isUserScrolling: false,
      userScrollTimeout: null,
      messagesCache: new Map()
    };
  },
  props: {
    room: {
      type: Object,
      default: null
    },
    isInputFocused: {
      type: Boolean,
      default: false
    }
  },
  created() {
    document.addEventListener('space-settings-updated', this.handleSpaceSettingsUpdate);
    document.addEventListener('matrix-message-received', this.messageReceived);
    document.addEventListener('matrix-message-deleted', this.messageDeleted);
    document.addEventListener('matrix-room-typing-received', this.handleTypingReceived);
    document.addEventListener('unseen-data-updated', this.handleUpdateUnseenData);
    this.$root.channel.addEventListener('message', this.handleBroadcastMessage);
    this.$root.$on('room-discussion-opened', this.markRoomAsRead);
  },
  beforeDestroy() {
    document.removeEventListener('space-settings-updated', this.handleSpaceSettingsUpdate);
    document.removeEventListener('matrix-message-received', this.messageReceived);
    document.removeEventListener('matrix-message-deleted', this.messageDeleted);
    document.removeEventListener('matrix-room-typing-received', this.handleTypingReceived);
    document.removeEventListener('unseen-data-updated', this.handleUpdateUnseenData);
    this.$root.channel.removeEventListener('message', this.handleBroadcastMessage);
    this.$root.$off('room-discussion-opened', this.markRoomAsRead);
  },
  computed: {
    typingUsers() {
      return this.typingCache?.[this.room?.id]?.typingUsers || [];
    },
    isTyping() {
      return this.typingUsers.length > 0;
    },
    unseenViewPortInfo() {
      return this.unSeenMessagesData?.viewPortInfo;
    },
    hasUnseenMessages() {
      const info = this.unseenViewPortInfo;
      if (!info) {
        return false;
      }

      return info.visibleTop === false && (info.above === true || info.below === false);
    },
    isAtBottomMessages() {
      return (
        this.messageContainerScrollHeight - this.messageContainerScrollTop - this.messageContainerClientHeight <= 60
      );
    },
    unseenMessagesCount() {
      const firstUnseenMessageId = this.unSeenMessagesData?.firstUnseenEventId;
      const messages = this.messages || [];

      if (!firstUnseenMessageId || !messages.length) {
        return 0;
      }

      const index = messages.findIndex(msg => msg.event_id === firstUnseenMessageId);
      return index >= 0 ? messages.length - (index + 1) : 0;
    }
  },
  watch: {
    loading() {
      this.$nextTick(() => {
        this.$emit('loading', this.loading);
      });
    },
    room() {
      this.messages = [];
      this.loading = false;
      // cancel any ongoing loads
      this.currentLoadToken++;
      this.loadAndProcessMessages();
    }
  },
  methods: {
    async initDiscussion() {
      this.reset();
      this.resetData();

      this.roomLastReadReceipts = await this.$matrixService.loadLastReadReceipts(this.room?.id);
      this.room.lastReadReceipts = this.roomLastReadReceipts;
    },
    async loadAndProcessMessages() {
      if (!this.room?.id) {
        return;
      }

      const roomId = this.room.id;
      const loadToken = ++this.currentLoadToken;

      this.loading = true;
      await this.$nextTick();

      try {
        const resp = await this.$matrixService.loadRoomMessages(roomId);
        this.from = resp.start;
        this.to = resp.end;

        if (this.currentLoadToken !== loadToken || this.room?.id !== roomId) {
          this.loading = false;
          return;
        }

        const allMessages = resp.chunk;
        if (!allMessages?.length) {
          this.loading = false;
          return;
        }
        const reversedMessages = allMessages.slice();
        const processed = await new Promise(resolve => {
          (window.requestIdleCallback || window.requestAnimationFrame)(async () => {
            const result = await this.$matrixService.processMessages(roomId, reversedMessages);
            resolve(result);
          });
        });

        if (this.currentLoadToken !== loadToken || this.room?.id !== roomId) {
          return;
        }

        const result = processed.messages;
        const chunkSize = 10;
        const chunks = [];

        for (let i = 0; i < result.length; i += chunkSize) {
          chunks.push(result.slice(i, i + chunkSize).reverse());
        }

        this.messages = chunks.shift();

        const container = this.getMessagesContainerElement();

        for (const chunk of chunks) {
          const prevScrollHeight = container.scrollHeight;
          this.messages = [...chunk, ...this.messages];
          await new Promise(resolve => requestAnimationFrame(resolve));
          const newScrollHeight = container.scrollHeight;
          container.scrollTop += newScrollHeight - prevScrollHeight;
        }

      } catch (err) {
        console.error('Error loading messages:', err);
      } finally {
        this.loading = false;
      }
    },
    messageReceived(event) {
      if (!this.messages) {
        return;
      }
      if (this.room?.id !== event.detail.roomId) {
        return;
      }
      const receivedMessage = event.detail.message;
      const relatesTo = receivedMessage.content['m.relates_to'];
      const inReplyTo = relatesTo?.['m.in_reply_to']?.event_id;

      if (receivedMessage.edited) {
        const index = this.messages.findIndex(msg => msg.event_id === receivedMessage.event_id);
        if (index !== -1) {
          this.$set(this.messages, index, {
            ...this.messages[index],
            content: receivedMessage.content,
            updatedAt: receivedMessage.updatedAt,
            edited: true
          });
        }

        for (let i = 0; i < this.messages.length; i++) {
          const message = this.messages[i];
          if (message?.replyTo?.targetEventId === receivedMessage.event_id) {
            const replyTo = this.$matrixService.buildReplyToObject(this.messages, message.replyTo.targetEventId);
            this.$set(this.messages, i, { ...message, replyTo });
          }
        }
      } else {
        this.messages.push(receivedMessage);
        if (inReplyTo) {
          receivedMessage.replyTo = this.$matrixService.buildReplyToObject(this.messages, inReplyTo);
        }
        setTimeout(() => {
          if (this.isAtBottomMessages) {
            this.scrollToEnd();
            if (!document.hidden) {
              this.markRoomAsRead(this.room?.id);
            }
          } else {
            this.hasUnseenNewReceivedMessage = true;
          }
        }, 100);
      }
    },
    messageDeleted(event) {
      if (!this.messages || this.room?.id !== event.detail.roomId) {
        return;
      }

      const redactedEventId = event?.detail?.eventId;
      const redaction = event.detail?.redaction;
      const index = this.messages.findIndex(msg => msg.event_id === redactedEventId);
      if (index === -1) {
        return;
      }

      const original = this.messages[index];
      const redacted = {
        ...original,
        redacted_because: redaction || { redacts: redactedEventId, reason: 'Redacted' },
        content: {
          ...original.content,
          body: undefined,
          formatted_body: undefined,
          format: undefined,
          msgtype: undefined
        }
      };

      if (original.edited) {
        redacted.edited = false;
        redacted.updatedAt = undefined;
      }

      this.$set(this.messages, index, redacted);
      this.updateUnseenOnMessageDelete(redactedEventId, index);
    },
    markRoomAsRead(roomId) {
      if (this.messages?.length) {
        const lastMessageIndex = this.messages.length - 1;
        this.$matrixService.markRoomAsFullyRead(roomId, this.messages[lastMessageIndex]?.event_id).then(() => {
          document.dispatchEvent(new CustomEvent('matrix-room-mark-full-read', {
            detail: {roomId: roomId}
          }));
        });
      }
    },
    resetData() {
      if (!this.unSeenMessagesData?.viewPortInfo) {
        return;
      }
      this.unSeenMessagesData.firstUnseenEventId = null;
      this.unSeenMessagesData.viewPortInfo.visibleTop = true;
      this.unSeenMessagesData.viewPortInfo.above = false;
      this.unSeenMessagesData.viewPortInfo.below = false;
    },
    updateUnseenOnMessageDelete(redactedEventId, index) {
      const unseenData = this.unSeenMessagesData;
      if (!unseenData) {
        return;
      }

      if (redactedEventId === unseenData.firstUnseenEventId) {
        const nextMessage = this.messages[index + 1];

        let updatedUnseen = {};
        if (nextMessage) {
          updatedUnseen.firstUnseenEventId = nextMessage.event_id;
        } else {
          updatedUnseen = null;
        }

        this.unSeenMessagesData = updatedUnseen;
        this.$matrixService.saveUnseenMessages(this.room.id, matrixUserId, updatedUnseen);
      }
    },
    handleTypingReceived(event) {
      const {roomId, users} = event.detail;
      const now = Date.now();
      this.$set(this.typingCache, roomId, {typingUsers: users, lastUpdated: now});
    },
    handleSpaceSettingsUpdate(event) {
      this.space = event.detail;
      if (this.space.id !== this.room?.spaceId) {
        return;
      }
      this.room.name = this.space.displayName;
    },
    handleUpdateUnseenData(event) {
      const {roomId} = event.detail;
      if (this.room?.id !== roomId) {
        return;
      }
      setTimeout(async () => {
        if (this.isInputFocused) {
          this.clearUnseenData();
        } else {
          await this.loadUnseenMessagesData();
        }
      }, 200);
    },
    async loadUnseenMessagesData() {
      this.unSeenMessagesData = await this.$matrixService.getUnseenMessagesData(this.room?.id, matrixUserId);
      this.$forceUpdate();
    },
    scrollToBottomMessages() {
      if (!this.hasUnseenMessages) {
        this.clearUnseenData();
      }
      this.scrollToEnd();
      this.markRoomAsRead(this.room?.id);
    },
    clearUnseenData() {
      this.$matrixService.clearUnseenMessages(this.room?.id, matrixUserId).then(() => {
        this.resetData();
        this.$root.channel.postMessage({type: 'reset-unseen-data'});
      });
    },
    getMessagesContainerElement() {
      return document.getElementById(this.messagesContainerId);
    },
    scrollToEnd(loadToken = this.currentLoadToken, roomId = this.room?.id) {
      const container = this.$refs.chatMessagesContainer;
      if (!container || !this.messages?.length) {
        return;
      }
      if (this.currentLoadToken !== loadToken || this.room?.id !== roomId) {
        return;
      }
      requestAnimationFrame(() => {
        if (this.currentLoadToken !== loadToken || this.room?.id !== roomId) {
          return;
        }
        container.scrollTop = container.scrollHeight;
        this.hasUnseenNewReceivedMessage = false;
      });
    },
    replyToMessage(message) {
      this.$root.$emit('reply-to-message', this.room?.id, this.messages, message);
    },
    async reactToMessage(emoji, targetMessage) {
      const existingReaction = targetMessage?.reactions?.find?.(reaction => reaction.key === emoji
          && reaction.userIds.includes(matrixUserId));
      if (existingReaction) {
        await this.removeReaction(emoji, targetMessage);
      } else {
        await this.$matrixService.reactToMessage(emoji, this.room.id, targetMessage.event_id);
      }
    },
    async removeReaction(emoji, targetMessage) {
      const reactionEventId = await this.$matrixService.findReactionEventId(
        emoji,
        targetMessage.event_id,
        matrixUserId,
        this.room.id);
      if (reactionEventId) {
        await this.$matrixService.redactEvent(this.room.id, reactionEventId);
      }
    },
    onMessagesContainerScroll(event) {
      const container = event.target;
      this.messageContainerScrollTop = container.scrollTop;

      this.messageContainerScrollHeight = container.scrollHeight;
      this.messageContainerClientHeight = container.clientHeight;

      this.isUserScrolling = true;
      clearTimeout(this.userScrollTimeout);
      this.userScrollTimeout = setTimeout(() => {
        this.isUserScrolling = false;
      }, 300);

      if (this.isAtBottomMessages) {
        this.hasUnseenNewReceivedMessage = false;
        this.markRoomAsRead(this.room?.id);
      }
    },
    resetUnseenData() {
      this.$matrixService.resetUnseenOnFirstMessageSeen(this.room?.id, matrixUserId).then(reset => {
        if (reset) {
          this.resetData();
          this.$root.channel.postMessage({type: 'reset-unseen-data'});
        }
      });
    },
    loadMoreMessages() {
      const messagesDOMEl = document.getElementById('chatMessagesContainer');
      const scrollTop = messagesDOMEl.scrollTop;
      if (scrollTop < this.lastScrollTop) {
        const composerDOMEl = document.getElementById('messageComposerArea');
        composerDOMEl.style.height = `${this.composerDefaultHeight}px`;
      }
      this.lastScrollTop = scrollTop >= 0 && scrollTop || 0;
      if (this.loadingNewMessages || !this.hasMoreMessages || messagesDOMEl.scrollTop > 0) {
        return;
      }
      this.loadingNewMessages = true;
      const lastMessageId = this.messages[0].event_id;
      setTimeout(() => {
        this.$matrixService.loadRoomMessages(this.room.id, this.to).then(async resp => {
          // check if there is no more messages
          if (!resp.chunk || !resp.chunk.length || resp.chunk.length < this.$chatConstants.MESSAGES_LOAD_LIMIT) {
            this.hasMoreMessages = false;
          }
          const messagesToProcess = [...resp.chunk.reverse()];
          const processedMessages = await this.$matrixService.processMessages(this.room?.id, messagesToProcess);
          this.messages = [...processedMessages.messages, ...this.messages];
          this.from = resp.start;
          this.to = resp.end;
        }).finally(() => {
          document.getElementById(`message-content-${lastMessageId}`).scrollIntoView({
            behavior: 'instant'
          });
          this.loadingNewMessages = false;
        });
      }, 1000);
    },
    reset() {
      this.messages = [];
      this.lastScrollTop = 0;
    },
    handleBroadcastMessage(event) {
      const {type} = event.data;
      if (type === 'reset-unseen-data') {
        this.resetData();
      }
    }
  }
};
</script>
