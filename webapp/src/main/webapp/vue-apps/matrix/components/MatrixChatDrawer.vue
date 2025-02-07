<template>
  <exo-drawer
    ref="meedsChatDrawer"
    :loading="loading > 0"
    class="meeds-chat-drawer"
    right
    @closed="drawer = false">
    <template slot="title">
      <div class="d-flex">
        <div
          :style="`backgroundImage: url(${avatarUrl})`"
          class="chat-top-drawer-avatar d-flex rounded-circle">
          <div class="matrix-user-status icon-small-size size-eight" :class="presenceClass"></div>
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
    <template #content>
      <div
        :class="expanded && 'pa-4'"
        class="d-flex fill-height">
        <div
          class="singlePageApplication pa-0 d-flex fill-height">
          <matrix-chat-rooms :rooms="rooms"/>
        </div>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    rooms: {
      type: Array,
      default: function() { return [];}
    }
  },
  data: () =>({
    loading: 0,
    presence: 'online'
  }),
  computed: {
    avatarUrl() {
      return this.$currentUserIdentity.profile.avatar;
    },
    presenceClass() {
      return `matrix-status-${this.presence}`;
    }
  },
  watch: {
    loading() {
      if (this.loading === 0) {
        this.$nextTick().then(() => {
          this.$root.initialized = true;
          this.$root.$emit('chat-drawer-initialized');
        });
      }
    },
    expanded() {
      console.log(`drawer is expanded ${expanded}`);
    },
  },
  created() {
    this.$root.$on('chat-loading-start', this.incrementLoading);
    this.$root.$on('chat-loading-end', this.decrementLoading);
    document.addEventListener('matrix-user-status-updated', event => this.userStatusUpdated(event));
  },
  beforeDestroy() {
    this.$root.$off('chat-loading-start', this.incrementLoading);
    this.$root.$off('chat-loading-end', this.decrementLoading);
    document.removeEventListener('matrix-user-status-updated', event => this.userStatusUpdated(event));
  },
  methods: {
    open() {
      this.$refs.meedsChatDrawer.open();
    },
    close() {
      this.$refs.meedsChatDrawer.close();
    },
    incrementLoading() {
      this.loading++;
    },
    decrementLoading() {
      this.loading--;
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
