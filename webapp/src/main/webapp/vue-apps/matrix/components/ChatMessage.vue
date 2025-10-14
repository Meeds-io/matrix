<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io

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
    class="chat-message-content"
    :class="{
      'mb-2':!nextMessage,
      'no-select': isMobile
    }"
    v-touch-hold="openMenu"
    @mouseleave="!isMobile && closeMenu()"
    @mouseenter="!isMobile && openMenu()">
    <div
      v-if="!sameDateAs(message.origin_server_ts, previousMessage.origin_server_ts)"
      class="mb-5 text-font-small-size font-weight-bold text-center"
      :class="{ 'mt-5' : previousMessage, 'mt-2' : !previousMessage, }">
      <v-chip color="primaryBackground" class="message-date-chip">
        {{ formattedDate }}
      </v-chip>
    </div>
    <v-divider
      v-if="showUnSeenMessageSeparator && showUnseen"
      v-intersect="onIntersect"
      id="unseenSeparator"
      class="mx-4 my-4 primary-border-color primary" />
    <div
      :id="message.event_id"
      class="px-4"
      :class="{'mt-3' : message.sender !== previousMessage.sender}">
      <div class="d-relative">
        <matrix-message-user
          v-if="displaySender"
          class="mb-n4 width-fit-content"
          :room="room"
          :sender-id="message.sender" />
        <div
          :id="`message${message.origin_server_ts}`"
          class="message-container full-width position-relative"
          :class="{
            'ms-4': !isMyMessage && !room.directChat,
            'float-right': isMyMessage,
            'float-left': !isMyMessage}">
          <v-menu
            :disabled="isRedacted"
            v-model="parentMenu"
            :offset-x="isMyMessage"
            :nudge-right="isMyMessage && -276 || 20"
            :close-on-content-click="false"
            :open-on-click="!isMobile"
            :attach="`#message${message.origin_server_ts}`"
            :nudge-top="-10"
            content-class="no-min-width border-radius"
            offset-y
            right
            top>
            <template #activator="{ on, attrs }">
              <div
                v-bind="attrs"
                v-on="on">
                <matrix-chat-message-content
                  :message="message"
                  :display-sender="displaySender"
                  :css-class="messageContentClass"
                  :display-timestamp="displayTimestamp"
                  :next-message="nextMessage"
                  :is-self-message="isSelfMessage"
                  :timestamp="formattedTimestamp"
                  :room="room"
                  :is-redacted="isRedacted" />
              </div>
            </template>
            <matrix-message-action-list
              ref="actionList"
              :message="message"
              :is-my-message="isMyMessage"
              @close="closeMenuOnMobile"
              @reply="replyToMessage"
              @edit="editMessage"
              @delete="deleteMessage"
              @reaction="reactToMessage" />
          </v-menu>
          <div
            v-if="!isRedacted && hasReactions"
            class="position-sticky mt-n1 d-flex flex-wrap"
            :class="{'justify-end': isMyMessage}">
            <matrix-message-reaction-item
              v-for="reaction in message.reactions"
              :key="reaction.key"
              :reaction="reaction"
              :room="room"
              :is-my-message="isMyMessage"
              @reaction="$emit('reaction', $event, message)" />
          </div>
          <v-sheet
            v-if="hasLastReaders"
            height="24">
            <matrix-message-receipt-list
              :room="room"
              :read-receipts="readReceipts"
              :class="{
                'mt-2': !hasReactions,
                'me-3': !isMyMessage
              }"
              class="d-flex justify-end" />
          </v-sheet>
        </div>
      </div>
    </div>
  </div>
</template>
<script>

export default {
  props: {
    message: {
      type: Object,
      default: {},
    },
    previousMessage: {
      type: Object,
      default: {},
    },
    nextMessage: {
      type: Object,
      default: {},
    },
    room: {
      type: Object,
      default: {},
    },
    unseenMessagesData: {
      type: Object,
      default: null
    },
    isInputFocused: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      sender: {},
      dateFormat: {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: 'numeric',
        minute: 'numeric',
      },
      defaultThumbnailMaxWidth: 345,
      defaultThumbnailMaxHeight: 275,
      menu: false,
      parentMenu: false,
      childMenu: null,
      touchHoldTimeout: null,
      touchMoved: false,
      touchStartY: 0,
      ignoreClickUntil: 0,
      readReceipts: [],
      loadingReceipts: false,
      showUnseen: true,
      hideTimeout: null
    };
  },
  created() {
    this.loadMessageReadReceipts();
    this.$matrixService.getUserByMatrixId(this.message.sender, this.room).then(sender => {
      this.sender = sender;
    });
    document.addEventListener('matrix-message-reaction-added', this.reactionAdded);
    document.addEventListener('matrix-message-reaction-removed', this.reactionRemoved);
    document.addEventListener('matrix-message-read', this.handleMessageRead);
    document.addEventListener('click', this.onClickOutside, true);
    document.addEventListener('touchstart', this.onClickOutside, true);
    this.$root.$on('message-child-menu-closed', this.closeChildMenu);
    this.$root.$on('message-child-menu-opened', this.openChildMenu);
  },
  beforeDestroy() {
    document.removeEventListener('matrix-message-reaction-added', this.reactionAdded);
    document.removeEventListener('matrix-message-reaction-removed', this.reactionRemoved);
    document.removeEventListener('click', this.onClickOutside, true);
    document.removeEventListener('touchstart', this.onClickOutside, true);
    this.$root.$off('message-child-menu-opened', this.openChildMenu);
    this.$root.$off('message-child-menu-closed', this.closeChildMenu);
  },
  watch: {
    isInputFocused() {
      if (this.isInputFocused && this.isUnseenInViewPort && this.showUnSeenMessageSeparator) {
        this.startHideUnseenSeparatorTimer();
      }
    }
  },
  computed: {
    firstUnseenEventId() {
      const eventId = this?.unseenMessagesData?.firstUnseenEventId;
      return eventId !== null ? String(eventId) : null;
    },
    showUnSeenMessageSeparator() {
      return this.unseenViewPortInfo?.below === false && this.message?.event_id === this.firstUnseenEventId;
    },
    unseenViewPortInfo() {
      return this.unseenMessagesData?.viewPortInfo;
    },
    isUnseenInViewPort() {
      return this.unseenViewPortInfo?.visibleTop === true;
    },
    hasLastReaders() {
      return this.message?.hasLastReaders;
    },
    displaySender() {
      return !this.isMyMessage && ((this.previousHasReactions && !this.room.directChat) ||
            ((this.previousMessage.sender !== this.message.sender ||
                    !this.sameDateAs(this.message.origin_server_ts, this.previousMessage.origin_server_ts)) &&
                !this.room.directChat));
    },
    previousHasReactions() {
      return this.previousMessage?.reactions?.length > 0;
    },
    hasReactions() {
      return this.message?.reactions?.length > 0;
    },
    isMyMessage() {
      return localStorage.getItem('matrix_user_id') === this.message.sender;
    },
    isMobile() {
      return this.$root.isMobile;
    },
    messageContentClass() {
      const selfMessage = localStorage.getItem('matrix_user_id') === this.message.sender;
      let cssSameMessageSenderSelf = 'border-bottom-right-radius-16';
      let cssSameMessageSenderOthers = 'border-bottom-left-radius-16';
      if (this.message.sender === this.nextMessage.sender && this.sameDateAs(this.message.origin_server_ts, this.nextMessage.origin_server_ts)) {
        cssSameMessageSenderSelf = 'border-bottom-right-radius-0';
        cssSameMessageSenderOthers = 'border-bottom-left-radius-0';
      }
      if (this.message.sender === this.previousMessage.sender && this.sameDateAs(this.message.origin_server_ts, this.previousMessage.origin_server_ts)) {
        cssSameMessageSenderSelf = `border-top-right-radius-0 ${cssSameMessageSenderSelf}`;
        cssSameMessageSenderOthers = `border-top-left-radius-0 ${cssSameMessageSenderOthers}`;
      } else {
        cssSameMessageSenderSelf = `border-top-right-radius-16 ${cssSameMessageSenderSelf}`;
        cssSameMessageSenderOthers = `border-top-left-radius-16 ${cssSameMessageSenderOthers}`;
      }
      return selfMessage ? `chat-message-from-self ${cssSameMessageSenderSelf}`: `chat-message-from-others ${cssSameMessageSenderOthers}`;
    },
    displayTimestamp() {
      if (this.nextMessage && this.message.sender === this.nextMessage.sender) {
        const nextMessageDate = new Date(this.nextMessage.origin_server_ts);
        nextMessageDate.setSeconds(0,0);
        const currentMessageDate = new Date(this.message.origin_server_ts);
        currentMessageDate.setSeconds(0,0);
        return nextMessageDate.getTime() !== currentMessageDate.getTime();
      } else {
        return true;
      }
    },
    formattedTimestamp() {
      const now = new Date().getTime();
      if (this.sameTimeAs(this.message.origin_server_ts, now) && !this.nextMessage.origin_server_ts) {
        return this.$t('matrix.chat.time.now');
      }
      const currentDate = new Date(this.message.origin_server_ts);
      return currentDate.toLocaleTimeString(eXo.env.portal.language.replace('_', '-'), {
        hour: '2-digit',
        minute: '2-digit',
      });
    },
    formattedDate() {
      const today = new Date();
      const todayTime = today.setHours(0,0,0,0);
      const messageDate = new Date(this.message.origin_server_ts);
      const messageDateTime = messageDate.setHours(0,0,0,0);
      if (this.$timeUtils.isSameDay(today, this.message.origin_server_ts)) {
        return this.$t('matrix.chat.time.today');
      } else if (this.$timeUtils.differenceInDays(todayTime, messageDateTime) === 1) { // one day before
        return this.$t('matrix.chat.time.yesterday');
      } else {
        return this.$matrixService.formatDateString(this.message.origin_server_ts);
      }
    },
    externalTag() {
      return `( ${this.$t('matrix.chat.user.external')} )`;
    },
    isSelfMessage() {
      return localStorage.getItem('matrix_user_id') === this.message?.sender;
    },
    isRedacted() {
      return !this.message.content.body && this.message.redacted_because?.redacts;
    },
    roomLastReadReceipts() {
      return this.room?.lastReadReceipts;
    }
  },
  methods: {
    reactToMessage(emoji) {
      this.$emit('reaction', emoji, this.message);
      this.closeMenuOnMobile();
    },
    replyToMessage() {
      this.$emit('reply', this.message);
      this.closeMenuOnMobile();
    },
    editMessage(message) {
      this.$root.$emit('edit-message', this.room.id, message);
    },
    deleteMessage(message) {
      this.$root.$emit('delete-message', this.room.id, message);
    },
    closeMenuOnMobile() {
      if (this.isMobile) {
        this.parentMenu = false;
      }
    },
    sameDateAs(thisMessageTime, anotherMessageTime) {
      if (anotherMessageTime) {
        const anotherMessageDate = new Date(anotherMessageTime);
        const thisMessageDate = new Date(thisMessageTime);
        return anotherMessageDate.getDate() === thisMessageDate.getDate()
            && anotherMessageDate.getMonth() === thisMessageDate.getMonth()
            && anotherMessageDate.getFullYear() === thisMessageDate.getFullYear();
      } else {
        return false;
      }
    },
    sameTimeAs(thisMessageTime, anotherMessageTime) {
      if (anotherMessageTime) {
        const anotherMessageDate = new Date(anotherMessageTime);
        const thisMessageDate = new Date(thisMessageTime);
        return anotherMessageDate.getDate() === thisMessageDate.getDate()
            && anotherMessageDate.getHours() === thisMessageDate.getHours()
            && anotherMessageDate.getMinutes() === thisMessageDate.getMinutes();
      } else {
        return false;
      }
    },
    reactionAdded(event) {
      if (this.room.id === event.detail.roomId && this.message.event_id === event.detail.message.content['m.relates_to'].event_id) {
        this.message = this.$matrixService.processMessageReaction(this.message, event.detail.message);
      }
    },
    reactionRemoved(event) {
      const {roomId, targetEventId, emoji, userId} = event.detail;

      if (this.room.id !== roomId || this.message.event_id !== targetEventId) {
        return;
      }
      const map = this.message.reactionsMap;
      if (!map || !map.has(emoji)) {
        return;
      }

      const entry = map.get(emoji);
      entry.userIds = entry.userIds.filter(id => id !== userId);
      if (!entry?.userIds?.length) {
        map.delete(emoji);
      }

      this.message.reactions = Array.from(map.values());
    },
    openMenu() {
      if (!this.childMenu) {
        this.parentMenu = true;
      }
    },
    closeMenu() {
      if (this.childMenu !== this.message.event_id) {
        this.childMenu = null;
        this.parentMenu = false;
      }
    },
    openChildMenu() {
      this.childMenu = this.message.event_id;
    },
    closeChildMenu() {
      this.childMenu = null;
    },
    onClickOutside(event) {
      const menuContent = this.$refs.actionList?.$el;
      const messageContainer = this.$el;

      if (
        this.parentMenu &&
            menuContent && !menuContent.contains(event.target) &&
            messageContainer && !messageContainer.contains(event.target)
      ) {
        this.closeMenu();
      }
    },
    loadMessageReadReceipts() {
      if (!this.hasLastReaders) {
        return;
      }
      this.loadingReceipts = true;
      this.$matrixService.loadReadReceiptsForMessage(this.roomLastReadReceipts, this.message.event_id).then(users => {
        this.readReceipts = users;
      }).finally(() => this.loadingReceipts = false);
    },
    handleMessageRead({detail: {eventId, userId}}) {
      if (this.message.event_id !== eventId && this.readReceipts.includes(userId)) {
        this.readReceipts = this.readReceipts.filter(u => u !== userId);
      } else if (this.message.event_id === eventId) {
        if (!this.readReceipts.includes(userId) && userId !== matrixUserId) {
          this.readReceipts = [...this.readReceipts, userId];
        }
      }
      this.$set(this.message, 'hasLastReaders', this.readReceipts.length > 0);
    },
    onIntersect(entries) {
      const entry = entries[0];
      if (entry.isIntersecting) {
        if (this.isUnseenInViewPort && !this.isInputFocused) {
          return;
        }
        this.startHideUnseenSeparatorTimer();
      } else {
        this.clearHideUnseenSeparatorTimer();
      }
    },
    startHideUnseenSeparatorTimer() {
      this.clearHideUnseenSeparatorTimer();
      this.hideTimeout = setTimeout(() => {
        this.showUnseen = false;
        this.$emit('reset-unseen');
      }, 2000);
    },
    clearHideUnseenSeparatorTimer() {
      if (this.hideTimeout) {
        clearTimeout(this.hideTimeout);
        this.hideTimeout = null;
      }
    }
  }
};
</script>
