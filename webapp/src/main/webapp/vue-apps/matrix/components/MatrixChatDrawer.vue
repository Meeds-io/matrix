<template>
  <exo-drawer
    ref="meedsChatDrawer"
    id="meedsChatDrawer"
    :loading="loading"
    class="meeds-chat-drawer"
    :filter-placeholder="$t('matrix.rooms.filter.placeholder')"
    use-filter
    right
    @filter-updated="handleFilterUpdate"
    @closed="close">
    <template slot="title">
      <div
        class="d-flex">
        <v-badge
          :color="presenceColor"
          :value="true"
          class="my-auto mx-0 pa-0"
          content=""
          offset-x="10"
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
    <template
      slot="titleIcons">
      <v-btn
        :title="$t('matrix.chat.quick.create.discussion')"
        icon
        @click="openQuickCreateChatDiscussionDrawer">
        <v-icon
          class="icon-default-color"
          size="20">
          fa-plus
        </v-icon>
      </v-btn>
    </template>
    <template slot="content">
      <div
        :class="{'disabled-background': !rooms?.length}"
        class="fill-height overflow-y-auto specific-scrollbar">
        <matrix-chat-rooms
          :rooms="sortedRooms"
          :loading="loading" />
        <matrix-chat-discussion-drawer ref="ChatDiscussionDrawer" />
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data() {
    return {
      searchTimer: null,
      searchTerm: null
    }
  },
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
    filteredRooms() {
      if (!this.searchTerm) {
        return this.rooms;
      }
      const normalize = str =>
          str?.normalize('NFD').replace(/\p{Diacritic}/gu, '').toLowerCase() || '';

      const normalizedSearch = normalize(this.searchTerm);

      return this.rooms.filter(room =>
          normalize(room.name).includes(normalizedSearch)
      );
    },
    sortedRooms() {
      if (!Array.isArray(this.filteredRooms)) {
        return [];
      }
      return [...this.filteredRooms].sort(
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
    handleFilterUpdate(text) {
      this.loading = true;
      if (this.searchTimer) {
        clearTimeout(this.searchTimer);
      }
      this.searchTimer = setTimeout(() => {
        this.searchTerm = text;
        this.loading = false;
      }, 300);
    },
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
