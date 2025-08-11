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

      if (this.notification?.parameters?.MATRIX_ROOM_TYPE === 'SPACE') {
        return this.$t('matrix.message.mention.space.notification', {
          0: `<a class="space-name font-weight-bold">${creator}</a>`,
          1: `<a class="space-name font-weight-bold">${room}</a>`
        });
      } else {
        return this.$t('matrix.message.mention.onetoone.notification', {
          0: `<a class="space-name font-weight-bold">${room}</a>`
        });
      }
    }
  }
};
</script>
