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
  computed: {
    url() {
      //we keep both possibility so that old notifications, generated before the current modification are still ok
      return this.notification?.parameters?.chatUrl || `/portal/dw/?chatRoomId=${this.notification?.parameters?.roomId}`;
    },
    avatarUrl() {
      //we keep both possibility so that old notifications, generated before the current modification are still ok
      return this.notification?.parameters?.avatar || `/portal/rest/v1/social/users/${this.notification?.parameters?.sender}/avatar`;
    },
    message() {
      const creator = this.notification?.parameters?.senderFullName;
      const room = this.notification?.parameters?.roomName;

      return this.$t('matrix.message.mention.pwa.notification', {
        0: `<a class="space-name font-weight-bold">${room}</a>`
      });
    }
  }
};
</script>
