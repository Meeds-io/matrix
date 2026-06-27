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
  <div class="d-flex">
    <div class="room-action-components">
      <div
        v-for="action in enabledRoomActionComponents"
        :key="action.key"
        :class="`${action.appClass} ${action.typeClass}`"
        :ref="action.key">
        <div v-if="action.component">
          <component
            v-dynamic-events="action.component.events"
            v-bind="action.component.props ? action.component.props : {}"
            :is="action.component.name" />
        </div>
        <div v-else-if="action.element" v-html="action.element.outerHTML">
        </div>
        <div v-else-if="action.html" v-html="action.html">
        </div>
      </div>
    </div>
    <v-menu
      v-model="menu"
      content-class="border-radius overflow-hidden"
      :nudge-left="-30"
      open-on-click
      left
      close-on-content-click
      offset-x
      offset-y>
      <template #activator="{ on, attrs }">
        <v-btn
          v-on="on"
          v-bind="attrs"
          icon>
          <v-icon
            size="20"
            class="icon-default-color">
            fa-ellipsis-v
          </v-icon>
        </v-btn>
      </template>
      <v-list class="pa-0">
        <v-list-item
          v-if="canEditSpace"
          class="ps-2 pe-3 height-auto"
          @click="editSpace">
          <v-sheet
            class="d-flex"
            width="28"
            height="36">
            <v-icon
              class="icon-default-color mx-auto"
              size="16">
              fas fa-cog
            </v-icon>
          </v-sheet>
          {{ $t('matrix.room.space.editProperties') }}
        </v-list-item>
        <v-list-item
          class="ps-2 pe-3 height-auto"
          @click.stop="muteRoom">
          <v-sheet
            class="d-flex"
            width="28"
            height="36">
            <v-icon
              class="icon-default-color mx-auto"
              size="16">
              {{ isMuted ? 'fas fa-bell' : 'fas fa-bell-slash' }}
            </v-icon>
          </v-sheet>
          <span v-if="!isMuted">
            {{ $t('matrix.room.mute.label') }}
          </span>
          <span v-else>
            {{ $t('matrix.room.unmute.label') }}
          </span>
        </v-list-item>
        <v-list-item
          v-if="!!spaceId"
          class="ps-2 pe-3 height-auto"
          @click.stop="showMembers">
          <v-sheet
            class="d-flex"
            width="28"
            height="36">
            <v-icon
              class="icon-default-color mx-auto"
              size="16">
              fas fa-users
            </v-icon>
          </v-sheet>
          <span>
            {{ $t('matrix.room.members.label') }}
          </span>
        </v-list-item>
        <v-list-item
          class="ps-2 pe-3 height-auto"
          @click.stop="showAttachments">
          <v-sheet
            class="d-flex"
            width="28"
            height="36">
            <v-icon
              class="icon-default-color mx-auto"
              size="16">
              fas fa-paperclip
            </v-icon>
          </v-sheet>
          <span>
            {{ $t('matrix.room.attachments.label') }}
          </span>
        </v-list-item>
      </v-list>
    </v-menu>
  </div>
</template>

<script>

export default {
  data() {
    return {
      space: null,
      menu: false,
      roomActionComponents: [],
      initializedActions: []
    };
  },
  props: {
    room: {
      type: Object,
      required: true
    }
  },
  computed: {
    isMuted() {
      return this.room?.muted;
    },
    spaceId() {
      return this.room?.spaceId;
    },
    canEditSpace() {
      return this.spaceId && this.space?.canEdit;
    },
    enabledRoomActionComponents() {
      return this.roomActionComponents && this.roomActionComponents.filter(action => action.enabled) || [];
    }
  },
  watch: {
    room() {
      this.roomActionComponents = [];
      this.initializedActions = [];
      this.getSpaceById(this.spaceId);
    },
  },
  created() {
    this.$root.$on('room-discussion-opened', this.initRoomActionComponents);
  },
  beforeDestroy() {
    this.$root.$off('room-discussion-opened', this.initRoomActionComponents);
  },
  mounted() {
    this.getSpaceById(this.spaceId);
    this.initRoomActionComponents();
  },
  methods: {
    async getSpaceById(spaceId) {
      if (this.space?.id === spaceId || !spaceId) {
        return;
      }
      try {
        this.space = await this.$spaceService.getSpaceById(spaceId, null, true);
      } catch (error) {
        console.error('Failed to fetch space:', error);
      }
    },
    muteRoom() {
      this.$matrixService.muteRoom(this.room.id, this.spaceId, this.isMuted).then(() => {
        this.$root.$emit(
          'alert-message',
          this.$t(`matrix.room.${!this.isMuted ? 'mute' : 'unmute'}.success`),
          'success');
        this.menu = false;
        setTimeout(() => {
          this.room.muted = !this.isMuted;
        }, 100);
      });
    },
    initRoomActionComponents() {
      this.roomActionComponents = extensionRegistry ? extensionRegistry.loadExtensions('chat', 'chat-drawer-title-action-component') : [];
      this.$nextTick().then(() => {
        const chat = {
          currentUser: eXo.env.portal.userName,
          fullname: this.room.name,
          type: this.room.directChat && 'u' || 's',
          prettyName: this.room.prettyName,
          user: this.room.dmMemberId,
          spaceId: this.room.spaceId,
          participants: []
        };
        for (const action of this.roomActionComponents) {
          const actionInitialized = this.initializedActions.some(actionToCheck => actionToCheck.key === action.key);
          if (action.init && action.enabled && !actionInitialized) {
            let container = this.$refs[action.key];
            if (container && container.length > 0) {
              container = container[0];
              action.init(container, chat);
              this.initializedActions.push(action);
            }
          }
        }
      });
    },
    editSpace() {
      window.require(['SHARED/spaceForm'], drawer => drawer.edit(this.space?.id));
    },
    showMembers() {
      this.$root.$emit('show-room-members', this.space);
      this.$nextTick(() => this.menu = false);
    },
    showAttachments() {
      this.$root.$emit('show-room-attachments', this.room);
      this.$nextTick(() => this.menu = false);
    }
  }
};
</script>
