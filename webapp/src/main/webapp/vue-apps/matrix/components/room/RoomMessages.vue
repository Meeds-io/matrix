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
      class="d-flex flex-column"
      @wheel="loadMoreMessages"
      @scroll="loadMoreMessages">
      <meeds-chat-message
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
        @reply="replyToMessage"
        @reaction="reactToMessage"
        @reset-unseen="resetUnseenData" />
      <message-typing-indicator
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
      leftReactions: [],
      composerDefaultHeight: 40,
      messageContainerScrollTop: 0,
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
    }
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
    document.addEventListener('unseen-data-updated', this.handleUpdateUnseenData)
    this.$root.channel.addEventListener('message', this.handleBroadcastMessage)
  },
  beforeDestroy() {
    document.removeEventListener('space-settings-updated', this.handleSpaceSettingsUpdate);
    document.removeEventListener('matrix-message-received', this.messageReceived);
    document.removeEventListener('matrix-message-deleted', this.messageDeleted);
    document.removeEventListener('matrix-room-typing-received', this.handleTypingReceived);
    document.removeEventListener('unseen-data-updated', this.handleUpdateUnseenData)
    this.$root.channel.removeEventListener('message', this.handleBroadcastMessage)
  },
  async mounted() {
    await this.initDiscussion();
    await this.loadAndProcessMessages();
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
      const element = this.getMessagesContainerElement();
      if (!element) {
        return true;
      }
      return element.scrollHeight - this.messageContainerScrollTop - element.clientHeight <= 60;
    },
  },
  watch: {
    loading() {
      this.$nextTick(() => {
        this.$emit('loading', this.loading);
      })
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

      this.loading = true;
      await this.$nextTick();

      const loadToken = ++this.currentLoadToken; 
      const roomId = this.room.id;

      const resp = await this.$matrixService.loadRoomMessages(roomId);

      if (this.currentLoadToken !== loadToken) {
        return;
      }

      if (!resp.chunk?.length) {
        this.loading = false;
        return;
      }

      const allMessages = resp.chunk;
      const chunkSize = 10;
      const chunks = [];
      for (let i = 0; i < allMessages.length; i += chunkSize) {
        chunks.push(allMessages.slice(i, i + chunkSize).reverse());
      }

      // Process last chunk first
      const lastChunk = chunks.shift();
      const newestProcessed = await this.$matrixService.processMessages(roomId, lastChunk);

      if (this.currentLoadToken !== loadToken) {
        return;
      }

      this.messages = newestProcessed.messages;
      this.leftReactions = newestProcessed.leftReactions;
      await this.$nextTick();
      this.scrollToEnd();
      this.loading = false;

      for (const chunk of chunks) {
        await new Promise(resolve => requestAnimationFrame(resolve));
        const processed = await this.$matrixService.processMessages(roomId, chunk);

        if (this.currentLoadToken !== loadToken) {
          return;
        }
        const container = this.getMessagesContainerElement();
        if (!container) {
          return;
        }
        
        const prevScrollTop = container.scrollTop;
        const prevScrollHeight = container.scrollHeight;

        this.messages = [...processed.messages, ...this.messages];
        await this.$nextTick();
        await new Promise(resolve => requestAnimationFrame(resolve));

        if (prevScrollTop + container.clientHeight < prevScrollHeight - 10) {
          container.scrollTop = prevScrollTop + (container.scrollHeight - prevScrollHeight);
        } else {
          this.scrollToEnd();
        }
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
      if (this.isAtBottomMessages && this.messages?.length) {
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
    async handleUpdateUnseenData(event) {
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
      }, 500)
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
    },
    clearUnseenData() {
      this.$matrixService.clearUnseenMessages(this.room?.id, matrixUserId).then(() => {
        this.resetData();
        this.$root.channel.postMessage({type: 'reset-unseen-data'});
      })
    },
    getMessagesContainerElement() {
      return document.getElementById(this.messagesContainerId);
    },
    scrollToEnd() {
      const container = this.getMessagesContainerElement();
      if (!this.messages || !container) {
        return;
      }
      const lastMessageIndex = this.messages.length - 1;
      const lastMessageEl = document.getElementById(`chat-message-${lastMessageIndex}`);
      if (lastMessageEl) {
        lastMessageEl.scrollIntoView({behavior: 'auto'});
      }
      requestAnimationFrame(() => {
        // Force scroll to bottom in case last message is too tall
        container.scrollTop = container.scrollHeight;
        this.hasUnseenNewReceivedMessage = false;
        if (this.isInputFocused) {
          this.markRoomAsRead();
        }
      })
    },
    replyToMessage(message) {
      this.$root.$emit('reply-to-message', this.room?.id, this.messages, message)
    },
    async reactToMessage(emoji, targetMessage) {
      const existingReaction = targetMessage?.reactions?.find?.(reaction => reaction.key === emoji
          && reaction.userIds.includes(matrixUserId));
      if (existingReaction) {
        await this.removeReaction(emoji, targetMessage)
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
    onMessagesContainerScroll() {
      this.messageContainerScrollTop = this.getMessagesContainerElement().scrollTop;
      if (this.isAtBottomMessages) {
        this.hasUnseenNewReceivedMessage = false;
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
      if(this.loadingNewMessages || !this.hasMoreMessages || messagesDOMEl.scrollTop > 0) {
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
          const messagesToProcess = [...resp.chunk.reverse(), ...this.leftReactions];
          const processedMessages = await this.$matrixService.processMessages(this.room?.id, messagesToProcess);
          this.messages = [...processedMessages.messages, ...this.messages];
          this.leftReactions = processedMessages.leftReactions;
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
