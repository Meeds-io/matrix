<template>
  <exo-drawer
    ref="meedsChatDrawer"
    id="meedsChatDrawer"
    :loading="loading"
    class="meeds-chat-drawer"
    right
    @closed="close">
    <template slot="title">
      <div class="d-flex">
        <div
          :style="`backgroundImage: url(${avatarUrl})`"
          class="chat-top-drawer-avatar d-flex rounded-circle">
          <div class="matrix-user-status icon-small-size size-2" :class="presenceClass"></div>
        </div>
        <span class="mx-5 content-align"> {{ $t('matrix.chat.discussions') }} </span>
      </div>
    </template>
    <template slot="titleIcons">
      <v-icon
        v-exo-tooltip.bottom="$t('matrix.chat.quick.create.discussion')"
        class="my-auto"
        @click="openQuickCreateChatDiscussionDrawer">
        mdi-plus
      </v-icon>
    </template>
    <template slot="content">
      <div
        :class="{'disabled-background': !rooms?.length}"
        class="fill-height overflow-y-auto specific-scrollbar">
        <matrix-chat-rooms
          :rooms="sortedRooms"
          :loading="loading" />
        <meeds-chat-discussion-drawer ref="ChatDiscussionDrawer" />
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    rooms: {
      type: Array,
      default: null
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  data: () =>({
    presence: 'online',
  }),
  computed: {
    avatarUrl() {
      return this.$currentUserIdentity.profile.avatar;
    },
    presenceClass() {
      return `matrix-status-${this.presence}`;
    },
    sortedRooms() {
      if (!Array.isArray(this.rooms)) {
        return [];
      }
      return [...this.rooms].sort(
          (a, b) =>
              (b.updated || 0) - (a.updated || 0) ||
              a.name?.localeCompare?.(b.name, undefined, {numeric: true}) || 0
      );
    }
  },
  watch: {
    loading() {
      this.checkLoading();
    }
  },
  created() {
    document.addEventListener('matrix-user-status-updated', event => this.userStatusUpdated(event));
  },
  mounted() {
    this.checkLoading();
  },
  beforeDestroy() {
    document.removeEventListener('matrix-user-status-updated', event => this.userStatusUpdated(event));
  },
  methods: {
    checkLoading() {
      if (this.loading) {
        this.$refs.meedsChatDrawer.startLoading();
      } else {
        this.$refs.meedsChatDrawer.endLoading()
      }
    },
    open() {
      if (!this.$refs.meedsChatDrawer.drawer) {
        this.$refs.meedsChatDrawer.open();
      }
    },
    close() {
      this.$refs.ChatDiscussionDrawer.close();
      this.$refs.meedsChatDrawer.close();
    },
    userStatusUpdated(event) {
      if(localStorage.getItem('matrix_user_id') === event.detail.userId) {
        this.presence = event.detail.presence;
      }
    },
    openQuickCreateChatDiscussionDrawer() {
      this.$root.$emit(this.$chatConstants.ACTION_CHAT_OPEN_QUICK_CREATE_DISCUSSION_DRAWER);
    },
  },
};
</script>
