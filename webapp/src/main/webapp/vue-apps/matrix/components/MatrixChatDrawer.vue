<template>
  <exo-drawer
    ref="meedsChatDrawer"
    id="meedsChatDrawer"
    :loading="loading"
    :filter-placeholder="$t('matrix.rooms.filter.placeholder')"
    class="meeds-chat-drawer"
    :use-filter="!fullPageMode"
    :class="customHeaderClass"
    allow-expand
    right
    @filter-updated="handleFilterUpdate"
    @expand-updated="handleExpanded"
    @closed="$emit('closed')">
    <template slot="title">
      <matrix-filter-room-list-input
        v-if="showFilter && fullPageMode"
        ref="filter"
        :show-filter.sync="showFilter"
        :filter-text.sync="filterText"
        @update:filterText="handleFilterUpdate" />
      <div
        v-else
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
    </template>
    <template
      slot="titleIcons">
      <v-btn
        v-if="!fullPageMode"
        :title="$t('matrix.chat.quick.create.discussion')"
        icon
        @click="openQuickCreateChatDiscussionDrawer">
        <v-icon
          class="icon-default-color"
          size="20">
          fa-plus
        </v-icon>
      </v-btn>
      <div 
        v-if="selectedRoom && fullPageMode"
        class="text-truncate">
        <room-avatar :room="selectedRoom" />
      </div>
      <room-header-actions
        v-if="selectedRoom && fullPageMode"
        ref="roomHeaderActions"
        :room="selectedRoom"
        class="ms-auto" />
    </template>
    <template slot="content">
      <matrix-chat-body
        ref="chatBody"
        :key="componentKey"
        :loading="loading"
        :rooms="rooms"
        :selected-room="selectedRoom"
        :parent-expanded="expanded"
        :from-room-list="true"
        @loading="loading = $event" />
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data() {
    return {
      loading: false,
      filterText: '',
      showFilter: false,
      expanded: false,
      selectedRoom: null,
      componentKey: 0,
      previousRoomId: null
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
    fullPageMode() {
      return this.$root.fullPageMode;
    },
    customHeaderClass() {
      return this.fullPageMode ? this.selectedRoom ? 'fullPageHeader' : 'fullPageHeader_no_room' : '';
    },
    defaultRoomListContainerWidth() {
      return this.$root.defaultRoomListContainerWidth;
    }
  },
  watch: {
    loading() {
      this.checkLoading();
    },
    loadingRooms() {
      this.checkLoading();
    }
  },
  mounted() {
    this.checkLoading();
  },
  methods: {
    openFilter() {
      this.showFilter = !this.showFilter;
      this.$nextTick(() => {
        this.$refs.filter?.openFilter?.();
      });
    },
    handleResize() {
      this.computeMessagesContainerWidth();
    },
    computeMessagesContainerWidth() {
      this.$root.fullPageMessagesContainerWidth = this.$refs?.meedsChatDrawer?.$el?.clientWidth
        - this.defaultRoomListContainerWidth;
    },
    handleExpanded(expanded) {
      setTimeout(async () => {
        this.expanded = expanded;
        this.$root.fullPageMode = expanded;
        this.computeMessagesContainerWidth();
        this.selectedRoom = this.getLastOpenedRoom();
        await this.openDiscussion(this.selectedRoom || this.rooms?.[0]);
      }, 300);
    },
    getLastOpenedRoom() {
      const lastOpenedRoomId = localStorage.getItem('lastOpenedRoomId');
      if (lastOpenedRoomId) {
        return this.rooms.find(room => room.id === lastOpenedRoomId);
      }
      return null;
    },
    async openDiscussion(room) {
      this.componentKey++;
      this.selectedRoom = room;
      const reload = this.previousRoomId && this.selectedRoom?.id !== this.previousRoomId;
      if (reload) {
        await this.$refs?.chatBody?.openDiscussion?.();
      }
      this.previousRoomId = this.selectedRoom?.id;
      this.$root.$emit('room-discussion-opened', this.selectedRoom?.id);
    },
    handleFilterUpdate(text) {
      this.filterText = text;
      this.$emit('filter-updated', text);
    },
    checkLoading() {
      if (this.loading || this.loadingRooms) {
        this.$refs.meedsChatDrawer.startLoading();
      } else {
        this.$refs.meedsChatDrawer.endLoading();
      }
    },
    open() {
      if (!this.$refs.meedsChatDrawer.drawer) {
        this.$refs.meedsChatDrawer.open();
      }
    },
    openQuickCreateChatDiscussionDrawer() {
      this.$root.$emit(this.$chatConstants.ACTION_CHAT_OPEN_QUICK_CREATE_DISCUSSION_DRAWER);
    },
  },
};
</script>
