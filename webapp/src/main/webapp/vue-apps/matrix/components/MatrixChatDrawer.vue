<template>
  <exo-drawer
    ref="meedsChatDrawer"
    id="meedsChatDrawer"
    :loading="loading"
    :filter-placeholder="$t('matrix.rooms.filter.placeholder')"
    class="meeds-chat-drawer"
    :use-filter="!$root.fullPageMode"
    :class="customHeaderClass"
    allow-expand
    right
    @filter-updated="handleFilterUpdate"
    @expand-updated="handleExpanded"
    @closed="$emit('closed')">
    <template slot="title">
      <v-text-field
        ref="filter"
        v-if="showFilter && fullPageMode"
        v-model="filterText"
        :placeholder="$t('matrix.rooms.filter.placeholder')"
        class="my-0 ms-0 me-5 pa-0 filter"
        hide-details
        @focus="filterFocused = true"
        @blur="filterFocused = false">
        <template #prepend-inner>
          <v-icon
            :class="{'primary--text': !!filterText || filterFocused }"
            class="mt-1"
            size="16">
            fa-filter
          </v-icon>
        </template>
        <template #prepend>
          <v-btn
            icon
            class="pa-0 mb-n1 mx-0 mt-0"
            @click="showFilter = !showFilter">
            <v-icon
              class="icon-default-color"
              size="20">
              fa-arrow-left
            </v-icon>
          </v-btn>
        </template>
        <template
          v-if="!!filterText"
          #append>
          <v-btn
            class="pa-0 mt-1 mx-0 mb-0"
            width="24"
            height="24"
            icon
            @click="filterText = ''">
            <v-icon
              class="primary--text"
              size="16">
              fa-times
            </v-icon>
          </v-btn>
        </template>
      </v-text-field>
      <div 
        v-else
        class="d-flex">
        <div class="d-flex">
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
        v-if="!$root.fullPageMode"
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
        v-if="room && fullPageMode"
        class="text-truncate">
        <room-avatar :room="room" />
      </div>
      <room-header-actions
        v-if="room && fullPageMode"
        ref="roomHeaderActions"
        :room="room"
        class="ms-auto" />
    </template>
    <template slot="content">
     <div 
      :class="{'d-flex overflow-hidden': fullPageMode}"
      class="fill-height">
      <v-sheet
        :max-width="420"
        :min-width="420"
        :class="{
          'disabled-background': !rooms?.length,
          'background-grey-primary ': fullPageMode
        }"
        class="fill-height overflow-y-auto flex-shrink-1 flex-grow-1 overflow-x-hidden specific-scrollbar">
        <matrix-chat-rooms
          :rooms="sortedRooms"
          :selectedRoom="selectedRoom"
          :loading="loading" />
      </v-sheet>
      <div 
        v-if="fullPageMode"
        class="d-flex flex-column flex-grow-1 fill-height">
        <div class="flex-grow-1 d-flex flex-column overflow-hidden">
          <room-messages
            v-if="room"
            ref="roomMessages"
            :room="room"
            :expanded="fullPageMode"
            :is-input-focused="isInputFocused"
            @loading="loading = $event"
            class="flex-grow-1 overflow-auto" />
        </div>
        <div class="flex-shrink-0 px-4 py-2">
          <message-composer
            v-if="room"
            ref="messageComposer"
            :room="room"
            :key="componentKey"
            @mark-room-as-read="markRoomAsRead"
            @input-focus="isInputFocused = $event" />
        </div>
      </div>
     </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  data() {
    return {
      searchTimer: null,
      searchTerm: null,
      room: null,
      previousRoomId: null,
      loading: false,
      isInputFocused: false,
      componentKey: 0,
      defaultRoomListContainerWidth: 404,
      filterText: '',
      showFilter: false,
      selectedRoom: null
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
  created() {
    this.$root.$on('open-chat-discussion', this.openDiscussion);
    window.addEventListener('resize', this.handleResize);
  },
  beforeDestroy() {
    this.$root.$off('open-chat-discussion', this.openDiscussion);
  },
  computed: {
    fullPageMode() {
      return this.$root.fullPageMode;
    },
    customHeaderClass() {
      return this.fullPageMode ? this.room ? 'fullPageHeader' : 'fullPageHeader_no_room' : '';
    },
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
    },
    filterText() {
      this.handleFilterUpdate(this.filterText);
    }
  },
  mounted() {
    this.checkLoading();
  },
  methods: {
    openFilter() {
      this.showFilter = !this.showFilter;
      this.$nextTick(() => {
        this.$refs?.filter?.focus?.();
      });
    },
    handleResize() {
      this.computeMessagesContainerWidth();
    },
    computeMessagesContainerWidth() {
      this.$root.fullPageMessagesContainerWidth = this.$refs?.meedsChatDrawer?.$el?.clientWidth - this.defaultRoomListContainerWidth;
    },
    handleExpanded(expanded) {
    setTimeout(async () => {
      this.$root.fullPageMode = expanded;
      this.computeMessagesContainerWidth();
      this.selectedRoom = this.getLastOpenedRoom();
      this.openDiscussion(this.selectedRoom || this.sortedRooms?.[0])
      }, 300)
    },
    getLastOpenedRoom() {
      const lastOpenedRoomId = localStorage.getItem('lastOpenedRoomId');
      if (lastOpenedRoomId) {
        return this.rooms.find(room => room.id === lastOpenedRoomId);
      }
      return null;
    },
    async openDiscussion(room) {
      if (this.$root.fullPageMode) {
        this.room = room;
        this.selectedRoom = room;
        this.componentKey++;

        await this.$nextTick();

        const reload = this.previousRoomId && this.room?.id !== this.previousRoomId;

        if (reload) {
          await this.$refs?.roomMessages?.initDiscussion();
          await this.$refs?.roomMessages?.loadAndProcessMessages();
        }

        setTimeout(() => {
          this.$refs?.messageComposer.setInputFocus();
        }, 200);

        this.previousRoomId = this.room?.id;
        this.$root.$emit("room-discussion-opened", this.room?.id);
      }
    },
    markRoomAsRead(roomId) {
      this.$refs.roomMessages.markRoomAsRead(roomId)
    },
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
    openQuickCreateChatDiscussionDrawer() {
      this.$root.$emit(this.$chatConstants.ACTION_CHAT_OPEN_QUICK_CREATE_DISCUSSION_DRAWER);
    },
  },
};
</script>
