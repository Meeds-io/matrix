<!--

 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io

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
  <v-badge
    v-if="totalUnreadMessages > 0"
    :content="totalUnreadMessages <= 99 ? totalUnreadMessages : '99+'"
    :aria-label="$t('matrix.chat.button.tooltip')"
    :style="heightStyle"
    class="badge-display position-absolute"
    color="var(--allPagesBadgePrimaryColor, #d32a2a)"
    overlap
    dense
    flat />
</template>
<script>
import {ensureChatApp} from '../js/chatApp.js';

export default {
  props: {
    badgeName: {
      type: String,
      default: null,
    },
    size: {
      type: Number,
      default: () => 20,
    },
    topSpacing: {
      type: String,
      default: () => '-19px',
    },
    xSpacing: {
      type: String,
      default: () => '-3px',
    },
  },
  data: () => ({
    totalUnreadMessages: 0,
  }),
  computed: {
    heightStyle() {
      return {
        '--badge-x-spacing': this.xSpacing,
        '--badge-top-spacing': this.topSpacing,
        '--badge-min-width': `${this.size}px`,
        '--badge-height': `${this.size}px`,
      };
    },
  },
  created() {
    document.addEventListener('meeds-chat-total-unread-changed', this.updateCount);
    // The chat instance announces its count on change; a badge mounted after
    // the last change asks for it, otherwise it stays empty until the next
    // message arrives
    document.dispatchEvent(new CustomEvent('meeds-chat-total-unread-request'));
    // The count only exists in the browser, so when no chat instance is on the
    // page — its topbar item unpinned by an administrator — one is mounted
    // hidden to compute it. `meedsChat.chatEnabled` carries the same condition
    // the topbar button renders on: enabled service, at least one room type,
    // and this user allowed by the restricted groups. Without it there is
    // nothing to count and a client would be mounted that cannot work.
    if (typeof meedsChat !== 'undefined' && meedsChat.chatEnabled) {
      ensureChatApp();
    }
  },
  beforeDestroy() {
    document.removeEventListener('meeds-chat-total-unread-changed', this.updateCount);
  },
  methods: {
    updateCount(event) {
      this.totalUnreadMessages = event?.detail || 0;
    },
  },
};
</script>
