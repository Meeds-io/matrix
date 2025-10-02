<template>
  <exo-drawer
    id="ChatDiscussionDrawer"
    ref="ChatDiscussionDrawer"
    :loading="loading"
    v-draggable="true"
    allow-expand
    hide-footer-divider
    go-back-button
    right
    @expand-updated="handleExpand"
    @closed="close">
    <template slot="title">
      <room-avatar
        v-if="room"
        :room="room" />
    </template>
    <template slot="titleIcons">
      <room-header-actions
        ref="roomHeaderActions"
        v-if="room"
        :room="room" />
    </template>
    <template slot="content">
      <room-messages
        ref="roomMessages"
        :room="room"
        :is-input-focused="isInputFocused"
        @reply="replyToMessage"
        @loading="loading = $event" />
    </template>
    <template slot="footer">
      <message-composer
        ref="messageComposer"
        :key=componentKey
        :room="room"
        :expanded="expanded"
        :drawer-width="drawerWidth"
        class="mx-auto"
        @mark-room-as-read="markRoomAsRead"
        @input-focus="isInputFocused = $event" />
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
      componentKey: 0
    };
  },
  provide() {
    return {
      getIsExpanded: () => this.expanded,
      getParentDrawerWidth: () => this.drawerWidth
    };
  },
  created() {
    this.$root.$on('open-chat-discussion', this.openDiscussion);
  },
  beforeDestroy() {
    this.$root.$off('open-chat-discussion', this.openDiscussion);
  },
  computed: {
    spaceId() {
      return this.room?.spaceId;
    },
  },
  methods: {
    handleExpand(expanded) {
      setTimeout(() => {
        this.expanded = expanded;
        this.drawerWidth = this.$refs?.ChatDiscussionDrawer?.$el?.clientWidth;
      }, 300)
    },
    markRoomAsRead(roomId) {
      this.$refs.roomMessages.markRoomAsRead(roomId)
    },
    async openDiscussion(e) {
      if (this.$root.fullPageMode) {
        this.room = null;
        return;
      }
      this.room = e;
      this.componentKey++;

      this.$refs?.roomHeaderActions?.getSpaceById(this?.spaceId);
      this.$refs.roomMessages?.scrollToEnd();

      await this.$refs?.roomMessages?.initDiscussion();
      
      if (!this.$refs.ChatDiscussionDrawer?.drawer) {
        this.$refs.ChatDiscussionDrawer?.open();
      }
        
      await this.$refs?.roomMessages?.loadAndProcessMessages();

      this.$root.$emit("room-discussion-opened", this.room?.id);
      setTimeout(() => {
        this.$refs?.messageComposer.setInputFocus();
      }, 200)
    },
    close() {
      this.$refs.messageComposer?.resetComposer();
      this.$refs.roomMessages?.reset()
      this.$refs.ChatDiscussionDrawer?.close();
    }
  },
};
</script>
