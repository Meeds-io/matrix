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
          <span class="mx-3 text-title text-truncate content-align">
            {{room.name}} <span v-if="room.external">{{ externalTag }}</span>
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
    </template>
    <template slot="content">
      <div id="chatMessagesContainer"
        v-touch="{
          down: () => loadMoreMessages()
        }">
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
      <div class="messageComposerContainer d-flex">
        <div
          id="messageComposerArea"
          ref="messageComposerArea"
          contenteditable="true"
          name="messageComposerArea"
          class="meeds-chat-composer pa-3"
          @keydown.enter=""
          @keypress.enter=""
          @keyup.enter="sendMessageWithEnter"
          @keyup.up=""
          @keyup="resizeComposerArea($event)"
          @focus="resizeComposerArea($event)"
          @paste="">
        </div>
        <div class="sendButtonArea d-flex flex-column justify-end">
          <v-btn
            class="matrix-chat-send-message-button btn-primary ms-4"
            v-if="!disableSendMessage"
            icon
            @click="sendMessage">
            <v-icon size="20">
              fa-paper-plane
            </v-icon>
          </v-btn>
        </div>
      </div>
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
      disableSendMessage: true,
      lastScrollTop: 0,
      roomActionComponents: [],
      initializedActions: [],
    };
  },
  mounted() {
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
    },
    enabledRoomActionComponents() {
      return this.roomActionComponents && this.roomActionComponents.filter(action => action.enabled) || [];
    },
    externalTag() {
      return `( ${this.$t('matrix.chat.user.external')} )`;
    },
  },

  created() {
    document.addEventListener('matrix-message-received', event => this.messageReceived(event));
    this.$root.$on('open-chat-discussion',e => this.openDiscussion(e));
    this.$root.$on('room-discussion-opened', () => this.initRoomActionComponents());
  },
  updated() {

  },
  watch:{
    room() {
      this.roomActionComponents = [];// reset the room actions to initialize them again when another roomis opened
    }
  },
  beforeDestroy() {
    document.removeEventListener('matrix-message-received', event => this.messageReceived(event));
    this.$root.$off('open-chat-discussion',e => this.openDiscussion(e));
    this.$root.$on('room-discussion-opened', () => this.initRoomActionComponents());
  },
  methods: {
    openDiscussion(e) {
      this.loading = true;
      this.room = e;
      this.$refs.ChatDiscussionDrawer?.open();
      this.$matrixService.loadRoomMessages(this.room.id).then(resp => {
        if(!resp.chunk || !resp.chunk.length || resp.chunk.length < this.$chatConstants.MESSAGES_LOAD_LIMIT) {
          this.hasMoreMessages = false;
        }
        this.from = resp.start;
        this.to = resp.end;
        this.messages = resp.chunk.reverse();
        this.$nextTick().then(() => {
          this.scrollToEnd();
          this.loading = false;
          this.$root.$emit('room-discussion-opened');
        });
      });
    },
    close(){
      this.messages = null;
      this.hasMoreMessages = true;
      this.disableSendMessage = true;
      this.$refs.messageComposerArea.innerHTML = '';
      this.$refs.ChatDiscussionDrawer?.close();
      this.initializedActions = [];
      this.roomActionComponents = [];
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
      if(this.messages) {
        const lastMessageIndex = this.messages.length - 1;
        const lastMessageElement = document.getElementById(`chat-message-${lastMessageIndex}`);
        if(lastMessageElement) {
          document.getElementById(`chat-message-${lastMessageIndex}`).scrollIntoView({
            behavior: 'instant'
          });
          this.$matrixService.markRoomAsFullyRead(this.room.id, this.messages[lastMessageIndex]?.event_id);
        }
      }
    },
    loadMoreMessages() {
      const messagesDOMEl = document.getElementById('chatMessagesContainer');
      const scrollTop = messagesDOMEl.scrollTop;
      if (scrollTop < this.lastScrollTop) {
        const composerDOMEl = document.getElementById('messageComposerArea');
        composerDOMEl.style = 'height: 40px'; // resize composer to original size == 1 line
      }
      this.lastScrollTop = scrollTop >= 0 && scrollTop || 0;
      if(this.loadingNewMessages || !this.hasMoreMessages || messagesDOMEl.scrollTop > 0) {
        return;
      }
      this.loadingNewMessages = true;
      const lastMessageId = this.messages[0].event_id;
      setTimeout( () => {
        this.$matrixService.loadRoomMessages(this.room.id, this.to).then(resp => {
          // check if there is no more messages
          if(!resp.chunk || !resp.chunk.length || resp.chunk.length < this.$chatConstants.MESSAGES_LOAD_LIMIT) {
            this.hasMoreMessages = false;
          }
          this.messages = [...resp.chunk.reverse(), ...this.messages];
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
    // composer functions
    resizeComposerArea(e) {
      const composerElement = e.target;
      composerElement.style.height = "auto";
      composerElement.style.height = composerElement.scrollHeight + "px";
      this.disableSendMessage = composerElement.innerText?.trim() === '';
    },
    sendMessageWithEnter(event) {
      const isMobile = this.$vuetify.breakpoint.name === 'sm' || this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'md';
      if (event && event.keyCode === this.$chatConstants.ENTER_CODE_KEY) {
        if (event.ctrlKey || event.altKey || event.shiftKey || isMobile) {
          this.insertNewLineAtCursor();
        } else {
          this.sendMessage();
        }
      }
    },
    sendMessage() {
      let message = this.$refs.messageComposerArea.innerText;
      message = message.trim();
      if(!message) {
        return;
      }
      this.$matrixService.sendMessage(message, this.room.id);
      this.$refs.messageComposerArea.innerHTML = '';
    },
    insertNewLineAtCursor() {
      let selection = window.getSelection();
      if (!selection.rangeCount) return;
      let range = selection.getRangeAt(0);
      let br = document.createElement('br');
      range.insertNode(br);
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
    }
  }
};
</script>
