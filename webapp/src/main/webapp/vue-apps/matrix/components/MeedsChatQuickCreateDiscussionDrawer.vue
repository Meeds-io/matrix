<!--

  This file is part of the Meeds project (https://meeds.io/).

  Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io

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
  <exo-drawer
    ref="QuickCreateDiscussionDrawer"
    id="QuickCreateDiscussionDrawer"
    right
    @closed="close">
    <template #title>
      <span class="d-flex my-auto text-header-title font-weight-bold text-color">
        <v-icon
          size="18"
          class="icon-default-color icon-default-size"
          left
          @click="close">fas fa-arrow-left
        </v-icon>
        {{ $t('matrix.chat.quick.create.discussion') }}
      </span>
    </template>
    <template #content>
      <v-form
        v-if="!isParentSpaceSelecting"
        id="QuickCreateRoom"
        ref="QuickCreateRoom"
        class="px-2 ms-2 mt-5">
        <div class="d-flex flex-column flex-grow-1">
          <div class="d-flex flex-column mb-2">
            <label class="d-flex flex-row font-weight-bold">
              {{ $t('matrix.chat.quick.create.discussion.add.people') }}
            </label>
            <div class="d-flex flex-row">
              <v-flex class="user-suggester text-truncate">
                <exo-identity-suggester
                  ref="invitedPeopleAutoCompleteToRoom"
                  v-model="participant"
                  :multiple="canCreatePrivateRooms"
                  :search-options="{}"
                  :labels="suggesterLabels"
                  include-users />
              </v-flex>
            </div>
            <div
              v-if="!canCreatePrivateRooms"
              class="caption font-weight-light ps-1 muted font-italic">
              <span class="mr-2">
                <v-icon small>info</v-icon>
                {{ $t('matrix.chat.quick.create.discussion.info') }}.
              </span>
            </div>
            <div 
              v-else-if="shouldJoinParentSpace">
              <span class="text-subtitle">
                {{ $t('matrix.chat.quick.create.subspace.discussion.info') }}.
              </span>
            </div>
            <div
              v-else-if="!privateRoomsEnabled">
              <span class="text-subtitle">
                {{ $t('matrix.chat.quick.create.private.room.disabled') }}.
              </span>
            </div>
          </div>
        </div>
      </v-form>
      <matrix-chat-parent-space-selector
        v-if="canSelectParentSpace"
        v-model="selectedSpace"
        :parent-spaces="parentSpaceList"
        :can-edit="parentSpaceList.length > 1"
        :has-more="hasMoreParentSpaces"
        :loading-more="loadingMore"
        @selecting="isParentSpaceSelecting = $event"
        @load-more="loadMoreParentSpaces" />
    </template>
    <template #footer>
      <div class="d-flex flex-row justify-end">
        <v-btn
          class="me-2 btn"
          @click="close()">
          {{ $t('matrix.chat.cancel') }}
        </v-btn>
        <v-btn
          :disabled="disabledSaveButton"
          :loading="loading"
          class="btn btn-primary"
          @click="quickCreateChatDiscussion">
          {{ $t('matrix.chat.quick.discussion.add') }}
        </v-btn>
      </div>
    </template>
  </exo-drawer>
</template>
<script>

export default {
  data() {
    return {
      participant: null,
      loading: false,
      spaceCircleTemplate: null,
      isSubspaceTemplate: false,
      parentSpaceList: [],
      parentSpacesSize: 0,
      parentSpacesLimit: 20,
      loadingMore: false,
      selectedSpace: null,
      isParentSpaceSelecting: false
    };
  },
  computed: {
    canCreatePrivateRooms() {
      return !!this.spaceCircleTemplate;
    },
    canSelectParentSpace() {
      return this.invitedSpaceMembers && this.isSubspaceTemplate && this.parentSpaceList.length > 0;
    },
    shouldJoinParentSpace() {
      return this.invitedSpaceMembers && this.isSubspaceTemplate && this.parentSpaceList.length === 0;
    },
    privateRoomsEnabled() {
      return meedsChat.privateRoomsEnabled;
    },
    hasMoreParentSpaces() {
      return this.parentSpaceList.length < this.parentSpacesSize;
    },
    invitedSpaceMembers() {
      return this.participant?.length > 1 && this.participant || null;
    },
    suggesterLabels() {
      return {
        placeholder: this.$t('matrix.chat.team.help'),
      };
    },
    disabledSaveButton(){
      return !this.participant || (this.participant.length <= 1 && !meedsChat.privateRoomsEnabled) || this.shouldJoinParentSpace || this.canSelectParentSpace && !this.selectedSpace;
    },
  },
  async created() {
    await this.checkCanCreatePrivateRooms();
    await this.getParentSpaces();
    this.$root.$on(this.$chatConstants.ACTION_CHAT_OPEN_QUICK_CREATE_DISCUSSION_DRAWER, this.openDrawer);
  },
  beforeDestroy() {
    this.$root.$off(this.$chatConstants.ACTION_CHAT_OPEN_QUICK_CREATE_DISCUSSION_DRAWER, this.openDrawer);
  },

  methods: {
    openDrawer() {
      this.$refs.QuickCreateDiscussionDrawer.open();
    },
    close() {
      this.reset();
      this.$refs.QuickCreateDiscussionDrawer.close();
    },
    createPrivateRoom() {
      this.loading = true;
      const space = {
        invitedMembers: this.invitedSpaceMembers,
        subscription: this.spaceCircleTemplate.spaceDefaultRegistration?.toLowerCase?.(),
        visibility: this.spaceCircleTemplate.spaceDefaultVisibility?.toLowerCase?.(),
        templateId: this.spaceCircleTemplate.id,
        parentSpaceId: this.selectedSpace?.id || 0
      };
      this.$spaceService.createSpace(space).then((createdSpace) => {
        this.openChatRoom(createdSpace.id, true);
      }).finally(() => this.loading = false );
    },
    openChatRoom(id, space) {
      if (!space) {
        this.$matrixService.getParticipantInfo(id).then(participant => {
          if (participant?.matrixId) {
            this.$matrixService.openDMRoom(eXo.env.portal.userName, id, matrixServerName,
              matrixUserId, participant.matrixId);
          }
        });
      } else {
        this.$matrixService.openSpaceRoom(id);
      }
      this.close();
    },
    quickCreateChatDiscussion() {
      if (this.invitedSpaceMembers) {
        this.createPrivateRoom();
        return;
      }
      const remoteId = Array.isArray(this.participant) && this.participant[0].remoteId
                                                       || this.participant.remoteId;
      this.openChatRoom(remoteId);
    },
    async checkCanCreatePrivateRooms() {
      const templates = await this.$spaceTemplateService.getSpaceTemplates(false);
      const circleTemplate = templates?.find(template => template.system && template.layout === 'circle' && !template.deleted);
      this.spaceCircleTemplate = circleTemplate || null;
      if (this.spaceCircleTemplate) {
        const subspaceTemplateIds = await this.$spaceTemplateService.getSubspaceTemplateIds() || [];
        this.isSubspaceTemplate = subspaceTemplateIds.includes(this.spaceCircleTemplate.id);
      }
    },
    async getParentSpaces() {
      if (this.isSubspaceTemplate) {
        const data = await this.$spaceService.getSpacesByFilter({
          offset: 0,
          limit: this.parentSpacesLimit,
          subspaceTemplateId: this.spaceCircleTemplate.id,
          filter: 'accessible'
        });
        this.parentSpaceList = data?.spaces || [];
        this.parentSpacesSize = data?.size || this.parentSpaceList.length;
        if (this.parentSpacesSize === 1) {
          this.selectedSpace = this.parentSpaceList[0];
        }
      }
    },
    async loadMoreParentSpaces() {
      this.loadingMore = true;
      this.parentSpacesLimit += 20;
      await this.getParentSpaces();
      this.loadingMore = false;
    },
    reset() {
      this.participant = null;
      this.selectedSpace = null;
      this.isParentSpaceSelecting = false;
      this.parentSpacesLimit = 20;
    }
  }
};
</script>
