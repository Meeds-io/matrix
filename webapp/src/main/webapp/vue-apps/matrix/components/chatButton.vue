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
    <div class="VuetifyApp">
      <div class="v-application v-application--is-ltr theme--light">
        <div class="v-application--wrap">
          <v-btn
              id="btnChatButtonNew"
              :title="$t('matrix.chat.button.tooltip')"
              class="text-xs-center"
              @click="openDrawer"
              :color="color"
              icon>
              <v-badge
                :value="totalUnreadMessages > 0"
                :content="totalUnreadMessages <= 99 ? totalUnreadMessages : '99+'"
                flat
                color="var(--allPagesBadgePrimaryColor, #d32a2a)"
                overlap>
                <v-icon size="20" :class="presenceClass">fa-comments</v-icon>
              </v-badge>
          </v-btn>
        </div>
        <matrix-chat-drawer
          v-if="open"
          ref="meedsChatDrawer"
          :rooms="rooms"
          @closed="open = false" />
        <meeds-chat-quick-create-discussion-drawer />
      </div>
    </div>
  </v-app>
</template>
<script>
  export default {
    props: {
    },
    data: () => ({
      presence: 'online',
      open: false,
      totalUnreadMessages: 0,
      rooms: []
    }),
    created() {
      const lastLoginOnMatrix = localStorage.getItem('matrix_last_login');
      const dayInMs = 24*60*60*1000;
      if(!lastLoginOnMatrix || (lastLoginOnMatrix && new Date().getTime() - lastLoginOnMatrix > dayInMs)) {
        localStorage.removeItem("matrix_user_id");
        localStorage.removeItem("matrix_access_token");
        localStorage.removeItem('matrix_last_login');
      }

      const matrixInfos = localStorage.getItem('matrix_user_id');
      if(!matrixInfos || matrixInfos !== matrixUserId) {
        this.$matrixService.checkAuthenticationTypes().then(enabled => {
          if(enabled) {
            this.$matrixService.authenticate().then(resp => {
              if(resp.user_id) {
                localStorage.setItem("matrix_user_id", resp.user_id);
                localStorage.setItem("matrix_access_token", resp.access_token);
                localStorage.setItem("matrix_last_login", new Date().getTime());
                this.loadRooms();
                this.$matrixService.saveFilter().then(filterResponse => {
                  this.$matrixService.longPollingSync(filterResponse.filter_id).then(() => this.presence = localStorage.getItem('matrix_user_presence'))
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
          this.$matrixService.longPollingSync(filterResponse.filter_id).then(() => this.presence = localStorage.getItem('matrix_user_presence'))
        });
        this.$matrixService.installPusher();
      }

      const urlParams = new URLSearchParams(window.location.search);
      if (urlParams.has('roomId')){
        this.openRoom(urlParams.get('roomId'));
      }

      this.$root.$on('chat-event-total-unread-updated',e => this.totalUnreadMessages = e);
      document.addEventListener('chat-load-chat-rooms',e => this.loadRooms());
      document.addEventListener('matrix-message-received', event => this.messageReceived(event));
      document.addEventListener('matrix-user-status-updated', event => this.userStatusUpdated(event));
      document.addEventListener(this.$chatConstants.ACTION_OPEN_CHAT_ROOM, event => this.openRoom(event.detail));
      document.addEventListener('matrix-room-mark-full-read', event => this.updateUnreadMessages(event));
    },
    beforeDestroy() {
      this.$root.$off('chat-event-total-unread-updated',e => this.totalUnreadMessages = e);
      document.removeEventListener('chat-load-chat-rooms',e => this.loadRooms());
      document.removeEventListener('matrix-message-received', event => this.messageReceived(event));
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
      openDrawer() {
        this.$root.initialized = false;
        this.open = true;
      },
      messageReceived(event) {
        const updatedRoomIndex = this.rooms.findIndex(room => room.id === event.detail.roomId);
        const updatedRoom = this.rooms[updatedRoomIndex];
        if(updatedRoom) {
          updatedRoom.lastMessage = updatedRoom.lastMessage ? updatedRoom.lastMessage : {};
          if(matrixUserId !== event.detail.sender) {
            this.totalUnreadMessages ++;
            updatedRoom.unreadMessages += 1;
          }
          this.rooms.splice(updatedRoomIndex, 1);
          this.rooms.unshift(updatedRoom);
          updatedRoom.updated = event.detail.origin_server_ts;
          if(event.detail.sender === localStorage.getItem('matrix_user_id')) {
            updatedRoom.lastMessage.content = this.$t('matrix.chat.lastMessage.pattern').replace('{0}',
                                              this.$t('matrix.words.you')).replace('{1}', event.detail.messageText);
          } else {
            const senderMatrixId = event.detail.sender.substr(1, event.detail.sender.indexOf(":") - 1);
            this.$matrixService.getUserByMatrixId(senderMatrixId).then(senderIdentity => {
              updatedRoom.lastMessage.content = this.$t('matrix.chat.lastMessage.pattern').replace('{0}',
                                                senderIdentity.profile.fullname).replace('{1}', event.detail.messageText);
            });
          }
        }
      },
      updateUnreadMessages(event) {
        const updatedRoomIndex = this.rooms.findIndex(room => room.id === event.detail.roomId);
        const updatedRoom = this.rooms[updatedRoomIndex];
        if(updatedRoom) {
          this.totalUnreadMessages -= updatedRoom.unreadMessages;
          updatedRoom.unreadMessages = 0;
        }
      },
      userStatusUpdated(event) {
        if(event.detail.userId === localStorage.getItem('matrix_user_id')) {
          this.presence = event.detail.presence;
        } else {
          const updatedUserStatusIndex = this.rooms.findIndex(room => room.dmMemberId === event.detail.userId);
          if(updatedUserStatusIndex >= 0) {
            this.rooms[updatedUserStatusIndex].presence = event.detail.presence;
          }
        }
      },
      loadRooms() {
        this.$matrixService.loadChatRooms(localStorage.getItem('matrix_user_id')).then(matrixRoomsObject => {
          this.rooms = matrixRoomsObject.rooms || [];
          this.$root.$emit('chat-event-total-unread-updated', matrixRoomsObject.totalUnreadMessages)
        });
      },
      openRoom(roomId) {
        this.openDrawer();
        setTimeout( () => {
          this.$root.$emit("open-chat-discussion", roomId);
        }, 100);
      }
    }
  };
</script>
