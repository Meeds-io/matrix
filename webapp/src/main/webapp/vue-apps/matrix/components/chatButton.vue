<template>
  <div class="VuetifyApp">
    <div class="v-application v-application--is-ltr theme--light">
      <div class="v-application--wrap">
        <v-btn
            id="btnChatButtonNew"
            :title="$t('matrix.chat.button.tooltip')"
            @click="openDrawer"
            :color="color"
            icon>
          <v-icon size="22" class="my-auto icon-default-color fas fa-comments" />
        </v-btn>
      </div>
      <matrix-chat-drawer
        v-if="open"
        ref="drawer"
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
      open: false
    }),
    created() {
      const matrixInfos = localStorage.getItem('matrix_user_id');

      if(!matrixInfos || matrixInfos !== matrixUserId) {
        this.$matrixService.checkAuthenticationTypes().then(enabled => {
          if(enabled) {
            this.$matrixService.authenticate().then(resp => {
              if(resp.user_id) {
                localStorage.setItem("matrix_user_id", resp.user_id);
                localStorage.setItem("matrix_access_token", resp.access_token);
              } else {
                this.$root.$emit('alert-message', `${this.$t('exo.matrix.login.failed')}`, 'error');
                this.$root.$emit('matrix-login-failed');
              }
            });
          } else {
            this.$root.$emit('alert-message', `${this.$t('exo.matrix.jwt.disabled')}`, 'error');
          }
        });
      }
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
      }
    }
  };
</script>
