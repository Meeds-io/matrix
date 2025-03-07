<template>
  <div class="chat-message-content">
    <div
      v-if="!sameDateAs(message.origin_server_ts, previousMessage.origin_server_ts)"
      class="mb-5 text-font-small-size font-weight-bold text-center"
      :class="{ 'mt-5' : previousMessage }">
      {{ formattedDate }}
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
              <span class="meeds-chat-contact-avatar-name mx-1 text-title text-subtitle-1 text-truncate" :style="userNameColor"> {{sender.profile && sender.profile.fullname}} </span>
            </div>
          </a>
        </div>
        <div class="chat-message-content-body py-2 px-3 mt-0-5" :class="messageContentClass">
          <div class="chat-message-content-text">
          {{ message.content.body }}
          </div>
          <v-tooltip bottom>
            <template #activator="{on, bind}">
              <div v-on="on"
                 v-bind="bind"
                 v-show="displayTimestamp"
                 class="text-font-small-size chat-message-content-timestamp">
                {{ formattedTimestamp }}
              </div>
            </template>
            <date-format :value="message.origin_server_ts" :format="dateFormat" />
          </v-tooltip>
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
        presenceClass: 'offline',
        dateFormat: {
          year: 'numeric',
          month: 'long',
          day: 'numeric',
          hour: 'numeric',
          minute: 'numeric',
        },
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
    beforeDestroy() {
    },
    computed: {
      formattedTimestamp() {
        const now = new Date().getTime();
        if(this.sameDateAs(this.message.origin_server_ts, now) && !this.nextMessage.timestamp) {
          return this.$t('matrix.chat.time.now');
        }
        const currentDate = new Date(this.message.origin_server_ts);
        return currentDate.toLocaleTimeString(eXo.env.portal.language.replace('_', '-'), {
          hour: "2-digit",
          minute: "2-digit",
        });
      },
      formattedDate() {
        return this.$matrixService.formatDate(this.message.origin_server_ts, true);
      },
      profileUrl() {
        return `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/profile/${this.sender.remoteId}`;
      },
      userNameColor() {
        return this.sender && this.$matrixService.getUserDisplayNameFontColor(this.sender.id);
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
          extraClass = 'ml-5';
        }
        return selfMessage ? `chat-message-from-self ${cssSameMessageSenderSelf} ${extraClass}`: `chat-message-from-others ${cssSameMessageSenderOthers} ${extraClass}`;
      },
      displaySender() {
        return this.previousMessage.sender !== this.message.sender && this.message.sender !== localStorage.getItem('matrix_user_id') && !this.room.directChat;
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
          return anotherMessageDate.getHours() === thisMessageDate.getHours()
            && anotherMessageDate.getMinutes() === thisMessageDate.getMinutes()
        } else {
          return false;
        }
      }
    }
  }
</script>
