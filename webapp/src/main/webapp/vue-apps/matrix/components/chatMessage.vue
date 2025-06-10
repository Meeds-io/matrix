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
        'mb-3':!nextMessage,
        'mb-1': nextMessage
      }"
      @mouseleave="closeMenu() && cancelPress()"
      @mouseenter="openMenu"
      @touchstart="startPress"
      @touchend="cancelPress"
      @touchmove="cancelPress"
      @mousedown="startPress"
      @mouseup="cancelPress">
      <div
        v-if="!sameDateAs(message.origin_server_ts, previousMessage.origin_server_ts)"
        class="mb-5 text-font-small-size font-weight-bold text-center"
        :class="{ 'mt-5' : previousMessage, 'mt-2' : !previousMessage,  }">
        <v-chip color="primaryBackground" class="message-date-chip">
          {{ formattedDate }}
        </v-chip>
      </div>
      <div
        :id="message.event_id"
        class="px-4"
        :class="{'mt-3' : message.sender !== previousMessage.sender}">
        <div class="d-relative">
          <message-user
            v-if="displaySender"
            class="mb-n4 width-fit-content"
            :room="room"
            :sender-id="message.sender" />
          <div
            class="message-container full-width position-relative"
            :class="{
              'ms-4': !isMyMessage && !room.directChat,
              'float-right': isMyMessage,
              'float-left': !isMyMessage}">
            <v-menu
              v-model="parentMenu"
              :offset-x="isMyMessage"
              :nudge-right="isMyMessage ? -249 : 20"
              :close-on-content-click="false"
              :nudge-top="-10"
              content-class="no-min-width border-radius-8"
              offset-y
              right
              top
              attach>
              <template #activator="{ on, attrs }">
                <div
                  v-bind="attrs"
                  v-on="on">
                  <meeds-chat-message-content
                    :message="message"
                    :display-sender="displaySender"
                    :css-class="messageContentClass"
                    :display-timestamp="displayTimestamp"
                    :next-message="nextMessage"
                    :is-self-message="isSelfMessage"
                    :timestamp="formattedTimestamp"
                    :room="room" />
                </div>
              </template>
              <message-action-list
                ref="actionList"
                :message="message"
                @reply="$emit('reply', message)"
                @reaction="$emit('reaction', $event, message)" />
            </v-menu>
            <div
              class="message-reactions d-flex flex-wrap"
              :class="{'justify-end': isMyMessage}">
              <message-reaction-item
                v-for="reaction in message.reactions"
                :key="reaction.key"
                :reaction="reaction"
                :is-my-message="isMyMessage"
                @reaction="$emit('reaction', $event, message)" />
            </div>
          </div>
        </div>
      </div>
    </div>
  </v-hover>
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
      }
    },
    data() {
      return {
        sender: {},
        presenceClass: 'offline',
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
        childMenu: ''
      };
    },
    created() {
      this.$matrixService.getUserByMatrixId(this.message.sender, this.room).then(sender => {
        this.sender = sender;
        this.$matrixService.getUserPresence(this.message.sender).then(status => {
          this.presenceClass = `matrix-status-${status}`;
        })
      });
      document.addEventListener('matrix-message-reaction-added', this.reactionAdded);
      document.addEventListener('matrix-message-reaction-removed', this.reactionRemoved);
      this.$root.$on('close-message-menu', this.closeMenu);
      this.$root.$on('close-message-child-menu', this.closeChildMenu);
      this.$root.$on('open-message-child-menu', this.openChildMenu);
      this.$root.$on('force-close-message-menu', this.forceCloseMenu);
    },
    beforeDestroy() {
      document.removeEventListener('matrix-message-reaction-added', event => this.reactionAdded);
      document.removeEventListener('matrix-message-reaction-removed', this.reactionRemoved);
      this.$root.$off('close-message-menu', this.closeMenu);
      this.$root.$off('close-message-child-menu', this.closeChildMenu);
      this.$root.$off('open-message-child-menu', this.openChildMenu);
      this.$root.$off('force-close-message-menu', this.forceCloseMenu);
    },
    computed: {
      displaySender() {
        return !this.isMyMessage && ((this.previousHasReactions && !this.room.directChat) ||
            ((this.previousMessage.sender !== this.message.sender ||
                    !this.sameDateAs(this.message.origin_server_ts, this.previousMessage.origin_server_ts)) &&
                !this.room.directChat));
      },
      previousHasReactions() {
        return this.previousMessage?.reactions?.length > 0;
      },
      isMyMessage() {
        return localStorage.getItem('matrix_user_id') === this.message.sender;
      },
      messageContentClass() {
        const selfMessage = localStorage.getItem('matrix_user_id') === this.message.sender;
        let cssSameMessageSenderSelf = 'border-bottom-right-radius-16';
        let cssSameMessageSenderOthers = 'border-bottom-left-radius-16';
        if(this.message.sender === this.nextMessage.sender && this.sameDateAs(this.message.origin_server_ts, this.nextMessage.origin_server_ts)) {
          cssSameMessageSenderSelf = 'border-bottom-right-radius-0';
          cssSameMessageSenderOthers = 'border-bottom-left-radius-0';
        }
        if(this.message.sender === this.previousMessage.sender && this.sameDateAs(this.message.origin_server_ts, this.previousMessage.origin_server_ts)) {
          cssSameMessageSenderSelf = `border-top-right-radius-0 ${cssSameMessageSenderSelf}`;
          cssSameMessageSenderOthers = `border-top-left-radius-0 ${cssSameMessageSenderOthers}`;
        } else {
          cssSameMessageSenderSelf = `border-top-right-radius-16 ${cssSameMessageSenderSelf}`;
          cssSameMessageSenderOthers = `border-top-left-radius-16 ${cssSameMessageSenderOthers}`;
        }
        return selfMessage ? `chat-message-from-self ${cssSameMessageSenderSelf}`: `chat-message-from-others ${cssSameMessageSenderOthers}`;
      },
      displayTimestamp() {
        if(this.nextMessage && this.message.sender === this.nextMessage.sender) {
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
        if(this.sameTimeAs(this.message.origin_server_ts, now) && !this.nextMessage.origin_server_ts) {
          return this.$t('matrix.chat.time.now');
        }
        const currentDate = new Date(this.message.origin_server_ts);
        return currentDate.toLocaleTimeString(eXo.env.portal.language.replace('_', '-'), {
          hour: "2-digit",
          minute: "2-digit",
        });
      },
      formattedDate() {
        let today = new Date();
        const todayTime = today.setHours(0,0,0,0);
        const messageDate = new Date(this.message.origin_server_ts);
        const messageDateTime = messageDate.setHours(0,0,0,0);
        if(this.$timeUtils.isSameDay(today, this.message.origin_server_ts)) {
          return this.$t('matrix.chat.time.today');
        } else if(this.$timeUtils.differenceInDays(todayTime, messageDateTime) === 1) { // one day before
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
      }
    },
    methods: {
      sameDateAs(thisMessageTime, anotherMessageTime) {
        if(anotherMessageTime) {
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
        if(anotherMessageTime) {
          const anotherMessageDate = new Date(anotherMessageTime);
          const thisMessageDate = new Date(thisMessageTime);
          return anotherMessageDate.getDate() === thisMessageDate.getDate()
            && anotherMessageDate.getHours() === thisMessageDate.getHours()
            && anotherMessageDate.getMinutes() === thisMessageDate.getMinutes()
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
      startPress(e) {
        if (e.type === 'click' && e.button !== 0) return;
        this.pressTimer = setTimeout(() => {
          this.onLongPress();
        }, 500);
      },
      cancelPress() {
        clearTimeout(this.pressTimer);
        this.pressTimer = null;
      },
      onLongPress() {
        this.openMenu();
      },
      openMenu() {
        if(this.childMenu === '') {
          this.parentMenu = true;
        }
      },
      closeMenu() {
        if(this.childMenu !== this.message.event_id) {
          this.childMenu = '';
          this.parentMenu = false;
        }
      },
      openChildMenu() {
        this.childMenu = this.message.event_id;
      },
      closeChildMenu() {
        this.childMenu = '';
      },
      forceCloseMenu() {
        this.childMenu = '';
        this.parentMenu = false;
      }
    }
  }
</script>
