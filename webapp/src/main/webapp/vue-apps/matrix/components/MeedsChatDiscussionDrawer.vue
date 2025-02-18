/*
 * Copyright (C) 2022 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
<template>
  <exo-drawer
    ref="ChatDiscussionDrawer"
    id="ChatDiscussionDrawer"
    right
    :loading="loading"
    @closed="close">
    <template slot="title">
      <span class="PopupTitle">
        <v-icon left @click="close">mdi-arrow-left</v-icon>
        <v-avatar size="36" class="me-3">
          <v-img :src="room.avatarUrl" eager />
        </v-avatar>
        <span class="content-align"> {{room.name}} </span>
      </span>
    </template>
    <template slot="content">
      <div class="d-flex flex-column">
        <meeds-chat-message :key="i" v-for="(message, i) in messages" :message="message" :previous-sender="i > 0 && messages[i-1].sender"/>
      </div>
    </template>
    <template slot="footer">

    </template>
  </exo-drawer>
</template>
<script>

export default {
  name: 'ChatDiscussionDrawer',

  data() {
    return {
      messages: [],
      room: {},
      loading: false,
    };
  },
  computed: {

  },

  created() {
    document.addEventListener(this.$chatConstants.ACTION_CHAT_OPEN_DISCUSSION_DRAWER,e => this.openDiscussion(e));
  },
  beforeDestroy() {
    document.removeEventListener(this.$chatConstants.ACTION_CHAT_OPEN_DISCUSSION_DRAWER,e => this.openDiscussion(e));
  },

  methods: {
    openDiscussion(e) {
      this.loading = true;
      this.room = e.detail;
      this.$refs.ChatDiscussionDrawer.open();
      this.$nextTick().then(() => {
        this.$matrixService.loadRoomMessages(this.room.id).then(resp => {
          this.messages = resp.chunk;
          this.loading = false
        });
      });
    },
    close(){
      this.messages = null;
      this.$refs.ChatDiscussionDrawer.close();
    },
  }
};
</script>
