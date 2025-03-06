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
      <div id="chatMessagesContainer">
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
          class="d-flex flex-column"
          @wheel="loadMoreMessages"
          @scroll="loadMoreMessages">
          <meeds-chat-message
            :id="'chat-message-' + i"
            :ref="'message' + i"
            :key="i"
            v-for="(message, i) in messages"
            :message="message"
            :previous-message="i > 0 && messages[i-1]"
            :next-message="i < (messages.length - 1) && messages[i+1]"
            :room="room"/>
        </div>
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
      loadingNewMessages: false,
      hasMoreMessages: true,
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
      if(this.room?.directChat && this.room?.userId) {
        return `${eXo.env.portal.context}/${eXo.env.portal.metaPortalName}/profile/${this.room.userId}`;
      } else if(this.room?.spaceId) {
        return `${eXo.env.portal.context}/s/${this.room.spaceId}`;
      } else {
        return '#';
      }
    }
  },

  created() {
    document.addEventListener('matrix-message-received', event => this.messageReceived(event));
    document.addEventListener(this.$chatConstants.ACTION_OPEN_CHAT_ROOM,e => this.openDiscussion(e.detail));
    this.$root.$on('open-chat-discussion',e => this.openDiscussion(e));
  },
  updated() {

  },
  beforeDestroy() {
    document.removeEventListener('matrix-message-received', event => this.messageReceived(event));
    document.removeEventListener(this.$chatConstants.ACTION_OPEN_CHAT_ROOM,e => this.openDiscussion(e.detail));
    this.$root.$off('open-chat-discussion',e => this.openDiscussion(e));
  },

  methods: {
    openDiscussion(e) {
      this.loading = true;
      this.room = e;
      this.$matrixService.loadRoomMessages(this.room.id).then(resp => {
        if(!resp.chunk || !resp.chunk.length) {
          this.hasMoreMessages = false;
        }
        this.messages = resp.chunk.reverse();
        this.from = resp.start;
        this.to = resp.end;
        this.$nextTick().then(() => {
          this.scrollToEnd();
        });
        this.$refs.ChatDiscussionDrawer?.open();
      }).finally(() => {
        this.loading = false;
      });;
    },
    close(){
      this.messages = null;
      this.hasMoreMessages = true;
      this.$refs.ChatDiscussionDrawer?.close();
    },
    messageReceived(event) {
      if(this.room?.id === event.detail.roomId && this.$refs.ChatDiscussionDrawer?.drawer) {
        const receivedMessage = {sender: event.detail.sender, content:{body: event.detail.message},origin_server_ts: event.detail.origin_server_ts};
        this.messages.push(receivedMessage);
        setTimeout( () => {
          this.scrollToEnd();
        }, 50);
      }
    },
    scrollToEnd() {
      setTimeout( () => {
        if(this.messages) {
          const lastMessageElement = document.getElementById(`chat-message-${this.messages.length - 1}`);
          if(lastMessageElement) {
            document.getElementById(`chat-message-${this.messages.length - 1}`).scrollIntoView({
              behavior: 'smooth'
            });
          }
        }
      }, 100);
    },
    loadMoreMessages(e) {
      const messagesDOMEl = document.getElementById('chatMessagesContainer');
      console.log(messagesDOMEl.scrollTop);
      if(this.loadingNewMessages || !this.hasMoreMessages || messagesDOMEl.scrollTop > 0) {
        return;
      }
      this.loadingNewMessages = true;
      const lastMessageId = this.messages[0].event_id;
      setTimeout( () => {
        this.$matrixService.loadRoomMessages(this.room.id, this.to).then(resp => {
          // check if there is no more messages
          if(!resp.chunk || !resp.chunk.length) {
            this.hasMoreMessages = false;
          }
          this.messages = [...resp.chunk.reverse(), ...this.messages];
          this.from = resp.start;
          this.to = resp.end;
        }).finally(() => {
          this.$nextTick().then(() => {
            document.getElementById(lastMessageId).scrollIntoView({
              behavior: 'smooth'
            });
          });

          this.loadingNewMessages = false;
        });
      }, 1000);
    }
  }
};
</script>
