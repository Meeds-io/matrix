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
  }),
  created() {
    const currentUserMatrixId = localStorage.getItem("matrix_user_id");
    this.$identityService.getIdentityById(identityId).then(data => this.contactMatrixId = data?.profile.matrixId);
    if(this.contactMatrixId) {
      this.$matrixService.getDMRoom(currentUserMatrixId, this.contactMatrixId).then();
    }
  },
  methods: {
    openChatDrawer(event) {
      event.preventDefault();
      event.stopPropagation();
//      const chatType =  this.identityType === 'space' ? 'space-id' : 'username';
//      const chatRoomName = this.identityId;
//
//      document.dispatchEvent(
//        new CustomEvent(chatConstants.ACTION_ROOM_OPEN_CHAT, { detail: {
//          name: chatRoomName,
//          type: chatType,
//        }}));
    },
  }
};
</script>
