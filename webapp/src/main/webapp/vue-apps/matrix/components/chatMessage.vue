<template>
  <div class="chat-message-content">
    <div v-if="displayDate" class="mb-5 text-font-small-size font-weight-bold text-center" :class="{ 'mt-5' : previousMessage }"> {{ formattedDate }} </div>
    <div class="px-4">
      <a :href="profileUrl">
        <div class="d-flex" v-if="displaySender">
          <div
            :style="`backgroundImage: url(${sender && sender.profile && sender.profile.avatar})`"
            class="meeds-chat-contact-avatar ma-0 size-8 d-flex rounded-circle">
            <div class="matrix-user-status size-3" :class="[presenceClass]"></div>
          </div>
          <span class="mx-3 text-title text-subtitle-1 text-truncate content-align" :style="userNameColor"> {{sender.profile && sender.profile.fullname}} </span>
        </div>
      </a>
      <div class="chat-message-content-body" :class="messageContentClass">
        <div class="chat-message-content-text">
        {{ message.content.body }}
        </div>
        <div v-if="displayTimestamp" class="text-font-extra-small-size chat-message-content-timestamp ">
          {{ formattedTimestamp }}
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
      }
    },
    data() {
      return {
        sender: {},
        timestamp: '',
        presenceClass: 'offline',
      };
    },
    created() {
      this.$matrixService.getUserByMatrixId(this.message.sender).then(sender => {
        this.timestamp = new Date(this.message.origin_server_ts);
        this.sender = sender;
        this.$matrixService.getUserPresence(this.message.sender).then(status => {
          this.presenceClass = `matrix-status-${status}`;
        })
      });
    },
    beforeDestroy() {
    },
    computed: {
      formattedTimestamp() {
        const currentDate = new Date(this.timestamp);
        return `${currentDate.getHours()}:${currentDate.getMinutes()}`;
      },
      formattedDate() {
        return this.$matrixService.formatDate(this.timestamp, true);
      },
      profileUrl() {
        return `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/profile/${this.sender.remoteId}`;
      },
      userNameColor() {
        return this.sender && this.$matrixService.getUserDisplayNameFontColor(this.sender.id);
      },
      messageContentClass() {
        let cssSameMessageSender = 'border-bottom-right-radius-16';
        if(localStorage.getItem('matrix_user_id') === this.nextMessage.sender) {
          cssSameMessageSender = 'border-bottom-right-radius-0';
        }
        if(localStorage.getItem('matrix_user_id') === this.previousMessage.sender) {
          cssSameMessageSender = `border-top-right-radius-0 ${cssSameMessageSender}`;
        } else {
          cssSameMessageSender = `border-top-right-radius-16 ${cssSameMessageSender}`;
        }
        return localStorage.getItem('matrix_user_id') === this.message.sender ? `chat-message-from-self py-1 px-3 mt-1 ${cssSameMessageSender}`: `chat-message-from-others ps-7 pe-3 pb-1 ${cssSameMessageSender}`;
      },
      displaySender() {
        return this.previousMessage.sender !== this.message.sender && this.message.sender !== localStorage.getItem('matrix_user_id') && !this.room.directChat;
      },
      displayTimestamp() {
        if(this.nextMessage) {
          const nextMessageDate = new Date(this.nextMessage.origin_server_ts);
          nextMessageDate.setSeconds(0,0);
          const currentMessageDate = new Date(this.timestamp);
          currentMessageDate.setSeconds(0,0);
          return nextMessageDate.getTime() !== currentMessageDate.getTime();
        } else {
          return true;
        }
      },
      displayDate() {
        if(this.previousMessage) {
          const previousMessageDate = new Date(this.previousMessage.origin_server_ts);
          const currentMessageDate = new Date(this.timestamp);
          return previousMessageDate.getDate() !== currentMessageDate.getDate()
            || previousMessageDate.getMonth() !== currentMessageDate.getMonth()
            || previousMessageDate.getFullYear() !== currentMessageDate.getFullYear();
        } else {
          return true;
        }
      }
    },
    methods: {

    }
  }
</script>
