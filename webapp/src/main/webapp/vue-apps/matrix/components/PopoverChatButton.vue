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
  async created() {
    if (this.identityType === 'space') {
      const space = await this.$spaceService.getSpaceById(this.identityId, 'extendedPermissions');
      if (!space?.extendedPermissions || (space?.extendedPermissions['meeds.chat.authorized']
                                       && space?.extendedPermissions['meeds.chat.authorized'] === 'true')) {
        const room = await this.$matrixService.getSpaceRoom(this.identityId);
        this.displayed = room.status === 'ENABLED';
      }
    } else {
      this.displayed = true;
    }
  },
  methods: {
    openChatDrawer(event) {
      event.preventDefault();
      event.stopPropagation();
      if(this.identityType === 'USER_TIPTIP') {
        this.$userService.getUser(this.identityId).then(data => {
          const userName = data.userName || data.username;
          this.$matrixService.getMatrixIdOfUser(userName).then(contactMatrixId => {
            if(contactMatrixId) {
              this.$matrixService.openDMRoom(eXo.env.portal.userName, data.userName, matrixServerName, matrixUserId, contactMatrixId);
            }
          });
        });
      } else if (this.identityType === 'space'){
        this.$matrixService.openSpaceRoom(this.identityId);
      }
    },
  }
};
</script>
