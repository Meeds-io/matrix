<template>
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
              :content="totalUnreadMessages"
              max="5"
              flat
              color="var(--allPagesBadgePrimaryColor, #d32a2a)"
              overlap>
              <v-icon size="22" class="icon-default-color">fa-comments</v-icon>
            </v-badge>
        </v-btn>
      </div>
      <matrix-chat-drawer
        v-if="open"
        ref="drawer"
        :rooms="rooms"
        @closed="open = false" />
    </div>
  </div>
</template>
<script>
  export default {
    props: {
    },
    data: () => ({
      color: 'green',
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
                this.$matrixService.saveFilter().then(filterResponse => this.$matrixService.longPollingSync(filterResponse.filter_id));
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
        this.$matrixService.saveFilter().then(filterResponse => this.$matrixService.longPollingSync(filterResponse.filter_id));
      }
      this.$root.$on('chat-event-total-unread-updated',e => {
        this.totalUnreadMessages = e;
      });
      document.addEventListener('matrix-message-received', event => this.messageReceived(event));
    },
    beforeDestroy() {
      this.$root.$off('chat-event-total-unread-updated',e => this.totalUnreadMessages = e);
      document.removeEventListener('matrix-message-received', event => this.messageReceived(event));
    },
    watch: {
      open() {
        if (this.open) {
          this.$nextTick().then(() => this.$refs.drawer.open());
        }
      },
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
        const updatedRoom = this.rooms.find(room => room.id === event.detail.roomId);
        updatedRoom.unreadMessages += 1;
        updatedRoom.lastMessage = event.detail.message;
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
