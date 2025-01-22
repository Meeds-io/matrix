<!--
Copyright (C) 2024 eXo Platform SAS.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.
-->
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
      const unreadMessagesCount = this.notification?.parameters?.UNREAD_MESSAGES_COUNT;
      const roomId = this.notification?.parameters?.ROOM_ID;
      const roomName = this.identityOfRoom && (this.identityOfRoom.providerId === 'space' ? this.identityOfRoom.space.displayName : this.identityOfRoom.profile.fullname) || roomId;

      return this.$t('matrix.message.received.pwa.notification', {
        0: `<b>${unreadMessagesCount}</b>`,
        1: `<a class="space-name font-weight-bold">${roomName}</a>`
      });
    }
  }
};
</script>
