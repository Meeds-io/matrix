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
  <v-tooltip
    max-width="340"
    open-delay="500"
    bottom>
    <template #activator="{ on, attrs }">
      <v-chip
        v-sanitized-html="formattedReactionLabel"
        v-bind="attrs"
        v-on="on"
        :class="{
          'current-user-reaction': isCurrentUserReaction,
          'other-user-reaction': !isCurrentUserReaction,
          'ms-2': isMyMessage,
          'me-2': !isMyMessage
        }"
        class="message-reaction-item px-2 mb-2 text-font-size"
        @click="$emit('reaction', emojiChar)" />
    </template>
    <span>{{ tooltipText }}</span>
  </v-tooltip>
</template>

<script>

export default {
  data() {
    return {
      tooltipText: '',
      andLabel: this.$t('matrix.chat.label.and')
    };
  },
  props: {
    room: {
      type: Object,
      default: null
    },
    reaction: {
      type: Array,
      default: null
    },
    isMyMessage: {
      type: Boolean,
      default: false
    }
  },
  watch: {
    reactionUserIds: {
      immediate: true,
      handler: 'updateTooltip'
    }
  },
  computed: {
    isCurrentUserReaction() {
      return this.reactionUserIds.includes(matrixUserId);
    },
    reactionUserIds() {
      return this.reaction?.userIds;
    },
    formattedReactionLabel() {
      const count = this.reactionUserIds.length;
      const displayCount = count > 1 ? (count > 9 ? '9+' : count) : '';
      return `${this.reaction.key} ${displayCount}`;
    },
    emojiChar() {
      return this.reaction?.key;
    }
  },
  methods: {
    parseMatrixUserId(userId) {
      if (!userId) {
        return;
      }
      const regex = new RegExp(`^@([^:]+):${matrixServerName.replace(/\./g, '\\.')}$`);
      const match = userId.match(regex);
      return match ? match[1] : null;
    },
    async updateTooltip() {
      const userIds = this.reactionUserIds ?? [];
      const usernames = (await Promise.all(userIds.map(userId => this.extractMemberFullName(userId)))).filter(Boolean);
      this.tooltipText = this.formatUserList(usernames);
    },
    formatUserList(usernames) {
      const length = usernames.length;
      if (!length) {
        return '';
      }
      if (length === 1) {
        return usernames[0];
      }
      if (length === 2) {
        return `${usernames[0]} ${this.andLabel} ${usernames[1]}`;
      }
      return `${usernames.slice(0, -1).join(', ')} ${this.andLabel} ${usernames[length - 1]}`;
    },
    async extractMemberFullName(matrixId) {
      if (matrixId === matrixUserId) {
        const currentUser = await this.$matrixService.getUserIdentity(eXo.env.portal.userName);
        return currentUser?.profile?.fullname || matrixId;
      }

      let userId;
      if (!this.room.spaceId) {
        userId = this.room.dmMemberId;
      } else {
        userId = this.room?.members?.find(member => member.matrixId === this.parseMatrixUserId(matrixId))?.userId;
      }

      if (!userId) {
        return matrixId;
      }

      const user = await this.$matrixService.getUserByMatrixId(userId, this.room);
      return user?.profile?.fullname || userId;
    }
  }
};
</script>
