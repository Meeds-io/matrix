<template>    
  <v-tooltip
    v-if="displayed"
    bottom>
    <template #activator="{ on, attrs }">
      <div
        v-bind="attrs"
        v-on="on">
        <v-btn
          :ripple="false"
          :id="`chat${identityType}${identityId}`"
          :key="`chat${identityType}${identityId}`"
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
    displayed: false,
  }),
  created() {
    return this.$matrixService.getSpaceRoom(this.identityId).then(room => {
      this.displayed = room.status === 'ENABLED';
    });
  },
  methods: {
    openChatDrawer(event) {
      event.preventDefault();
      event.stopPropagation();
      if(this.identityType === 'USER_TIPTIP') {
        this.$userService.getUser(this.identityId, 'settings').then(data => {
          const matrixIdProperty = data.properties.filter(p => p.propertyName == 'matrixId').shift();
          if(matrixIdProperty) {
            const contactMatrixId = matrixIdProperty.value;
            if(contactMatrixId) {
              this.$matrixService.openDMRoom(eXo.env.portal.userName, data.userName, matrixServerName, matrixUserId, contactMatrixId);
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
