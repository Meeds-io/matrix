<template>
  <user-notification-template
    :notification="notification"
    :avatar-url="avatarUrl"
    :message="message"
    :url="url"
    user-avatar />
</template>
<script>
export default {
  props: {
    notification: {
      type: Object,
      default: null,
    },
  },
  data: () => ({
    identityOfRoom: null,
  }),
  created() {
    this.$matrixService.getByRoomId(this.notification?.parameters?.ROOM_ID).then(identityId => {
      this.$identityService.getIdentityById(identityId).then(identity => {
        this.identityOfRoom = identity;
      })
    });
  },
  computed: {
    url() {
      return `/portal/dw/?chatRoomId=${this.notification?.parameters?.ROOM_ID}`;
    },
    avatarUrl() {
      return this.identityOfRoom && (this.identityOfRoom.providerId === 'space' ? this.identityOfRoom.space.avatarUrl : this.identityOfRoom.profile.avatar) || '';
    },
    message() {
      const roomId = this.notification?.parameters?.ROOM_ID;
      let roomName = roomId;
      if(this.identityOfRoom) {
        if(this.identityOfRoom.providerId === 'space') {
          roomName = this.identityOfRoom.space.displayName;
          return this.$t('matrix.space.message.received.pwa.notification', {
            0: `<a class="space-name font-weight-bold">${roomName}</a>`
          });
        } else {
          roomName = this.identityOfRoom.profile.fullname;
          return this.$t('matrix.user.message.received.pwa.notification', {
            0: `<a class="space-name font-weight-bold">${roomName}</a>`
          });
        }
      }
    }
  }
};
</script>
