<template>
  <exo-drawer
    id="ChatDiscussionDrawer"
    ref="ChatDiscussionDrawer"
    :loading="loading"
    v-draggable="true"
    allow-expand
    hide-footer-divider
    go-back-button
    right
    @expand-updated="handleExpand"
    @closed="close">
    <template slot="title">
      <a :href="url">
        <div class="d-flex">
          <v-badge
            :color="presenceColor"
            :value="room.directChat"
            class="my-auto mx-0 pa-0"
            content=""
            offset-x="10"
            offset-y="10"
            width="12"
            height="12"
            bordered
            bottom
            overlap
            dot>
            <v-avatar
              :tile="!room.directChat"
              :class="{'rounded-lg': !room.directChat}"
              width="36"
              min-width="36"
              height="36">
              <v-img
                :src="room.avatarUrl"
                :lazy-src="room.avatarUrl"
                :alt="room?.name" />
            </v-avatar>
          </v-badge>
          <span class="mx-3 text-title text-truncate content-align">
            {{room.name}}
            <span v-if="room.external">
              {{ externalTag }}
            </span>
          </span>
        </div>
      </a>
    </template>
    <template slot="titleIcons">
      <div class="room-action-components">
        <div
          v-for="action in enabledRoomActionComponents"
          :key="action.key"
          :class="`${action.appClass} ${action.typeClass}`"
          :ref="action.key">
          <div v-if="action.component">
            <component
              v-dynamic-events="action.component.events"
              v-bind="action.component.props ? action.component.props : {}"
              :is="action.component.name" />
          </div>
          <div v-else-if="action.element" v-html="action.element.outerHTML">
          </div>
          <div v-else-if="action.html" v-html="action.html">
          </div>
        </div>
      </div>
      <v-menu
        v-model="menu"
        content-class="border-radius overflow-hidden"
        :nudge-left="-30"
        open-on-click
        left
        close-on-content-click
        offset-x
        offset-y>
        <template #activator="{ on, attrs }">
          <v-btn
            v-on="on"
            v-bind="attrs"
            icon>
            <v-icon
              size="20"
              class="icon-default-color">
              fa-ellipsis-v
            </v-icon>
          </v-btn>
        </template>
        <v-list class="pa-0">
          <v-list-item
            v-if="canEditSpace"
            class="ps-2 pe-3 height-auto"
            @click="editSpace">
            <v-sheet
              class="d-flex"
              width="28"
              height="36">
              <v-icon
                class="icon-default-color mx-auto"
                size="16">
                fas fa-cog
              </v-icon>
            </v-sheet>
            {{ $t('matrix.room.space.editProperties') }}
          </v-list-item>
          <v-list-item
            class="ps-2 pe-3 height-auto"
            @click.stop="muteRoom">
            <v-sheet
              class="d-flex"
              width="28"
              height="36">
              <v-icon
                class="icon-default-color mx-auto"
                size="16">
                {{ isMuted ? 'fas fa-bell' : 'fas fa-bell-slash' }}
              </v-icon>
            </v-sheet>
            <span v-if="!isMuted">
              {{ $t('matrix.room.mute.label') }}
            </span>
            <span v-else>
              {{ $t('matrix.room.unmute.label') }}
            </span>
          </v-list-item>
        </v-list>
      </v-menu>
    </template>
    <template slot="content">
      <div
        id="chatMessagesContainer"
        ref="chatMessagesContainer"
        class="specific-scrollbar position-relative"
        v-touch="{
          down: () => loadMoreMessages()
        }"
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
        <sticky-arrow-button
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
        <sticky-arrow-button
          v-if="!loading && !isAtBottomMessages"
          :show-badge="hasUnseenNewReceivedMessage"
          :button-tooltip="$t('matrix.messages.jump.to.last')"
          class="mb-16 pb-2 me-5"
          @click="scrollToBottomMessages" />
      </div>
    </template>
    <template slot="footer">
      <v-sheet
        :max-width="composerContainerMaxWidth"
        :class="{'justify-self-center': expanded}"
        class="d-flex"
        width="100%">
        <message-upload-file-input
         :room="room"
         paste-target="messageComposerArea"
         drop-target="ChatDiscussionDrawer"
         class="me-2 mb-0_5 d-flex flex-column justify-end" />
        <div
          class="flex-grow-1 no-min-width border-radius-16"
          :class="{'border-color-grey-lighten': hasReplyQuote || messageToEdit}">
          <message-reply-quote
            v-if="hasReplyQuote"
            ref="replyQuote"
            :message="targetReplyMessage"
            :room="room"
            class="background-grey-primary no-min-width mx-2 mt-2"
            read-only
            closeable
            @close="cancelReply" />
          <message-edit-banner
            v-if="messageToEdit"
            ref="editMessageBanner"
            @close="cancelEditMessage" />
          <div
            :class="{'no-border': hasReplyQuote || messageToEdit}"
            class="d-flex border-color-grey-lighten border-radius-16">
            <div
              id="messageComposerArea"
              :placeholder="$t('matrix.chat.message.label')"
              ref="messageComposerArea"
              contenteditable="true"
              class="meeds-chat-composer specific-scrollbar text-break no-border input-placeholder border-box-sizing ps-3 pe-1 py-2"
              @keypress.enter.prevent
              @blur="isInputFocused = false"
              @keydown.enter="checkIfMentioning"
              @keydown.enter.prevent="sendMessageWithEnter"
              @keyup="resizeComposerArea"
              @focus="onInputFocus"
              @input="onComposerInput">
            </div>
            <div class="mb-0_5 me-1 d-flex flex-column justify-end">
              <emoji-picker-button
                :icon-size="20"
                @select-emoji="insertEmojiIntoComposer" />
            </div>
          </div>
        </div>
        <div class="d-flex flex-column justify-end">
          <v-btn
            :disabled="disableSendMessage"
            class="ms-2 mb-0_5"
            icon
            @click="sendMessage">
            <v-icon
              color="primary"
              size="20">
              fa-paper-plane
            </v-icon>
          </v-btn>
        </div>
        <emoji-suggester
          composer-id="messageComposerArea"
          :min-width="258"
          @select-emoji="insertEmojiIntoComposer" />
      </v-sheet>
      <exo-confirm-dialog
        ref="deleteConfirmDialog"
        :title="$t('matrix.chat.label.confirmDeleteTitle')"
        :message="$t('matrix.chat.label.confirmDeleteMessage')"
        :ok-label="$t('matrix.chat.label.confirm')"
        :cancel-label="$t('matrix.chat.label.cancel')"
        @ok="deleteMessage"
        @closed="messageToDelete = null" />
    </template>
  </exo-drawer>
</template>
<script>

export default {
  data() {
    return {
      messages: [],
      room: {},
      loading: false,
      loadingNewMessages: false,
      hasMoreMessages: true,
      lastScrollTop: 0,
      roomActionComponents: [],
      initializedActions: [],
      mentioningInProgress: false,
      leftReactions: [],
      composerDefaultHeight: 40,
      messageContent: null,
      insertedNewLine: false,
      targetReplyMessage: null,
      messageToEdit: null,
      messageToDelete: null,
      expanded: false,
      drawerWidth: 420,
      space: null,
      menu: false,
      roomLastReadReceipts: [],
      typingUsers: [],
      typingTimeout: null,
      unSeenMessagesData: {
        firstUnseenEventId: null,
        inViewport: {
          visibleTop: false,
          above: false,
          below: false
        }
      },
      messagesContainerId: 'chatMessagesContainer',
      messagesContainerElement: null,
      isInputFocused: false,
      hasUnseenNewReceivedMessage: false,
      messageContainerScrollTop: 0
    };
  },
  provide() {
    return {
      getIsExpanded: () => this.expanded,
      getParentDrawerWidth: () => this.drawerWidth
    };
  },
  computed: {
    isAtBottomMessages() {
      const element = this.getMessagesContainerElement();
      if (!element) {
        return true;
      }
      return element.scrollHeight - this.messageContainerScrollTop - element.clientHeight <= 60;
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
    isMuted() {
      return this.room?.muted;
    },
    spaceId() {
      return this.room?.spaceId;
    },
    presence() {
      return this.room?.presence
    },
    presenceColor() {
      return this.presence && this.$root.statusMap[this.presence];
    },
    canEditSpace() {
      return this.spaceId && this.space?.canEdit;
    },
    composerContainerMaxWidth() {
      return this.expanded && this.drawerWidth * 2 / 3 || undefined
    },
    hasReplyQuote() {
      return !!this.targetReplyMessage;
    },
    disableSendMessage() {
      return !this.messageContent?.trim()?.length;
    },
    url() {
      if(this.room?.directChat && this.room?.userId) {
        return `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/profile/${this.room.userId}`;
      } else if(this.room?.spaceId) {
        return `${eXo.env.portal.context}/s/${this.room.spaceId}`;
      } else {
        return '#';
      }
    },
    enabledRoomActionComponents() {
      return this.roomActionComponents && this.roomActionComponents.filter(action => action.enabled) || [];
    },
    externalTag() {
      return `( ${this.$t('matrix.chat.user.external')} )`;
    },
    isTyping() {
      return this.typingUsers.length > 0;
    }
  },
  created() {
    document.addEventListener('space-settings-updated', this.handleSpaceSettingsUpdate);
    document.addEventListener('matrix-message-received', this.messageReceived);
    document.addEventListener('matrix-message-deleted', this.messageDeleted);
    document.addEventListener('matrix-room-typing-received', this.handleTypingReceived);
    document.addEventListener('unseen-data-updated', this.handleUpdateUnseenData)
    this.$root.$on('open-chat-discussion', this.openDiscussion);
    this.$root.$on('room-discussion-opened', this.initRoomActionComponents);
    this.$root.$on('chat-edit-message', this.editMessage);
    this.$root.$on('chat-delete-message',  this.openDeleteMessageDialog);
    this.$root.channel.addEventListener('message', event => {
      const {type} = event.data;
      if (type === 'reset-unseen-data') {
        this.resetData();
      }
    });
  },
  watch:{
    room() {
      // reset the room actions to initialize them again when another room is opened
      this.roomActionComponents = [];
    }
  },
  beforeDestroy() {
    document.removeEventListener('matrix-message-received', this.messageReceived);
    document.removeEventListener('matrix-message-deleted', this.messageDeleted);
    document.removeEventListener('matrix-room-typing-received', this.handleTypingReceived);
    document.removeEventListener('unseen-data-updated', this.handleUpdateUnseenData)
    this.$root.$off('open-chat-discussion', this.openDiscussion);
    this.$root.$off('room-discussion-opened', this.initRoomActionComponents);
    this.$root.$off('chat-edit-message', this.editMessage);
    this.$root.$off('chat-delete-message', this.openDeleteMessageDialog);
  },
  methods: {
    muteRoom() {
      this.$matrixService.muteRoom(this.room.id, this.spaceId, this.isMuted).then(() => {
        this.$root.$emit(
          'alert-message',
          this.$t(`matrix.room.${!this.isMuted ? 'mute' : 'unmute'}.success`),
          'success');
        this.menu = false;
        setTimeout(() => {
          this.room.muted = !this.isMuted;
        }, 100)
      });
    },
    handleExpand(expanded) {
      setTimeout(() => {
        this.expanded = expanded;
        this.drawerWidth = this.$refs?.ChatDiscussionDrawer?.$el?.clientWidth;
      }, 300)
    },
    insertEmojiIntoComposer(emoji, range = null) {
      const composer = this.$refs.messageComposerArea;
      composer.focus();
      const selection = window.getSelection();
      let insertRange = range;
      if (!insertRange) {
        if (!selection || selection.rangeCount === 0) {
          composer.innerHTML += emoji;
          this.$matrixUtils.placeCaretAtEnd(composer);
          return;
        }
        insertRange = selection.getRangeAt(0).cloneRange();
      }
      selection.removeAllRanges();

      const emojiNode = document.createTextNode(emoji);
      insertRange.deleteContents();
      insertRange.insertNode(emojiNode);

      insertRange.setStartAfter(emojiNode);
      insertRange.setEndAfter(emojiNode);
      selection.addRange(insertRange);

      // Notify Vue it's updated
      const event = new Event('input', { bubbles: true });
      composer.dispatchEvent(event);
    },
    replyToMessage(targetMessage) {
      this.messageToEdit = null;
      this.$refs.messageComposerArea.innerHTML = '';
      this.targetReplyMessage = {
        ...targetMessage,
        replyTo: this.$matrixService.buildReplyToObject(this.messages, targetMessage.event_id)
      };
      this.$nextTick(() => {
        this.$refs?.messageComposerArea?.focus();
      })
    },
    cancelReply() {
      this.targetReplyMessage = null;
      this.$refs?.messageComposerArea?.focus();
    },
    cancelEditMessage() {
      this.messageToEdit = null;
      this.$refs.messageComposerArea.innerHTML = '';
      this.$refs?.messageComposerArea?.focus();
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
    async onComposerInput(event) {
      this.messageContent = event.target?.innerText;
      this.resizeComposerArea(event);
      await this.$matrixService.sendTyping(this.room.id, true);
      if (this.typingTimeout) {
        clearTimeout(this.typingTimeout);
      }
      this.typingTimeout = setTimeout(() => {
        this.$matrixService.sendTyping(this.room.id, false);
      }, 3000);
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
    onInputFocus(event) {
      setTimeout(() => {
        this.markRoomAsRead();
      }, 200)
      this.isInputFocused = true;
      this.resizeComposerArea(event)
    },
    markRoomAsRead() {
      if (this.isAtBottomMessages && this.messages?.length) {
        const lastMessageIndex = this.messages.length - 1;
        this.$matrixService.markRoomAsFullyRead(this.room.id, this.messages[lastMessageIndex]?.event_id).then(() => {
          document.dispatchEvent(new CustomEvent('matrix-room-mark-full-read', {
            detail: {roomId: this.room.id}
          }));
        });
      }
    },
    getMessagesContainerElement() {
      if (!this.messagesContainerElement) {
        this.messagesContainerElement = document.getElementById(this.messagesContainerId);
      }
      return this.messagesContainerElement;
    },
    resetComposer() {
      if (!this.$refs.messageComposerArea) {
        return;
      }
      this.$refs.messageComposerArea.style.height = `${this.composerDefaultHeight}px`;
      this.$refs.messageComposerArea.innerHTML = '';
      this.messageContent = null;
      this.insertedNewLine = false;
      this.targetReplyMessage = null;
    },
    async openDiscussion(e) {
      this.loading = true;
      this.room = e;

      this.resetData();
      this.roomLastReadReceipts = await this.$matrixService.loadLastReadReceipts(this.room?.id);
      this.room.lastReadReceipts = this.roomLastReadReceipts;

      this.getSpaceById(this.room?.spaceId);

      if (!this.$refs.ChatDiscussionDrawer?.drawer) {
        this.$refs.ChatDiscussionDrawer?.open();
      }

      await this.$nextTick();

      await new Promise(resolve => setTimeout(resolve, 50));

      // Load messages
      const resp = await this.$matrixService.loadRoomMessages(this.room.id);
      if (!resp.chunk || !resp.chunk.length || resp.chunk.length < this.$chatConstants.MESSAGES_LOAD_LIMIT) {
        this.hasMoreMessages = false;
      }
      this.from = resp.start;
      this.to = resp.end;

      // Process messages
      const processedMessages = await this.$matrixService.processMessages(this.room?.id, resp.chunk.reverse());
      this.messages = processedMessages.messages;
      this.leftReactions = processedMessages.leftReactions;

      await this.$nextTick();

      // slight delay before unseen messages
      setTimeout(async () => {
        await this.loadUnseenMessagesData();
      }, 500);

      // Scroll + finalize
      this.scrollToEnd();
      this.loading = false;
      this.$root.$emit("room-discussion-opened");
      this.$refs?.messageComposerArea?.focus();
    },
    close() {
      this.messages = null;
      this.hasMoreMessages = true;
      this.resetComposer();
      this.$refs.ChatDiscussionDrawer?.close();
      this.initializedActions = [];
      this.roomActionComponents = [];
      this.lastScrollTop = 0;
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
    resizeComposerArea() {
      if (this.room) {
        this.initSuggester();
      }

      const composerElement = this.$refs.messageComposerArea;
      const minHeight = this.composerDefaultHeight;
      const maxHeight = 300;

      composerElement.style.height = 'auto';
      const newHeight = composerElement.scrollHeight;

      if (newHeight < minHeight) {
        composerElement.style.height = `${minHeight}px`;
        composerElement.style.overflowY = 'hidden';
      } else if (newHeight <= maxHeight) {
        composerElement.style.height = `${newHeight}px`;
        composerElement.style.overflowY = 'hidden';
      } else {
        composerElement.style.height = `${maxHeight}px`;
        composerElement.style.overflowY = 'auto';
      }
    },
    sendMessageWithEnter(event) {
      const isMobile = this.$vuetify.breakpoint.name === 'sm'
                       || this.$vuetify.breakpoint.name === 'xs'
                       || this.$vuetify.breakpoint.name === 'md';
      if (event && event.keyCode === this.$chatConstants.ENTER_CODE_KEY && !this.mentioningInProgress) {
        if (event.ctrlKey || event.altKey || event.shiftKey || isMobile) {
          this.insertNewLineAtCursor();
        } else {
          this.sendMessage();
        }
      }
    },
    sendMessage() {
      let messageText = this.$refs.messageComposerArea.innerText;
      messageText = messageText.trim();
      if (!messageText) {
        return;
      }
      let message = {'body': messageText,
                     'msgtype': 'm.text'};
      let mentionsArray = [];
      this.$refs.messageComposerArea.querySelectorAll('span[data-user-id]').forEach(selectedSpan => {
          const userId = '@' + selectedSpan.getAttribute('data-user-id') + ':' + matrixServerName;
          mentionsArray.indexOf(userId) === -1 && mentionsArray.push(userId);
          });
      if(mentionsArray && mentionsArray.length) {
        const regexForMentions = /<span class="atwho-inserted"[\p{L} 0-9="\-_@<>:;\/#.()]*data-user-id="([^"]+)"[\p{L} 0-9="\-_@<>:;\/#.()]*data-user-name="([^"]+)"[\p{L} 0-9 ="\-_@<>:;\/#.()]*<\/span>/gu;
        const messageHTML = this.$refs.messageComposerArea.innerHTML.replace(regexForMentions, '<a href=\"https://matrix.to/#/@$1:' + matrixServerName + '\">$2</a>');
        message.format="org.matrix.custom.html";
        message.formatted_body=messageHTML;
        message['m.mentions'] = {'user_ids': mentionsArray}
      }
      if (this.targetReplyMessage) {
        message['m.relates_to'] = {
          'm.in_reply_to': {
            event_id: this.targetReplyMessage?.event_id
          }
        };
      }
      if (!this.messageToEdit) {
        this.$matrixService.sendMessage(message, this.room.id);
        this.$root.$emit('message-sent-statistics', message, this.room);
      } else {
        message['m.new_content'] = {
          'msgtype': 'm.text',
          'body': message.body,
          'm.mentions': message['m.mentions'] || {}
        };
        if (message.formatted_body) {
          message['m.new_content'].formatted_body = message.formatted_body;
          message['m.new_content'].format = message.format;
        }
        message['m.relates_to'] = {
          'rel_type': 'm.replace',
          'event_id': this.messageToEdit.event_id
        }
        this.$matrixService.sendMessage(message, this.room.id);
      }
      this.resetComposer();
      this.mentioningInProgress = false;
      this.messageToEdit = null;
      this.scrollToEnd();
    },
    checkIfMentioning() {
      this.mentioningInProgress = this.$refs.messageComposerArea.lastElementChild?.className === 'atwho-query' && !this.$refs.messageComposerArea.lastChild.wholeText;
    },
    insertNewLineAtCursor() {
      let selection = window.getSelection();
      if (!selection.rangeCount) {
        return;
      }
      let range = selection.getRangeAt(0);
      let br = document.createElement('br');
      range.insertNode(br);
      range.setStartAfter(br);
      if (!this.insertedNewLine) {
        const textNode = document.createTextNode('\u00a0');
        range.insertNode(textNode);
        this.insertedNewLine = true;
      }
      range.setStartAfter(br);
      range.setEndAfter(br);
      selection.removeAllRanges();
      selection.addRange(range);
    },
    initRoomActionComponents() {
      this.roomActionComponents = extensionRegistry ? extensionRegistry.loadExtensions('chat', 'chat-drawer-title-action-component') : [];
      this.$nextTick().then(() => {
        let chat = {
          currentUser: eXo.env.portal.userName,
          fullname: this.room.name,
          type: this.room.directChat && 'u' || 's',
          prettyName : this.room.prettyName,
          user: this.room.dmMemberId,
          spaceId: this.room.spaceId,
          participants: []
        };
        for (const action of this.roomActionComponents) {
          const actionInitialized = this.initializedActions.some(actionToCheck => actionToCheck.key === action.key);
          if (action.init && action.enabled && !actionInitialized) {
            let container = this.$refs[action.key];
            if (container && container.length > 0) {
              container = container[0];
              action.init(container, chat);
              this.initializedActions.push(action);
            }
          }
        }
      });
    },
    initSuggester() {
      const $messageSuggestor = $('#messageComposerArea');
      const component = this;
      const peopleSearchCached = {};
      let lastNoResultQuery = false;
      let space = null;

      const getSpace = async (spaceId) => {
        if (!spaceId || space) {
          return space;
        }
        const url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/spaces/${spaceId}`;
        const resp = await fetch(url, { credentials: 'include' });
        return resp.ok ? (space = await resp.json()) : null;
      };

      const retrievePeople = async (url) => {
        const resp = await fetch(url, { credentials: 'include' });
        return resp.ok ? resp.json() : [];
      };

      const cacheAndCallback = (key, query, results, callback) => {
        peopleSearchCached[key] = results;
        lastNoResultQuery = results.length ? false : query;
        callback(results);
      };

      const suggesterData = {
        type: 'mix',
        suffix: '\u00A0',
        create: false,
        createOnBlur: false,
        highlight: false,
        openOnFocus: false,
        closeAfterSelect: true,
        dropdownParent: 'body',
        hideSelected: true,
        renderMenuItem(item, parent) {
          parent.data('value', item.uid);
          return `<div class="avatarSmall" style="display: inline-block;"><img src="${item.avatar}"></div> ${item.name}`;
        },
        renderItem(item) {
          return `<span class="exo-mention" data-user-id="${item.uid}" data-user-name="${item.name}"><i aria-hidden="true" class="v-icon fa" style="font-size: 14px;"></i> ${item.name}<a href="#" class="remove"><i class="uiIconClose uiIconLightGray"></i></a></span>`;
        },
        sourceProviders: ['chat:users'],
        providers: {
          'chat:users': function (query, callback) {
            (async () => {
              const cleanQuery = query?.trim().toLowerCase();
              if (!cleanQuery) {
                return callback([]);
              }

              if (
                  lastNoResultQuery &&
                  cleanQuery.startsWith(lastNoResultQuery.toLowerCase()) &&
                  cleanQuery.length > lastNoResultQuery.length
              ) {
                return callback([]);
              }

              const room = component.room;
              const spaceId = room?.spaceId;
              const cacheKey = `${cleanQuery}#${spaceId || room.id}`;

              if (peopleSearchCached[cacheKey]) {
                return callback(peopleSearchCached[cacheKey]);
              }

              peopleSearchCached[cacheKey] = [];

              try {
                if (spaceId) {
                  const spaceData = await getSpace(spaceId);
                  const userName = eXo.env.portal.userName;

                  let url = `${eXo.env.portal.context}/${eXo.env.portal.rest}/social/people/suggest.json`;
                  url += `?nameToSearch=${encodeURIComponent(cleanQuery)}&typeOfRelation=member_of_space&currentUser=${encodeURIComponent(userName)}`;
                  if (spaceData?.prettyName) {
                    url += `&spaceURL=${encodeURIComponent(spaceData.prettyName)}`;
                  }

                  const users = await retrievePeople(url);
                  const results = users?.options?.map(user => ({
                    uid: user.value,
                    name: user.text,
                    avatar: user.avatarUrl,
                  })) || [];

                  return cacheAndCallback(cacheKey, cleanQuery, results, callback);

                } else {
                  // fallback when no spaceId (direct chat)
                  const participant = room.members?.find(member => member.id !== matrixUserId);
                  const memberId = participant?.id || room.dmMemberId;
                  const user = await component.$matrixService.getUserByMatrixId(memberId, room);

                  const filters = user?.profile?.properties ?? [];
                  const settings = filters
                    .filter(property => !!property.value)
                    .map(property => ({[property.propertyName]: property.value}));

                  const data = await component.$userService.getUsersByAdvancedFilter(
                      settings, 0, 10, '', 'all', cleanQuery, false, null, 'true'
                  );

                  const matchedUser = data?.users?.find(u => u.username === user?.profile?.username);

                  const results = matchedUser ? [{
                    uid: matchedUser.username,
                    name: matchedUser.fullname,
                    avatar: matchedUser.avatar,
                  }] : [];

                  return cacheAndCallback(cacheKey, cleanQuery, results, callback);
                }
              } catch (err) {
                console.error('Suggester error:', err);
                return callback([]);
              }
            })();
          }
        }
      };
      $messageSuggestor.suggester(suggesterData);
    },
    editMessage(message) {
      const composerArea = this.$refs.messageComposerArea;
      this.targetReplyMessage = null;
      this.messageToEdit = message;
      composerArea.innerHTML = message.content.formatted_body || message.content.body ;
      composerArea.focus();
      // Move the cursor to the end of the message
      const range = document.createRange();
      const selection = window.getSelection();
      range.setStart(composerArea, composerArea.childNodes.length);
      range.collapse(true);
      selection.removeAllRanges();
      selection.addRange(range);
    },
    openDeleteMessageDialog(e) {
      this.messageToDelete = e;
      this.$refs.deleteConfirmDialog.open();
    },
    deleteMessage() {
      if(this.messageToDelete?.event_id) {
        this.$matrixService.redactEvent(this.room.id, this.messageToDelete.event_id).then(deletionEvent => {
          this.$root.$emit('alert-message', this.$t('matrix.chat.delete.message.success'), 'success');
        })
        .catch(err => {
          this.$root.$emit('alert-message', this.$t('matrix.chat.delete.message.error'), 'error');
        });
      } else {
        this.$root.$emit('alert-message', this.$t('matrix.chat.delete.message.error'), 'error');
      }
    },
    editSpace() {
      window.require(['SHARED/spaceForm'], drawer => drawer.edit(this.space?.id));
    },
    async getSpaceById(spaceId) {
      if (this.space?.id === spaceId || !spaceId) {
        return;
      }
      try {
        this.space = await this.$spaceService.getSpaceById(spaceId, null, true);
      } catch (error) {
        console.error('Failed to fetch space:', error);
      }
    },
    handleSpaceSettingsUpdate(event) {
      this.space = event.detail;
      if (this.space.id !== this.room?.spaceId) {
        return;
      }
      this.room.name = this.space.displayName;
    },
    handleTypingReceived(event) {
      const {roomId, users} = event.detail;
      if (roomId !== this.room.id) {
        this.typingUsers = [];
        return;
      }
      this.typingUsers = users;
    },
    resetData() {
      this.typingUsers = [];
      if (!this.unSeenMessagesData?.viewPortInfo) {
        return;
      }
      this.unSeenMessagesData.firstUnseenEventId = null;
      this.unSeenMessagesData.viewPortInfo.visibleTop = true;
      this.unSeenMessagesData.viewPortInfo.above = false;
      this.unSeenMessagesData.viewPortInfo.below = false;
    },
    clearUnseenData() {
      this.$matrixService.clearUnseenMessages(this.room?.id, matrixUserId).then(() => {
        this.resetData();
        this.$root.channel.postMessage({type: 'reset-unseen-data'});
      })
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
    }
  },
};
</script>
