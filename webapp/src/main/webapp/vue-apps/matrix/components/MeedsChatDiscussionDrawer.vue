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
          @keydown.enter="checkIfMentioning"
          @keypress.enter="preventDefault"
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
      mentionsArray: [],
      mentioningInProgress: false
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

  mounted() {

  },
  updated() {

  },
  watch:{
    room() {
      this.roomActionComponents = [];// reset the room actions to initialize them again when another room is opened
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
        const receivedMessage = event.detail.message;
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
        composerDOMEl.style = 'height: 54px'; // resize composer to original size == 1 line
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
      if(this.room?.spaceId) {
        this.initSuggester();
      }
      const composerElement = e.target;
      composerElement.style.height = "auto";
      composerElement.style.height = Number(composerElement.scrollHeight + 4) + "px";
      this.disableSendMessage = composerElement.innerText?.trim() === '';
    },
    sendMessageWithEnter(event) {
      const isMobile = this.$vuetify.breakpoint.name === 'sm' || this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'md';
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
      if(!messageText) {
        return;
      }
      let message = {'body': messageText,
                     'msgtype': 'm.text'};
      this.mentionsArray = [];
      this.$refs.messageComposerArea.querySelectorAll('span[data-user-id]').forEach(selectedSpan => {
          const userId = '@' + selectedSpan.getAttribute('data-user-id') + ':' + matrixServerName;
          this.mentionsArray.indexOf(userId) === -1 && this.mentionsArray.push(userId);
          });
      if(this.mentionsArray && this.mentionsArray.length) {
        const regexForMentions = /<span class=\"atwho-inserted\"[a-z A-Z 0-9 =\"\-@<>:;\/#\.\(\)]*data-user-id=\"([^\"]+)\"[a-z A-Z 0-9 =\"\-@<>:;\/#\.\(\)]*data-user-name=\"([^\"]+)\"[a-z A-Z 0-9 =\"\-@<>:;\/#\.\(\)]*<\/span>/g;
        const messageHTML = this.$refs.messageComposerArea.innerHTML.replace(regexForMentions, '<a href=\"https://matrix.to/#/@$1:' + matrixServerName + '\">$2</a>');
        message.format="org.matrix.custom.html";
        message.formatted_body=messageHTML;
        message['m.mentions'] = {'user_ids': this.mentionsArray}
      }
      this.$matrixService.sendMessage(message, this.room.id, this.mentionsArray);
      this.$refs.messageComposerArea.innerHTML = '';
      this.mentionsArray = [];
      this.mentioningInProgress = false;
    },
    checkIfMentioning() {
      this.mentioningInProgress = this.$refs.messageComposerArea.lastElementChild?.className === 'atwho-query' && !this.$refs.messageComposerArea.lastChild.wholeText;
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
    },
    preventDefault(event) {
      if (event.keyCode === this.$chatConstants.ENTER_CODE_KEY) {
        event.stopPropagation();
        event.preventDefault();
      }
    },
    initSuggester() {
      const $messageSuggestor = $('#messageComposerArea');
      let peopleSearchCached = {};
      let lastNoResultQuery = false;
      let space = null;
      const getSpace = async function(spaceId) {
        if (!spaceId) {
          return Promise.resolve();
        }
        return space && Promise.resolve(space)
          || fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/spaces/${spaceId}`, {credentials: 'include'}).then(resp => resp?.ok && resp.json());
      };
      const retrievePeople = async function(url, query) {
        return !query?.length && Promise.resolve([]) || fetch(url, {credentials: 'include'})
          .then(resp => resp?.ok && resp.json())
      };
      const component = this;
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
          'chat:users': function(query, callback) {
            if (lastNoResultQuery && query.length > lastNoResultQuery.length) {
              if (query.substr(0, lastNoResultQuery.length) === lastNoResultQuery) {
                callback.call(this, []);
                return;
              }
            }
            const spaceId = component.room.spaceId;
            const key = `${query}#${spaceId}`;
            if (peopleSearchCached[key]) {
              callback.call(this, peopleSearchCached[key]);
            } else {
              peopleSearchCached[key] = [];
              getSpace(spaceId)
                .then(data => {
                  space = data;
                  const userName = eXo.env.portal.userName;
                  let url = eXo.env.portal.context + '/' + eXo.env.portal.rest + '/social/people/suggest.json?nameToSearch=' + query + '&typeOfRelation=member_of_space' + '&currentUser=' + userName;
                  if (space) {
                    url += '&spaceURL=' + space.prettyName;
                  }
                  return retrievePeople(url, query)
                    .then(users => {
                      if (users?.options?.length) {
                        users.options.forEach(user => {
                          peopleSearchCached[key].push({
                            uid: user.value,
                            name: user.text,
                            avatar: user.avatarUrl,
                          });
                        });
                      }
                      return peopleSearchCached[key];
                    });
                })
                .finally(() => {
                  if (peopleSearchCached[key].length == 0) {
                    lastNoResultQuery = query;
                  } else {
                    lastNoResultQuery = false;
                  }
                  callback.call(this, peopleSearchCached[key]);
                })
            }
          }
        }
      };
      //init suggester
      $messageSuggestor.suggester(suggesterData);
    },
  },
};
</script>
