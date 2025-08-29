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
          <div
            :style="`backgroundImage: url(${room.avatarUrl})`"
            :class="avatarBorderClass"
            class="flex-shrink-0 meeds-chat-contact-avatar ma-0 size-9 d-flex">
            <div
             v-if="room.directChat"
             :class="[presenceClass, avatarBorderClass]"
             class="matrix-user-status size-2" />
          </div>
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
        v-if="canEditSpace"
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
        </v-list>
      </v-menu>
    </template>
    <template slot="content">
      <div id="chatMessagesContainer"
        class="specific-scrollbar"
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
            :id="`chat-message-${i}`"
            :ref="`chat-message-${i}`"
            :key="message.event_id"
            v-for="(message, i) in messages"
            :message="message"
            :previous-message="messages?.[i - 1]"
            :next-message="messages?.[i + 1]"
            :room="room"
            @reply="replyToMessage"
            @reaction="reactToMessage" />
        </div>
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
              @keydown.enter="checkIfMentioning"
              @keydown.enter.prevent="sendMessageWithEnter"
              @keyup="resizeComposerArea"
              @focus="resizeComposerArea"
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
      drawerWidth: 420
    };
  },
  provide() {
    return {
      getIsExpanded: () => this.expanded,
      getParentDrawerWidth: () => this.drawerWidth
    };
  },
  computed: {
    composerContainerMaxWidth() {
      return this.expanded && this.drawerWidth * 2 / 3 || undefined
    },
    hasReplyQuote() {
      return !!this.targetReplyMessage;
    },
    disableSendMessage() {
      return !this.messageContent?.trim()?.length;
    },
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
    document.addEventListener('matrix-message-deleted', this.messageDeleted);
    this.$root.$on('open-chat-discussion',e => this.openDiscussion(e));
    this.$root.$on('room-discussion-opened', () => this.initRoomActionComponents());
    this.$root.$on('chat-edit-message', e => this.editMessage(e));
    this.$root.$on('chat-delete-message', e => this.openDeleteMessageDialog(e));
  },
  watch:{
    room() {
      this.roomActionComponents = [];// reset the room actions to initialize them again when another room is opened
    }
  },
  beforeDestroy() {
    document.removeEventListener('matrix-message-received', event => this.messageReceived(event));
    document.removeEventListener('matrix-message-deleted', this.messageDeleted);
    this.$root.$off('open-chat-discussion',e => this.openDiscussion(e));
    this.$root.$off('room-discussion-opened', () => this.initRoomActionComponents());
    this.$root.$off('chat-edit-message', e => this.editMessage(e));
    this.$root.$off('chat-delete-message', e => this.openDeleteMessageDialog(e));
  },
  methods: {
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
    onComposerInput(event) {
      this.messageContent = event.target?.innerText;
      this.resizeComposerArea(event);
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
    openDiscussion(e) {
      this.loading = true;
      this.room = e;
      if (!this.$refs.ChatDiscussionDrawer?.drawer) {
        this.$refs.ChatDiscussionDrawer?.open();
      }
      this.$nextTick(() => {
        // Slight delay allows browser to paint before heavy JS
        setTimeout(async () => {
          const resp = await this.$matrixService.loadRoomMessages(this.room.id);

          if (!resp.chunk || !resp.chunk.length || resp.chunk.length < this.$chatConstants.MESSAGES_LOAD_LIMIT) {
            this.hasMoreMessages = false;
          }

          this.from = resp.start;
          this.to = resp.end;

          const processedMessages = this.$matrixService.processMessages(resp.chunk.reverse());
          this.messages = processedMessages.messages;
          this.leftReactions = processedMessages.leftReactions;

          this.$nextTick().then(() => {
            this.scrollToEnd();
            this.loading = false;
            this.$root.$emit('room-discussion-opened');
          });
        }, 0);
      });
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
          this.scrollToEnd();
        }, 50);
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
        composerDOMEl.style.height = `${this.composerDefaultHeight}px`;
      }
      this.lastScrollTop = scrollTop >= 0 && scrollTop || 0;
      if(this.loadingNewMessages || !this.hasMoreMessages || messagesDOMEl.scrollTop > 0) {
        return;
      }
      this.loadingNewMessages = true;
      const lastMessageId = this.messages[0].event_id;
      setTimeout(() => {
        this.$matrixService.loadRoomMessages(this.room.id, this.to).then(resp => {
          // check if there is no more messages
          if(!resp.chunk || !resp.chunk.length || resp.chunk.length < this.$chatConstants.MESSAGES_LOAD_LIMIT) {
            this.hasMoreMessages = false;
          }
          const messagesToProcess = [...resp.chunk.reverse(), ...this.leftReactions];
          const processedMessages = this.$matrixService.processMessages(messagesToProcess);
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
      if (this.room?.spaceId) {
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
      if (this.space?.id === this.room?.spaceId || !spaceId) {
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
    }
  },
};
</script>
