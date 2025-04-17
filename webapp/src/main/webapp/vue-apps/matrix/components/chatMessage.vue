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
  <div class="chat-message-content">
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
      :class="{'mt-3' : message.sender !== previousMessage.sender}"
      >
      <div class="d-relative">
        <div class="avatar-of-user mt-3" v-if="displaySender">
          <a :href="profileUrl">
            <div class="d-flex">
              <div
                :style="`backgroundImage: url(${sender && sender.profile && sender.profile.avatar})`"
                class="meeds-chat-contact-avatar ma-0 size-8 d-flex rounded-circle">
              </div>
              <span
                class="meeds-chat-contact-avatar-name mx-1 text-title text-subtitle-1 text-truncate"
                :style="userNameColor">
                {{sender.profile && sender.profile.fullname || message.sender}}
                <span v-if="sender.profile?.dataEntity?.external === 'true'">
                  {{ externalTag }}
                </span>
              </span>
            </div>
          </a>
        </div>
        <meeds-chat-message-content
          :message="message"
          :display-sender="displaySender"
          :class="messageContentClass"
          :display-timestamp="displayTimestamp"
          :timestamp="formattedTimestamp"/>
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
      };
    },
    created() {
      this.$matrixService.getUserByMatrixId(this.message.sender).then(sender => {
        this.sender = sender;
        this.$matrixService.getUserPresence(this.message.sender).then(status => {
          this.presenceClass = `matrix-status-${status}`;
        })
      });
    },
    computed: {
      displaySender() {
        return this.previousMessage.sender !== this.message.sender && this.message.sender !== localStorage.getItem('matrix_user_id') && !this.room.directChat;
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
        let extraClass='';
        if(!this.room.directChat && !selfMessage) {
          extraClass = 'ml-5 mt--4';
        }
        return selfMessage ? `chat-message-from-self ${cssSameMessageSenderSelf} ${extraClass}`: `chat-message-from-others ${cssSameMessageSenderOthers} ${extraClass}`;
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
      profileUrl() {
        return `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/profile/${this.sender.remoteId}`;
      },
      userNameColor() {
        return this.sender && this.$matrixService.getUserDisplayNameFontColor(this.sender.id);
      },
      externalTag() {
        return `( ${this.$t('matrix.chat.user.external')} )`;
      },
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
    }
  }
</script>
