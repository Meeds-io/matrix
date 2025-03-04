<template>    
  <v-tooltip bottom>
    <template #activator="{ on, attrs }">
      <div
        v-bind="attrs"
        v-on="on">
        <v-btn
          :ripple="false"
          icon
          @click="openChatDrawer($event)">
          <v-icon size="18">fas fa-comments</v-icon>
        </v-btn>
      </div>
    </template>
    <span>
      {{ $t('meeds.chat.send.message') }}
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
      const currentUserMatrixId = localStorage.getItem("matrix_user_id");
      if(this.identityType === 'USER_TIPTIP') {
        this.$userService.getUser(this.identityId, 'settings').then(data => {
          const matrixIdProperty = data.properties.filter(p => p.propertyName == 'matrixId').shift();
          if(matrixIdProperty) {
            this.contactMatrixId = matrixIdProperty.value;
            if(this.contactMatrixId) {
              this.$matrixService.openDMRoom(eXo.env.portal.userName, data.userName, matrixServerName);
            }
          }
        });
      } else if (this.identityType === 'space'){
        this.$matrixService.openSpaceRoom(this.identityId);
      }
    },
  }
};
</script>
