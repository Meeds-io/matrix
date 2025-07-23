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
        <v-badge
          :color="presenceColor"
          :value="true"
          class="my-auto mx-0 pa-0"
          content=""
          offset-x="13"
          offset-y="10"
          width="12"
          height="12"
          bordered
          bottom
          overlap
          dot>
          <v-avatar
            width="36"
            min-width="36"
            height="36">
            <v-img
              :src="avatarUrl"
              :lazy-src="avatarUrl"
              :alt="fullName" />
          </v-avatar>
        </v-badge>
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
    },
    presence: {
      type: String,
      default: 'available'
    }
  },
  computed: {
    avatarUrl() {
      return this.$currentUserIdentity.profile.avatar;
    },
    fullName() {
      return this.$currentUserIdentity?.profile?.fullname;
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
    },
    presenceColor() {
      return this.presence && this.$root.statusMap[this.presence];
    }
  },
  watch: {
    loading() {
      this.checkLoading();
    }
  },
  mounted() {
    this.checkLoading();
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
    openQuickCreateChatDiscussionDrawer() {
      this.$root.$emit(this.$chatConstants.ACTION_CHAT_OPEN_QUICK_CREATE_DISCUSSION_DRAWER);
    },
  },
};
</script>
