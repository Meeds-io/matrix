<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2025 Meeds Association contact@meeds.io

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
  <v-chip
    v-sanitized-html="formatReactionLabel"
    :class="{
      'current-user-reaction': isCurrentUserReaction,
      'other-user-reaction': !isCurrentUserReaction,
      'ms-2': isMyMessage,
      'me-2': !isMyMessage
    }"
    class="message-reaction-item px-2 mb-2 text-font-size"
    @click="$emit('reaction', emojiChar)"/>
</template>

<script>

export default {
  props: {
    reaction: {
      type: Array,
      default: null
    },
    isMyMessage: {
      type: Boolean,
      default: false
    }
  },
  computed: {
    isCurrentUserReaction() {
      return this.reaction.userIds.includes(matrixUserId);
    },
    formatReactionLabel() {
      const count = this.reaction.userIds.length;
      const displayCount = count > 1 ? (count > 9 ? '9+' : count) : '';
      return `${this.reaction.key} ${displayCount}`;
    },
    emojiChar() {
      return this.reaction?.key;
    }
  }
};
</script>
