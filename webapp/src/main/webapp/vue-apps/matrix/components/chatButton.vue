<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io

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
  <v-app>
    <v-btn
      id="btnChatButtonNew"
      :title="$t('matrix.chat.button.tooltip')"
      class="text-xs-center"
      icon
      @click="openDrawer">
      <v-badge
        :value="totalUnreadMessages > 0"
        :content="totalUnreadMessages <= 99 ? totalUnreadMessages : '99+'"
        color="var(--allPagesBadgePrimaryColor, #d32a2a)"
        flat
        overlap>
        <v-icon size="20" :color="presenceColor">fa-comments</v-icon>
      </v-badge>
    </v-btn>
    <matrix-chat-drawer
      v-if="open"
      ref="meedsChatDrawer"
      :rooms="rooms"
      :loading="loading"
      :presence="presence"
      @closed="open = false"/>
    <meeds-chat-quick-create-discussion-drawer/>
    <room-action-menu-drawer />
    <audio
      ref="messageAudio"
      class="hidden">
      <source src="/matrix/audio/notif.wav">
      <source src="/matrix/audio/notif.mp3">
      <source src="/chat/audio/notif.ogg">
    </audio>
  </v-app>
</template>
<script>

  export default {
    data: () => ({
      presence: 'online',
      open: false,
      totalUnreadMessages: 0,
      rooms: null,
      loading: false,
      messageEventQueue: [],
      processingMessageQueue: false,
      seenEventIds: new Map()
    }),
    created() {
      this.scheduleSeenEventsCleanup();
      const lastLoginOnMatrix = localStorage.getItem('matrix_last_login');
      const dayInMs = 24*60*60*1000;
      if(!lastLoginOnMatrix || (lastLoginOnMatrix && new Date().getTime() - lastLoginOnMatrix > dayInMs)) {
        this.$matrixService.dropUserData();
      }

      const matrixInfos = localStorage.getItem('matrix_user_id');
      if(!matrixInfos || matrixInfos !== matrixUserId) {
        this.$matrixService.checkAuthenticationTypes().then(enabled => {
          if(enabled) {
            this.$matrixService.authenticate().then(resp => {
              if(resp.user_id) {
                this.$matrixService.initUserData(resp);
                this.loadRooms();
                this.$matrixService.saveFilter().then(filterResponse => {
                  this.$matrixService.startMatrixSyncLoop(filterResponse.filter_id).then(() => this.presence = localStorage.getItem('matrix_user_presence'));
                  this.bindSyncPollingListeners(filterResponse.filter_id);
                });
                this.$matrixService.installPusher();
              } else {
                this.$root.$emit('alert-message', `${this.$t('meeds.matrix.login.failed')}`, 'error');
                this.$root.$emit('matrix-login-failed');
              }
            });
          } else {
            this.$root.$emit('alert-message', `${this.$t('meeds.matrix.jwt.disabled')}`, 'error');
          }
        });
      } else {
        this.loadRooms();
        this.$matrixService.saveFilter().then(filterResponse => {
          this.$matrixService.startMatrixSyncLoop(filterResponse.filter_id).then(() => this.presence = localStorage.getItem('matrix_user_presence'));
          this.bindSyncPollingListeners(filterResponse.filter_id);
        });
        this.$matrixService.installPusher();
      }

      this.$root.$on('chat-event-total-unread-updated', this.handleTotalUnreadUpdate);
      this.$root.$on('message-sent-statistics', this.sendMessageStatistics);
      document.addEventListener('matrix-message-received', event => this.enqueueMessageReceivedEvent(event));
      document.addEventListener('matrix-message-reaction-added', event => this.reactionReceived(event));
      document.addEventListener('matrix-message-deleted', this.messageDeleted);
      document.addEventListener('matrix-user-status-updated', event => this.userStatusUpdated(event));
      document.addEventListener(this.$chatConstants.ACTION_OPEN_CHAT_ROOM, event => this.openRoom(event.detail));
      document.addEventListener('matrix-room-mark-full-read', event => this.updateUnreadMessages(event));
    },
    mounted() {
      const urlParams = new URLSearchParams(window.location.search);
      if (urlParams.has('roomId') && urlParams.get('roomId')) {
        this.$matrixService.getRoomById(urlParams.get('roomId')).then(room => this.openRoom(room));
      }
      this.$nextTick().then(() => {
        this.$matrixService.registerUserToken();
      });
    },
    beforeDestroy() {
      this.$root.$off('chat-event-total-unread-updated',e => this.totalUnreadMessages = e);
      this.$root.$off('message-sent-statistics', this.sendMessageStatistics);
      document.removeEventListener('matrix-message-received', event => this.enqueueMessageReceivedEvent(event));
      document.removeEventListener('matrix-message-deleted', this.messageDeleted);
      document.removeEventListener('matrix-message-reaction-added', event => this.reactionReceived(event));
      document.removeEventListener('matrix-user-status-updated', event => this.userStatusUpdated(event));
      document.removeEventListener(this.$chatConstants.ACTION_OPEN_CHAT_ROOM, event => this.openRoom(event.detail));
      document.removeEventListener('matrix-room-mark-full-read', event => this.updateUnreadMessages(event));
    },
    watch: {
      open() {
        if (this.open) {
          this.$nextTick().then(() => this.$refs.meedsChatDrawer.open());
        }
      },
    },
    computed: {
      presenceClass() {
        return `chat-button-status-${this.presence}`;
      }
    },
    methods: {
      enqueueMessageReceivedEvent(event) {
        this.enableAndPlayBipSound(event);
        this.messageEventQueue.push(event);
        this.processNextMessageEvent();
      },
      async processNextMessageEvent() {
        if (this.processingMessageQueue || !this.messageEventQueue.length) {
          return;
        }

        this.processingMessageQueue = true;
        const event = this.messageEventQueue.shift();

        try {
          await this.handleMessageReceivedEvent(event);
        } catch (err) {
          console.error('Error while handling message event:', err);
        } finally {
          this.processingMessageQueue = false;
          await this.processNextMessageEvent();
        }
      },
      enableAndPlayBipSound() {
        const keyToCheck = 'matrix_allow_bip';
        if (event.detail?.message?.sender !== matrixUserId) {
          if (localStorage.getItem(keyToCheck) === null) {
            document.dispatchEvent(new CustomEvent('alert-message', {detail: {
              alertType: 'info',
              alertMessage: this.$t('matrix.message.allow.bip.ask'),
              alertTimeout: 5000000,
              alertLinkCallback: () =>
              {
                localStorage.setItem(keyToCheck, 'true');
                document.dispatchEvent(new CustomEvent('close-alert-message'));
              },
              alertLinkTooltip: this.$t('matrix.message.allow.bip.confirm'),
              alertLinkText: this.$t('matrix.message.allow.bip.link.text'),
              alertDismissCallback: () => localStorage.setItem(keyToCheck, 'false')
            }}));
          } else if (localStorage.getItem(keyToCheck) === 'true') {
            const roomIndex = this.rooms?.findIndex(room => room.id === event?.detail?.roomId);
            if (Number.isInteger(roomIndex) && !this.rooms[roomIndex].muted) {
              this.$refs.messageAudio.play().catch(err => {
                this.$root.$emit('alert-message', this.$t('matrix.message.audio.play.error'), 'error');
              });
            }
          }
        }
      },
      openDrawer() {
        this.open = true;
        this.$refs.meedsChatDrawer?.open();
      },
      async handleMessageReceivedEvent(event) {
        const {roomId, message} = event.detail || {};
        if (!roomId || !message || !message.event_id) {
          return;
        }
        if (this.seenEventIds.has(message.event_id)) {
          return;
        }
        this.seenEventIds.set(message.event_id, Date.now());

        const roomIndex = this.rooms?.findIndex(room => room.id === roomId);
        if (roomIndex === -1 || roomIndex == null) {
          return;
        }

        const existingRoom = this.rooms[roomIndex];
        const isNewMessageFromOtherUser = matrixUserId !== message.sender;

        const newUnreadCount = isNewMessageFromOtherUser
            ? existingRoom.unreadMessages + 1
            : existingRoom.unreadMessages;

        const messageText = message.content.format === 'org.matrix.custom.html'
            ? this.$matrixService.formatMentionsInRoomList(message.content.formatted_body)
            : message.content.body;

        const lastMessageContent = await this.buildLastMessageContent(
            message.sender,
            messageText,
            existingRoom
        );

        const updatedRoom = {
          ...existingRoom,
          lastMessage: {
            ...(existingRoom.lastMessage || {}),
            eventId: message.event_id,
          },
          unreadMessages: newUnreadCount,
          updated: message.origin_server_ts,
          lastMessageContent,
        };

        const updatedRooms = this.rooms.filter(room => room.id !== updatedRoom.id);
        updatedRooms.unshift(updatedRoom);
        this.rooms = updatedRooms;

        if (isNewMessageFromOtherUser) {
          this.totalUnreadMessages++;
        }
      },
      scheduleSeenEventsCleanup() {
        setInterval(() => {
          const now = Date.now();
          const maxAge = 10 * 60 * 1000;
          for (const [eventId, timestamp] of this.seenEventIds.entries()) {
            if (now - timestamp > maxAge) {
              this.seenEventIds.delete(eventId);
            }
          }
        }, 5 * 60 * 1000);
      },
      async reactionReceived(event) {
        const {roomId, message, user_id, emojiKey, targetMessageBody} = event.detail;
        const updatedRoomIndex = this.rooms?.findIndex?.(room => room.id === roomId);
        const updatedRoom = this.rooms?.[updatedRoomIndex];

        if (updatedRoom) {
          const updatedRooms = [...this.rooms];
          updatedRooms[updatedRoomIndex] = {
            ...updatedRoom,
            lastMessageContent: await this.buildLastReactionMessageContent(user_id, emojiKey, targetMessageBody, updatedRoom),
            updated: message.origin_server_ts,
          };
          this.rooms = updatedRooms;
        }
      },
      async messageDeleted(event) {
        const {roomId, eventId, redaction, sender} = event.detail;
        const updatedRoomIndex = this.rooms?.findIndex?.(room => room.id === roomId);
        const updatedRoom = this.rooms?.[updatedRoomIndex];

        if (updatedRoom) {
          if (updatedRoom.lastMessage?.eventId === eventId) {
            updatedRoom.lastMessageContent = await this.buildLastMessageContent(sender, this.$t('matrix.chat.message.deleted'), updatedRoom);
            updatedRoom.lastMessage.redacted = true;

            if (updatedRoom.unreadMessages === 1) {
              updatedRoom.unreadMessages--;
              this.totalUnreadMessages--;
              this.$matrixService.markRoomAsFullyRead(roomId, eventId);
            }
          }

          updatedRoom.updated = redaction.origin_server_ts;
          this.rooms.splice(updatedRoomIndex, 1);
          this.rooms.unshift(updatedRoom);
        }
      },
      updateUnreadMessages(event) {
        const updatedRoomIndex = this.rooms?.findIndex?.(room => room.id === event.detail.roomId);
        const updatedRoom = this.rooms?.[updatedRoomIndex];
        if(updatedRoom) {
          this.totalUnreadMessages -= updatedRoom.unreadMessages;
          updatedRoom.unreadMessages = 0;
        }
      },
      userStatusUpdated(event) {
        if(event.detail.userId === localStorage.getItem('matrix_user_id')) {
          this.presence = event.detail.presence;
        } else if(this.rooms) {
          const updatedUserStatusIndex = this.rooms.findIndex(room => room.dmMemberId === event.detail.userId);
          if(updatedUserStatusIndex >= 0) {
            this.rooms[updatedUserStatusIndex].presence = event.detail.presence;
          }
        }
      },
      loadRooms() {
        this.loading = true;
        this.$matrixService.loadChatRooms(localStorage.getItem('matrix_user_id')).then(matrixRoomsObject => {
          this.rooms = matrixRoomsObject.rooms || [];
          this.$root.$emit('chat-event-total-unread-updated', matrixRoomsObject.totalUnreadMessages);
        })
        .finally(() => {
          this.loading = false;
        });
      },
      addRoomIfNotExists(room) {
        if (!room?.id) {
          return;
        }
        const exists = this.rooms?.some(r => r.id === room.id);
        if (!exists) {
          this.rooms?.push(room);
        }
      },
      openRoom(event) {
        const room = event?.detail || event;
        this.addRoomIfNotExists(room);
        this.openDrawer();
        setTimeout(() => {
          this.$root.$emit("open-chat-discussion", roomId);
        }, 100);
      },
      sendMessageStatistics(message, room) {
        document.dispatchEvent(new CustomEvent('exo-statistic-message', {
          detail: {
            module: 'Chat',
            userId: eXo.env.portal.userIdentityId,
            userName: eXo.env.portal.userName,
            operation: 'sendMessage',
            parameters: {
              messageType: message.msgtype,
              roomType: room.spaceId ? 'space' : 'private',
              spaceId: room.spaceId,
            },
            timestamp: Date.now()
          }
        }));
      },
      async buildLastMessageContent(userId, message, updatedRoom) {
        const user = userId === matrixUserId ? this.$t('matrix.words.you') :
            (await this.$matrixService.getUserByMatrixId(userId, updatedRoom))?.profile?.fullname || userId;
        return this.$t('matrix.chat.lastMessage.pattern', {0: user, 1: message});
      },
      async buildLastReactionMessageContent(userId, emoji, message, updatedRoom) {
        const isSelf = userId === matrixUserId;
        let reactedBy = userId;
        if (!isSelf) {
          const user = await this.$matrixService.getUserByMatrixId(userId, updatedRoom);
          reactedBy = user?.profile?.fullname || userId;
        }
        return isSelf
          ? this.$t('matrix.message.you.reacted.with', { 0: emoji, 1: message })
          : this.$t('matrix.message.user.reacted.with', { 0: reactedBy, 1: emoji, 2: message });
      },
      bindSyncPollingListeners(matrixFilterId) {
        document.addEventListener('visibilitychange', () => {
          if (!document.hidden) {
            this.$matrixService.startMatrixSyncLoop(matrixFilterId).catch(console.error);
          }
        });
      }
    }
  };
</script>
