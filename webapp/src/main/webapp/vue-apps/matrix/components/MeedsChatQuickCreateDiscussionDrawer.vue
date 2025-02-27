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
    ref="QuickCreateDiscussionDrawer"
    id="QuickCreateDiscussionDrawer"
    right
    @closed="close">
    <template slot="title">
      <span class="PopupTitle"> <v-icon left @click="close">mdi-arrow-left</v-icon>{{ $t('matrix.chat.quick.create.discussion') }}</span>
    </template>
    <template slot="content">
      <v-form ref="QuickCreateRoom" id="QuickCreateRoom" class="px-2 ms-2 mt-5">
        <div class="d-flex flex-column flex-grow-1">
          <div class="d-flex flex-column mb-2">
            <label class="d-flex flex-row font-weight-bold my-2">{{ $t('matrix.chat.quick.create.discussion.add.people') }}</label>
            <div class="d-flex flex-row">
              <v-flex class="user-suggester text-truncate">
                <exo-identity-suggester
                  ref="invitedPeopleAutoCompleteToRoom"
                  v-model="participant"
                  :multiple="false"
                  :search-options="{}"
                  :labels="suggesterLabels"
                  include-users />
              </v-flex>
            </div>
            <div class="caption font-weight-light ps-1 muted font-italic">
                <span class="mr-2"><v-icon small>info</v-icon></span>{{ $t('matrix.chat.quick.create.discussion.info') }}.
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
  name: 'MeedsChatDrawer',

  data() {
    return {
      participant: null,
      fullName: '',
    };
  },
  computed: {
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
    this.$root.$on(this.$chatConstants.ACTION_CHAT_OPEN_QUICK_CREATE_DISCUSSION_DRAWER, this.openDrawer);
  },
  beforeDestroy() {
    this.$root.$off(this.$chatConstants.ACTION_CHAT_OPEN_QUICK_CREATE_DISCUSSION_DRAWER, this.openDrawer);
  },

  methods: {
    openDrawer() {
      this.$refs.QuickCreateDiscussionDrawer.open();
    },
    close(){
      this.participant = null;
      this.fullName = '' ;
      this.$refs.QuickCreateDiscussionDrawer.close();
    },
    quickCreateChatDiscussion() {
      const remoteId = this.participant.remoteId;
      if(remoteId) {
        this.close();
        this.$matrixService.openDMRoom(eXo.env.portal.userName, remoteId, matrixServerName);
      }
    }
  }
};
</script>
