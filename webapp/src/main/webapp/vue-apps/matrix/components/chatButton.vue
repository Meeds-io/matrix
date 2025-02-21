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
                this.$root.$emit('alert-message', `${this.$t('exo.matrix.login.failed')}`, 'error');
                this.$root.$emit('matrix-login-failed');
              }
            });
          } else {
            this.$root.$emit('alert-message', `${this.$t('exo.matrix.jwt.disabled')}`, 'error');
          }
        });
      } else {
        this.loadRooms();
        this.$matrixService.saveFilter().then(filterResponse => {
          this.$matrixService.longPollingSync(filterResponse.filter_id).then(() => this.presence = localStorage.getItem('matrix_user_presence'))
        });
        this.$matrixService.installPusher();
      }
      this.$root.$on('chat-event-total-unread-updated',e => this.totalUnreadMessages = e);
      document.addEventListener('chat-load-chat-rooms',e => this.loadRooms());
      document.addEventListener('matrix-message-received', event => this.messageReceived(event));
      document.addEventListener('matrix-user-status-updated', event => this.userStatusUpdated(event));
    },
    beforeDestroy() {
      this.$root.$off('chat-event-total-unread-updated',e => this.totalUnreadMessages = e);
      document.removeEventListener('chat-load-chat-rooms',e => this.loadRooms());
      document.removeEventListener('matrix-message-received', event => this.messageReceived(event));
      document.removeEventListener('matrix-user-status-updated', event => this.userStatusUpdated(event));
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
      openMatrixRoom(event){
        if (event){
          event.preventDefault();
          event.stopPropagation();
        }
        const url = `https://matrix.to/#/${this.roomId}:${this.serverName}?via=${this.serverName}`;
        window.open(url, '_blank');
      },
      openDrawer() {
        this.$root.initialized = false;
        this.open = true;
      },
      messageReceived(event) {
        this.totalUnreadMessages ++;
        const updatedRoomIndex = this.rooms.findIndex(room => room.id === event.detail.roomId);
        const updatedRoom = this.rooms[updatedRoomIndex];
        if(updatedRoom) {
          updatedRoom.lastMessage = updatedRoom.lastMessage ? updatedRoom.lastMessage : {};
          if(event.detail.sender === localStorage.getItem('matrix_user_id')) {
            updatedRoom.unreadMessages += 1;
            updatedRoom.lastMessage.content = `${this.$t('matrix.words.you')} ${event.detail.message}`;
            this.rooms.splice(updatedRoomIndex, 1);
            this.rooms.unshift(updatedRoom);
          } else {
            const senderMatrixId = event.detail.sender.substr(1, event.detail.sender.indexOf(":") - 1);
            this.$matrixService.getUserByMatrixId(senderMatrixId).then(senderIdentity => {
              updatedRoom.unreadMessages += 1;
              updatedRoom.lastMessage.content = `${senderIdentity.profile.fullname}: ${event.detail.message}`;
              this.rooms.splice(updatedRoomIndex, 1);
              this.rooms.unshift(updatedRoom);
            });
          }
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
    }
  };
</script>
