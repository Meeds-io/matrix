<template>
  <exo-drawer
    ref="ChatDiscussionDrawer"
    id="ChatDiscussionDrawer"
    go-back-button
    right
    :loading="loading"
    @closed="close">
    <template slot="title">
      <a :href="url">
        <div class="d-flex">
          <div
            :style="`backgroundImage: url(${room.avatarUrl})`"
            :class="avatarBorderClass"
            class="meeds-chat-contact-avatar ma-0 size-9 d-flex">
            <div v-if="room.directChat" class="matrix-user-status size-2" :class="[presenceClass, avatarBorderClass]"></div>
          </div>
          <span class="mx-3 text-title text-truncate content-align"> {{room.name}} </span>
        </div>
      </a>
    </template>
    <template slot="content">
      <div class="d-flex flex-column">
        <meeds-chat-message :id="'chat-message-' + i" :ref="'message' + i" :key="i" v-for="(message, i) in messages" :message="message" :previous-message="i > 0 && messages[i-1]" :next-message="messages[i+1] || {}" :room="room"/>
      </div>
    </template>
    <template slot="footer">

    </template>
  </exo-drawer>
</template>
<script>

export default {
  name: 'ChatDiscussionDrawer',

  data() {
    return {
      messages: [],
      room: {},
      loading: false,
    };
  },
  computed: {
    presenceClass() {
      return this.room.presence && `matrix-status-${this.room.presence}` || 'matrix-status-offline';
    },
    avatarBorderClass() {
      return this.room.directChat ? 'rounded-circle' : 'rounded-lg';
    },
    url() {
      if(this.room.directChat && this.room.userId) {
        return `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/profile/${this.room.userId}`;
      } else if(this.room.spaceId) {
        return `${eXo.env.portal.context}/s/${this.room.spaceId}`;
      } else {
        return '#';
      }
    }
  },

  created() {
    document.addEventListener('matrix-message-received', event => this.messageReceived(event));
    document.addEventListener(this.$chatConstants.ACTION_CHAT_OPEN_DISCUSSION_DRAWER,e => this.openDiscussion(e));
  },
  updated() {

  },
  beforeDestroy() {
    document.removeEventListener('matrix-message-received', event => this.messageReceived(event));
    document.removeEventListener(this.$chatConstants.ACTION_CHAT_OPEN_DISCUSSION_DRAWER,e => this.openDiscussion(e));
  },

  methods: {
    openDiscussion(e) {
      this.loading = true;
      this.$refs.ChatDiscussionDrawer.open();
      this.room = e.detail;
      this.$matrixService.loadAllRoomMessages(this.room.id, false).then(resp => {
          this.messages = resp;
          this.$nextTick().then(() => {
            this.scrollToEnd();
          });
        });
      this.loading = false
    },
    close(){
      this.messages = null;
      this.$refs.ChatDiscussionDrawer?.close();
    },
    messageReceived(event) {
      if(this.room.id === event.detail.roomId && this.$refs.ChatDiscussionDrawer.drawer) {
        const receivedMessage = {sender: event.detail.sender, content:{body: event.detail.message},origin_server_ts: event.detail.origin_server_ts};
        this.messages.push(receivedMessage);
        setTimeout( () => {
          this.scrollToEnd();
        }, 50);
      }
    },
    scrollToEnd() {
      if(this.messages) {
        const lastMessageElement = document.getElementById(`chat-message-${this.messages.length - 1}`);
        if(lastMessageElement) {
          document.getElementById(`chat-message-${this.messages.length - 1}`).scrollIntoView({
            behavior: 'smooth'
          });
        }
      }
    }
  }
};
</script>
