<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2026 Meeds Association contact@meeds.io

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU Lesser General Public
 License as published by the Free Software Foundation; either
 version 3 of the License, or (at your option) any later version.
 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 Lesser General Public License for more details.

 You should have received a copy of the GNU Lesser General Public License
 along with this program; if not, write to the Free Software Foundation,
 Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
-->
<template>
  <!-- Where something shared from outside the chat is given a conversation. The
       add-on that started this handed over a file or a link and nothing else —
       no room id, no matrix id, no homeserver. -->
  <exo-drawer
    id="meedsChatShareFileDrawer"
    ref="shareFileDrawer"
    v-model="drawer"
    right
    :loading="sending"
    use-filter
    :filter-placeholder="$t('matrix.chat.shareFile.filter')"
    @filter-updated="term = $event"
    @closed="reset">
    <template #title>
      <span>{{ $t('matrix.chat.shareFile.title') }}</span>
    </template>
    <template v-if="drawer" #content>
      <!-- What is about to be sent, named, so a mistaken click is visible before
           it lands rather than after. -->
      <div class="d-flex align-center px-4 py-3">
        <v-icon size="18" class="icon-default-color me-2">
          {{ shareIcon }}
        </v-icon>
        <span class="text-truncate">{{ shareLabel }}</span>
      </div>
      <v-divider />
      <!-- The chat's own room row, in its picking role: a conversation must look
           here exactly as it looks in the room list, because it is the same
           conversation and the same component. -->
      <matrix-chat-room
        v-for="room in filteredRooms"
        :key="room.id"
        :room="room"
        select-mode
        @select="pick" />
      <div
        v-if="!filteredRooms.length"
        class="d-flex justify-center text-sub-title pa-4">
        {{ $t('matrix.chat.shareFile.empty') }}
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
    }
  },
  data() {
    return {
      drawer: false,
      sending: false,
      term: null,
      // What is being shared: {file} or {link: {url, title}} — never both.
      share: null,
      // The conversation to open once this drawer has finished closing.
      openWhenClosed: null,
    };
  },
  computed: {
    /**
     * What is being shared, named for the user: a file by its name, a link by
     * its title.
     *
     * @returns {String} the label, empty when no share is in progress
     */
    shareLabel() {
      return this.share?.file?.name || this.share?.link?.title || '';
    },
    /**
     * The icon that says which of the two this is — bytes travelling, or a
     * pointer to something that stays where it lives.
     *
     * @returns {String} the icon class
     */
    shareIcon() {
      return this.share?.file && 'fas fa-paperclip' || 'fas fa-link';
    },
    /**
     * The conversations to choose from, narrowed by the header filter. Both DMs
     * and space rooms: what is being shared is as likely to be meant for a team
     * as for one person.
     *
     * @returns {Array} the rooms to list
     */
    filteredRooms() {
      const term = this.term?.trim?.()?.toLowerCase?.();
      const rooms = this.rooms || [];
      return term && rooms.filter(room => room.name?.toLowerCase?.()?.includes(term)) || rooms;
    },
  },
  methods: {
    /**
     * Opens the picker on something to share.
     *
     * @param {Object} share {file} or {link: {url, title}}
     * @returns {void}
     */
    open(share) {
      this.share = share;
      this.drawer = true;
      this.$refs.shareFileDrawer.open();
    },
    /**
     * Sends the pending share into the picked conversation, then leaves the user
     * in it — a share they cannot see landing is a share they will do twice.
     *
     * @param {Object} room the picked conversation
     * @returns {Promise<void>} resolves once sent or refused
     */
    pick(room) {
      if (this.sending) {
        // The rows stay clickable while the send runs; a second click would send
        // the same thing twice.
        return Promise.resolve();
      }
      this.sending = true;
      return this.sendTo(room, this.share)
        .then(() => {
          // Opened from the closed handler, not here: closing this drawer is
          // animated, and a conversation opened before that animation ends is
          // closed again by it — which is how the user lands back on the room
          // list instead of the message they just sent.
          this.openWhenClosed = room;
          this.close();
        })
        .catch(error => {
          if (error?.code === 'FILE_TOO_LARGE') {
            this.$root.$emit('alert-message', this.$t('matrix.chat.shareFile.tooLarge',
              {0: Math.round(error.maxUploadSize / (1024 * 1024))}), 'warning');
          } else {
            console.error('Sharing into a conversation failed:', error);
            this.$root.$emit('alert-message', this.$t('matrix.chat.shareFile.error'), 'error');
          }
        })
        .finally(() => this.sending = false);
    },
    /**
     * Puts one share into one room, whichever of the two shapes it is. Also the
     * path taken when the caller already named the room, so both ways send the
     * same message.
     *
     * @param {Object} room the target conversation
     * @param {Object} share {file} or {link: {url, title}}
     * @returns {Promise} resolved when the message is sent
     */
    sendTo(room, share) {
      if (share?.file) {
        return this.$matrixService.sendFileToRoom(share.file, room.id);
      }
      const {url, title} = share?.link || {};
      // A link, not an upload: what lives in another app keeps its own
      // permissions and its own current version there. The plain body carries
      // the URL for clients that ignore formatting.
      //
      // target="_blank" because following it means leaving for another app — a
      // document opens in its editor, and swallowing the chat to get there loses
      // the conversation the reader was in. Mentions already travel with the
      // same attribute, so the message sanitizer keeps it.
      return this.$matrixService.sendMessage({
        msgtype: 'm.text',
        body: title && `${title}\n${url}` || url,
        format: 'org.matrix.custom.html',
        formatted_body: `<a href="${url}" target="_blank" rel="noopener noreferrer">${title || url}</a>`,
      }, room.id);
    },
    /**
     * Closes the drawer.
     *
     * @returns {void}
     */
    close() {
      this.drawer = false;
      this.$refs.shareFileDrawer.close();
    },
    /**
     * Forgets the closed share, so the next one starts clean — and opens the
     * conversation the share landed in, now that this drawer is out of the way.
     *
     * @returns {void}
     */
    reset() {
      this.drawer = false;
      this.share = null;
      this.term = null;
      this.sending = false;
      const room = this.openWhenClosed;
      this.openWhenClosed = null;
      if (room) {
        // detail.room, not detail: openRoom reads event.detail.room and falls
        // back to the event itself, which opens an empty discussion.
        // discussionOnly: the share came from another app, so closing the
        // conversation should uncover that app again, not a room list.
        document.dispatchEvent(new CustomEvent(this.$chatConstants.ACTION_OPEN_CHAT_ROOM,
          {detail: {room, discussionOnly: true}}));
      }
    },
  },
};
</script>
