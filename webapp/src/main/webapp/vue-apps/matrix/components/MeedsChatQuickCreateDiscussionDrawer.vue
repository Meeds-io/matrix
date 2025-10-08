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
    <template slot="title">
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
    <template slot="content">
      <v-form
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
          </div>
        </div>
      </v-form>
    </template>
    <template slot="footer">
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
      spaceCircleTemplate: null
    };
  },
  computed: {
    canCreatePrivateRooms() {
      return !!this.spaceCircleTemplate;
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
      return !this.participant;
    }
  },
  created() {
    this.checkCanCreatePrivateRooms();
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
      this.participant = null;
      this.$refs.QuickCreateDiscussionDrawer.close();
    },
    createPrivateRoom() {
      this.loading = true;
      const space = {
        invitedMembers: this.invitedSpaceMembers,
        subscription: this.spaceCircleTemplate.spaceDefaultRegistration?.toLowerCase?.(),
        visibility: this.spaceCircleTemplate.spaceDefaultVisibility?.toLowerCase?.(),
        templateId: this.spaceCircleTemplate.id
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
    checkCanCreatePrivateRooms() {
      this.$spaceTemplateService.getSpaceTemplates(false).then(templates => {
        const circleTemplate = templates?.find?.(template => template.system && template.layout === 'circle');
        if (circleTemplate) {
          this.$spaceTemplateService.canCreateSpaceWithTemplate(circleTemplate.id).then(data => {
            if (data?.canCreate) {
              this.spaceCircleTemplate = circleTemplate;
            }
          });
        }
      });
    }
  }
};
</script>
