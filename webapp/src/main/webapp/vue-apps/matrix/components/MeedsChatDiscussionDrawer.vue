<template>
  <exo-drawer
    id="ChatDiscussionDrawer"
    ref="ChatDiscussionDrawer"
    :loading="loading || loadingRooms"
    :class="customHeaderClass"
    :go-back-button="!fullPageMode"
    v-draggable="!fullPageMode"
    allow-expand
    right
    @expand-updated="handleExpanded"
    @closed="close">
    <template slot="title">
      <matrix-filter-room-list-input
        v-if="showFilter && fullPageMode"
        ref="filter"
        :show-filter.sync="showFilter"
        :filter-text.sync="filterText"
        @update:filterText="handleFilterUpdate" />
      <div
        v-else-if="fullPageMode"
        class="d-flex">
        <chat-header-user-avatar :presence="presence" />
        <div class="ms-auto me-5">
          <v-btn
            v-if="fullPageMode"
            :title="$t('matrix.chat.quick.create.discussion')"
            icon
            @click="openQuickCreateChatDiscussionDrawer">
            <v-icon
              class="icon-default-color"
              size="20">
              fa-plus
            </v-icon>
          </v-btn>
          <v-btn
            v-if="fullPageMode"
            icon
            @click="openFilter">
            <v-icon
              :class="{
                'primary--text': !!filterText,
                'icon-default-color': !filterText
              }"
              size="20">
              fa-filter
            </v-icon>
          </v-btn>
        </div>
      </div>
      <room-avatar
        v-else-if="room"
        :room="room" />
    </template>
    <template slot="titleIcons">
      <div
        v-if="room && fullPageMode"
        class="text-truncate">
        <room-avatar :room="room" />
      </div>
      <room-header-actions
        ref="roomHeaderActions"
        v-if="room"
        :room="room"
        class="ms-auto" />
    </template>
    <template slot="content">
      <matrix-chat-body
        ref="chatBody"
        :key="componentKey"
        :loading="loading"
        :rooms="sortedRooms"
        :selected-room="room"
        @loading="loading = $event" />
    </template>
  </exo-drawer>
</template>
<script>

export default {
  data() {
    return {
      room: {},
      loading: false,
      expanded: false,
      isInputFocused: false,
      drawerWidth: 420,
      componentKey: 0,
      previousRoomId: null,
      filterText: '',
      showFilter: false,
      open: false
    };
  },
  props: {
    rooms: {
      type: Array,
      default: null
    },
    loadingRooms: {
      type: Boolean,
      default: false
    },
    presence: {
      type: String,
      default: 'available'
    }
  },
  created() {
    this.$root.$on('open-chat-discussion', this.openDiscussion);
    window.addEventListener('resize', this.handleResize);

  },
  beforeDestroy() {
    this.$root.$off('open-chat-discussion', this.openDiscussion);
    window.removeEventListener('resize', this.handleResize);
  },
  computed: {
    spaceId() {
      return this.room?.spaceId;
    },
    defaultRoomListContainerWidth() {
      return this.$root.defaultRoomListContainerWidth;
    },
    fullPageMode() {
      return this.$root.fullPageMode;
    },
    sortedRooms() {
      return this.rooms;
    },
    customHeaderClass() {
      return this.fullPageMode ? this.room ? 'fullPageHeader' : 'fullPageHeader_no_room' : '';
    },
  },
  methods: {
    handleFilterUpdate(text) {
      this.filterText = text;
      this.$emit('filter-updated', text);
    },
    openFilter() {
      this.showFilter = !this.showFilter;
      this.$nextTick(() => {
        this.$refs.filter?.openFilter?.();
      });
    },
    handleResize() {
      if (!this.open) {
        return;
      }
      this.computeMessagesContainerWidth();
    },
    computeMessagesContainerWidth() {
      this.$root.fullPageMessagesContainerWidth = this.$refs?.ChatDiscussionDrawer?.$el?.clientWidth
          - this.defaultRoomListContainerWidth;
    },
    getLastOpenedRoom() {
      const lastOpenedRoomId = localStorage.getItem('lastOpenedRoomId');
      if (lastOpenedRoomId) {
        return this.rooms.find(room => room.id === lastOpenedRoomId);
      }
      return null;
    },
    handleExpanded(expanded) {
      setTimeout(async () => {
        this.$root.fullPageMode = expanded;
        this.computeMessagesContainerWidth();
        this.selectedRoom = this.getLastOpenedRoom();
        await this.openDiscussion(this.selectedRoom || this.sortedRooms?.[0]);
      }, 300);
    },
    async openDiscussion(room, fromRoomList) {
      if (this.fullPageMode && fromRoomList) {
        return;
      }
      this.componentKey++;
      this.room = room;
      if (!this.$refs.ChatDiscussionDrawer?.drawer) {
        this.$refs.ChatDiscussionDrawer?.open();
        this.open = true;
      }
      await this.$nextTick();
      const reload = this.previousRoomId && this.selectedRoom?.id !== this.previousRoomId;
      if (reload) {
        await this.$refs?.chatBody?.openDiscussion?.();
      }
      this.previousRoomId = this.selectedRoom?.id;
      this.$root.$emit('room-discussion-opened', this.selectedRoom?.id);
    },
    close() {
      this.open = false;
      this.$refs.chatBody?.reset();
      this.$refs.ChatDiscussionDrawer?.close();
    },
    openQuickCreateChatDiscussionDrawer() {
      this.$root.$emit(this.$chatConstants.ACTION_CHAT_OPEN_QUICK_CREATE_DISCUSSION_DRAWER);
    }
  }
};
</script>
