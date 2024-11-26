<template>    
  <v-tooltip bottom>
    <template #activator="{ on, attrs }">
      <div
        v-bind="attrs"
        v-on="on">
        <v-btn
          :ripple="false"
          icon
          color="primary"
          @click="openChatDrawer($event)">
          <v-icon size="18">fas fa-comments</v-icon>
        </v-btn>
      </div>
    </template>
    <span>
      {{ $t('exoplatform.chat.send.message') }}
    </span>
  </v-tooltip>
</template>
<script>
export default {
  props: {
    identityType: {
      type: String,
      default: '',
    },
    identityId: {
      type: String,
      default: ''
    }
  },
  data: () => ({
    contactMatrixId: String,
    matrixDMRoom: String
  }),
  created() {

  },
  methods: {
    openChatDrawer(event) {
      event.preventDefault();
      event.stopPropagation();
      const matrixRoom = '';
      console.log('open chat drawer from popover');
      const currentUserMatrixId = localStorage.getItem("matrix_user_id");
      this.$userService.getUser(this.identityId, 'settings').then(data => {
        const matrixIdProperty = data.properties.filter(p => p.propertyName == 'matrixId').shift();

        if(matrixIdProperty) {
          this.contactMatrixId = matrixIdProperty.value;
          if(this.contactMatrixId) {
            this.$matrixService.openDMRoom(eXo.env.portal.userName, this.identityId, matrixServerName);
          }
        }
      });
      if(matrixRoom) {
        document.dispatchEvent(new CustomEvent(chatConstants.ACTION_OPEN_CHAT_ROOM, this.matrixDMRoom));
      }
    },
  }
};
</script>
