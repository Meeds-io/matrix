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
      ref="meedsChatDrawer"
      :rooms="sortedRooms"
      :loading-rooms="loading"
      :presence="presence"
      @filter-updated="handleFilterUpdate" />
    <matrix-chat-discussion-drawer
      ref="ChatDiscussionDrawer"
      :rooms="sortedRooms"
      :presence="presence"
      :loading-rooms="loading"
      @filter-updated="handleFilterUpdate" />
    <matrix-chat-quick-create-discussion-drawer />
    <matrix-room-action-menu-drawer />
    <matrix-message-read-receipt-list-drawer />
    <exo-confirm-dialog
      ref="deleteConfirmDialog"
      :title="$t('matrix.chat.label.confirmDeleteTitle')"
      :message="$t('matrix.chat.label.confirmDeleteMessage')"
      :ok-label="$t('matrix.chat.label.confirm')"
      :cancel-label="$t('matrix.chat.label.cancel')"
      @ok="deleteMessage"
      @closed="onDialogClosed" />
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
    presence: 'offline',
    open: false,
    totalUnreadMessages: 0,
    rooms: null,
    loading: false,
    messageEventQueue: [],
    processingMessageQueue: false,
    seenEventIds: new Map(),
    userName: eXo?.env?.portal.userName,
    presenceInterval: null,
    isPresencePollingOwner: false,
    presencePollingKey: 'presence_polling_owner',
    messageToDelete: null,
    targetRoomId: null,
    searchTimer: null,
    searchTerm: null,
  }),
  created() {
    this.tryBecomePresencePollingOwner();
    this.getUserStatus();
    this.scheduleSeenEventsCleanup();
    this.setupBroadcastChannelListener();
    const lastLoginOnMatrix = localStorage.getItem('matrix_last_login');
    const dayInMs = 24*60*60*1000;
    if (!lastLoginOnMatrix || (lastLoginOnMatrix && new Date().getTime() - lastLoginOnMatrix > dayInMs)) {
      this.$matrixService.dropUserData();
    }
    const matrixInfos = localStorage.getItem('matrix_user_id');
    if (!matrixInfos || matrixInfos !== matrixUserId) {
      this.$matrixService.checkAuthenticationTypes().then(enabled => {
        if (enabled) {
          this.$matrixService.authenticate().then(resp => {
            if (resp.user_id) {
              this.$matrixService.initUserData(resp);
              this.loadRooms();
              this.$matrixService.saveFilter().then(filterResponse => {
                this.$matrixService.startMatrixSyncLoop(filterResponse.filter_id);
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
        this.$matrixService.startMatrixSyncLoop(filterResponse.filter_id);
        this.bindSyncPollingListeners(filterResponse.filter_id);
      });
      this.$matrixService.installPusher();
    }

    this.$root.$on('chat-event-total-unread-updated', this.handleTotalUnreadUpdate);
    this.$root.$on('message-sent-statistics', this.sendMessageStatistics);
    this.$root.$on('room-muted-updated', this.handleRoomMuteUpdate);
    document.addEventListener('matrix-message-received', this.enqueueMessageReceivedEvent);
    document.addEventListener('matrix-message-reaction-added', this.reactionReceived);
    document.addEventListener('matrix-message-deleted', this.messageDeleted);
    document.addEventListener(this.$chatConstants.ACTION_OPEN_CHAT_ROOM, this.openRoom);
    document.addEventListener('matrix-room-mark-full-read', this.updateUnreadMessages);
    document.addEventListener('user-status-updated', this.handleCurrentUserStatusUpdated);
    document.addEventListener('space-unmuted', this.handleSpaceUnmute);
    document.addEventListener('space-muted', this.handleSpaceMute);
    document.addEventListener('chat-ws-message-received', this.handleWSMessageReceived);
    window.addEventListener('beforeunload', this.handleBeforeUnload);
    window.addEventListener('storage', this.handleLocaleStorageUpdate);
    this.$root.$on('delete-message',  this.openDeleteMessageDialog);
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
    this.$root.$off('chat-event-total-unread-updated',this.handleTotalUnreadUpdate);
    this.$root.$off('message-sent-statistics', this.sendMessageStatistics);
    this.$root.$off('room-muted-updated', this.handleRoomMuteUpdate);
    document.removeEventListener('matrix-message-received', this.enqueueMessageReceivedEvent);
    document.removeEventListener('matrix-message-deleted', this.messageDeleted);
    document.removeEventListener('matrix-message-reaction-added', this.reactionReceived);
    document.removeEventListener(this.$chatConstants.ACTION_OPEN_CHAT_ROOM, this.openRoom);
    document.removeEventListener('matrix-room-mark-full-read', this.updateUnreadMessages);
    document.removeEventListener('user-status-updated', this.handleCurrentUserStatusUpdated);
    document.removeEventListener('space-unmuted', this.handleSpaceUnmute);
    document.removeEventListener('chat-ws-message-received', this.handleWSMessageReceived);
    document.removeEventListener('space-muted', this.handleSpaceMute);
    window.removeEventListener('beforeunload', this.handleBeforeUnload);
    window.removeEventListener('storage', this.handleLocaleStorageUpdate);
    this.$root.$off('delete-message',  this.openDeleteMessageDialog);
    this.releasePresencePollingOwner();
  },
  watch: {
    open() {
      if (this.open) {
        this.$nextTick().then(() => this.$refs.meedsChatDrawer.open());
      }
    },
    sortedRooms: {
      handler() {
        this.$matrixService.cacheRooms(JSON.stringify(this.sortedRooms));
      },
      deep: true,
    }
  },
  computed: {
    presenceColor() {
      return this.presence && this.$root.statusMap[this.presence];
    },
    isUserStatusAvailable() {
      return this.presence === 'available';
    },
    filteredRooms() {
      if (!this.searchTerm) {
        return this.rooms;
      }
      const normalize = str =>
        str?.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase() || '';

      const normalizedSearch = normalize(this.searchTerm);

      return this.rooms.filter(room =>
        normalize(room.name).includes(normalizedSearch)
      );
    },
    sortedRooms() {
      if (!Array.isArray(this.filteredRooms)) {
        return [];
      }
      return [...this.filteredRooms].sort(
        (a, b) =>
          (b.updated || 0) - (a.updated || 0) ||
              a.name?.localeCompare?.(b.name, undefined, {numeric: true}) || 0
      );
    },
    isSoundOwner() {
      return this.isPresencePollingOwner;
    }
  },
  methods: {
    handleFilterUpdate(text) {
      this.loading = true;
      if (this.searchTimer) {
        clearTimeout(this.searchTimer);
      }
      this.searchTimer = setTimeout(() => {
        this.searchTerm = text;
        this.loading = false;
      }, 300);
    },
    handleTotalUnreadUpdate(total) {
      this.totalUnreadMessages = total;
    },
    updateTotalUnread(value, isDecrement = false, broadcast = false) {
      this.totalUnreadMessages = Math.max(0, this.totalUnreadMessages + (isDecrement ? -value : value));
      if (broadcast) {
        this.postBroadcastUpdateTotalUnread();
      }
    },
    postBroadcastUpdateTotalUnread() {
      this.$root.channel.postMessage({
        type: 'total-unread-messages-updated',
        payload: {totalUnreadMessages: this.totalUnreadMessages}
      });
    },
    getUserStatus() {
      return this.$userStateService.getUserStatus(this.userName).then(data => {
        this.presence = data?.status;
      });
    },
    tryBecomePresencePollingOwner() {
      const now = Date.now();
      const ownerRaw = localStorage.getItem(this.presencePollingKey);
      const owner = ownerRaw ? JSON.parse(ownerRaw) : null;

      if (!owner || now - owner.timestamp > 35000) {
        const myOwner = {id: this._uid, timestamp: now};
        localStorage.setItem(this.presencePollingKey, JSON.stringify(myOwner));
        this.becomePresencePollingOwner();
      }
    },
    becomePresencePollingOwner() {
      if (this.isPresencePollingOwner) {
        return;
      }
      this.isPresencePollingOwner = true;

      if (this.presencePollingInterval) {
        clearInterval(this.presencePollingInterval);
      }

      this.refreshUserStatus();
      this.presencePollingInterval = setInterval(() => {
        if (document.hidden || !navigator.onLine) {
          return;
        }
        this.refreshUserStatus();
      }, 30000 + Math.floor(Math.random() * 5000));
    },
    async refreshUserStatus() {
      this.getUserStatus();
      if (this.rooms?.length) {
        this.rooms = await Promise.all(
          this.rooms.map(async room => {
            if (room.directChat && room.dmMemberId) {
              const userData = await this.$userStateService.getUserStatus(room.dmMemberId);
              this.$root.channel.postMessage({
                type: 'user-status-updated',
                payload: {userId: room.dmMemberId, status: userData?.status}
              });
              return {...room, presence: userData?.status};
            }
            return room;
          })
        );
      }
    },
    handleCurrentUserStatusUpdated({detail: {userId, status}}) {
      if (this.userName === userId) {
        this.presence = status;
        this.$root.channel.postMessage({
          type: 'user-status-updated',
          payload: {userId: userId, status: this.presence}
        });
      }
    },
    handleUserStatusUpdated({userId, status}) {
      if (userId === this.userName && this.presence !== status) {
        this.presence = status;
        return;
      }
      const updatedRooms = this.rooms?.map(room => (room.directChat && room.dmMemberId === userId
        && room.presence !== status ?
        {...room, presence: status} : room));
      if (updatedRooms) {
        this.rooms = updatedRooms;
      }
    },
    handleBeforeUnload() {
      this.releasePresencePollingOwner();
    },
    enqueueMessageReceivedEvent(event) {
      this.enableAndPlayBipSound(event);
      this.messageEventQueue.push(event);
      this.handleUnseenMessages(event);
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
    async handleUnseenMessages({ detail: { roomId, message } }) {
      if (message.sender === matrixUserId) {
        return;
      }
      const lastReadMessage = await this.$matrixService.loadLastReadReceipts(roomId);
      const lastReadMessageTimestamp = lastReadMessage?.[matrixUserId]?.ts || 0;

      if (message.origin_server_ts > lastReadMessageTimestamp) {
        let unseenData = await this.$matrixService.getUnseenMessages(roomId, matrixUserId);
        if (!unseenData) {
          unseenData = {};
        }
        if (!unseenData.firstUnseenEventId) {
          unseenData.firstUnseenEventId = message.event_id;
        }
        await this.$matrixService.saveUnseenMessages(roomId, matrixUserId, unseenData);
      }
    },
    enableAndPlayBipSound({detail: {roomId, message}}) {
      const keyToCheck = 'matrix_allow_bip';
      if (message.sender !== matrixUserId) {
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
          const roomIndex = this.rooms?.findIndex(room => room.id === roomId);
          if (Number.isInteger(roomIndex) && !this.rooms[roomIndex].muted
              && this.isUserStatusAvailable && this.isSoundOwner) {
            this.$refs.messageAudio.play().catch(() => {
              this.$root.$emit('alert-message', this.$t('matrix.message.audio.play.error'), 'error');
            });
          }
        }
      }
    },
    openDeleteMessageDialog(roomId, message) {
      this.messageToDelete = message;
      this.targetRoomId = roomId;
      this.$refs.deleteConfirmDialog.open();
    },
    onDialogClosed() {
      this.messageToDelete = null;
      this.targetRoomId = null;
    },
    deleteMessage() {
      if (this.messageToDelete?.event_id) {
        this.$matrixService.redactEvent(this.targetRoomId, this.messageToDelete.event_id).then(() => {
          this.$root.$emit('alert-message', this.$t('matrix.chat.delete.message.success'), 'success');
        }).catch(() => {
          this.$root.$emit('alert-message', this.$t('matrix.chat.delete.message.error'), 'error');
        });
      } else {
        this.$root.$emit('alert-message', this.$t('matrix.chat.delete.message.error'), 'error');
      }
    },
    openDrawer() {
      this.open = true;
      this.$refs.meedsChatDrawer?.open();
    },
    async handleMessageReceivedEvent(event) {
      if (this.loading || !this.rooms) {
        return;
      }
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

      if (isNewMessageFromOtherUser && !updatedRoom.muted) {
        this.updateTotalUnread(1);
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
            this.updateTotalUnread(1, true);
            this.$matrixService.markRoomAsFullyRead(roomId, eventId).then(() => {
              updatedRoom.unreadMessages = 0;
            });
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
      if (updatedRoom) {
        this.updateTotalUnread(updatedRoom.unreadMessages, true);
        this.$set(updatedRoom, 'unreadMessages', 0);
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
      const room = event?.detail?.room || event;
      const fromRoomList = event?.detail?.fromRoomList || false;
      this.addRoomIfNotExists(room);
      this.openDrawer();
      setTimeout(() => {
        this.$root.$emit('open-chat-discussion', room, fromRoomList);
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
        if (document.hidden && this.isPresencePollingOwner) {
          this.releasePresencePollingOwner();
        }
        if (!document.hidden) {
          this.$matrixService.startMatrixSyncLoop(matrixFilterId).catch(console.error);
          this.tryBecomePresencePollingOwner();
        }
      });
    },
    getLocalRoomById(roomId) {
      const updatedRoomIndex = this.rooms?.findIndex?.(room => room.id === roomId);
      return this.rooms?.[updatedRoomIndex];
    },
    getLocalRoomBySpaceId(spaceId) {
      const updatedRoomIndex = this.rooms?.findIndex?.(room => room.spaceId === spaceId);
      return this.rooms?.[updatedRoomIndex];
    },
    handleRoomMuteUpdate(room) {
      const updatedRoom = this.getLocalRoomById(room?.roomId);
      const wasMuted = updatedRoom.muted;
      updatedRoom.muted = room.muted;

      if (wasMuted !== room.muted) {
        const delta = updatedRoom.unreadMessages || 0;
        this.updateTotalUnread(delta, room.muted, true);
      }
    },
    handleSpaceMute({detail: {spaceId}}) {
      const updatedRoom = this.getLocalRoomBySpaceId(spaceId);
      if (updatedRoom) {
        updatedRoom.muted = true;
        this.updateTotalUnread(updatedRoom.unreadMessages, false, true);
      }
    },
    handleSpaceUnmute({detail: {spaceId}}) {
      const updatedRoom = this.getLocalRoomBySpaceId(spaceId);
      if (updatedRoom) {
        updatedRoom.muted = false;
        this.updateTotalUnread(updatedRoom.unreadMessages, true, true);
      }
    },
    setupBroadcastChannelListener() {
      this.$root.channel.addEventListener('message', event => {
        if (!this.rooms) {
          return;
        }
        const {type, payload} = event.data;
        if (type === 'total-unread-messages-updated' && this.totalUnreadMessages !== payload.totalUnreadMessages) {
          this.totalUnreadMessages = payload.totalUnreadMessages;
        }
        if (type === 'user-status-updated') {
          this.handleUserStatusUpdated(payload);
        }
      });
    },
    handleLocaleStorageUpdate(e) {
      if (e.key === this.presencePollingKey && !e.newValue) {
        this.tryBecomePresencePollingOwner();
      }
    },
    releasePresencePollingOwner() {
      if (this.isPresencePollingOwner) {
        this.isPresencePollingOwner = false;
        clearInterval(this.presencePollingInterval);
        this.presencePollingInterval = null;
        localStorage.removeItem(this.presencePollingKey);
      }
    },
    handleWSMessageReceived({detail: {wsEventName, message}}) {
      if (navigator.serviceWorker) {
        navigator.serviceWorker.ready.then((registration) => {
          const messageObject = {
            type: 'CHAT_NOTIFICATION',
            eventId: message.eventId,
            roomId: message.roomId,
          };
          registration.active.postMessage(messageObject);
        });
      }
    },
  }
};
</script>
