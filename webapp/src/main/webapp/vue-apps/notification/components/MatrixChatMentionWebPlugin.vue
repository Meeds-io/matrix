<template>
  <user-notification-template
    :notification="notification"
    :avatar-url="avatarUrl"
    :message="message"
    :url="url"
    space-avatar />
</template>
<script>
export default {
  props: {
    notification: {
      type: Object,
      default: null,
    },
  },
  computed: {
    url() {
      return `${location.pathname}?roomId=${this.notification?.parameters?.ROOM_ID}`;
    },
    avatarUrl() {
      return this.notification?.parameters?.MATRIX_ROOM_AVATAR;
    },
    message() {
      const creator = this.notification?.parameters?.MATRIX_SENDER_FULL_NAME;
      const room = this.notification?.parameters?.MATRIX_ROOM_NAME;

      return this.$t('matrix.message.mention.pwa.notification', {
        0: creator,
        1: `<a class="space-name font-weight-bold">${room}</a>`
      });
    }
  }
};
</script>
