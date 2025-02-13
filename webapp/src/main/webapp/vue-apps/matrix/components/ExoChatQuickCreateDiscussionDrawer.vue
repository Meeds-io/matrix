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
      <v-form ref="Quicksuggester" id="Quicksuggester" class="pa-2 ms-2 mt-4">
        <div class="d-flex flex-column flex-grow-1">
          <div class="d-flex flex-column mb-2">
            <label class="d-flex flex-row font-weight-bold my-2">{{ $t('matrix.chat.quick.create.discussion.add.people') }}</label>
            <div class="d-flex flex-row">
              <v-flex class="user-suggester text-truncate">
                <exo-identity-suggester
                  ref="invitedPeopleAutoCompleteToRoom"
                  v-model="participants"
                  :multiple="false"
                  :search-options="{}"
                  :labels="suggesterLabels"
                  include-users />
                <div v-if="participantItem" class="identitySuggester no-border mt-0">
                  <exo-chat-quick-discussion-participant-item
                    v-for="item in participantItem"
                    :key="item.identity.id"
                    :attendee="item"
                    @remove-attendee="removeAttendee" />
                </div>
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
      <div class="d-flex my-2 flex-row justify-end">
        <button class="mx-5 px-8 btn" @click="close()">{{ $t('matrix.chat.cancel') }}</button>
        <button
          :disabled="disabledSaveButton"
          class="btn btn-primary"
          @click="quickCreateChatDiscussion">
          {{ $t('matrix.chat.quick.discussion.add') }}
        </button>
      </div>
    </template>
  </exo-drawer>
</template>


<script>

export default {
  name: 'ExoChatDrawer',
  components: {},

  data() {
    return {
      participants: [],
      participantItem: [],
      fullName: '',
    };
  },
  computed: {
    validNewRoomName() {
      return this.fullName && this.fullName.trim().length;
    },
    suggesterLabels() {
      return {
        placeholder: this.$t('matrix.chat.team.help'),
      };
    },
    disabledSaveButton(){
      return !this.participantItem || this.participantItem.length !== 1;
    }
  },
  watch: {
    participants() {
      if (!this.participants) {
        this.$nextTick(this.$refs.invitedPeopleAutoCompleteToRoom.$refs.selectAutoComplete.deleteCurrentItem);
        return;
      }
      if (!this.participantItem) {
        this.participantItem = [];
      }
      const found = this.participantItem?.find(item => {
        return item.identity.remoteId === this.participants.remoteId
            && item.identity.providerId === this.participants.providerId;
      });
      if (!found) {
        this.participantItem.push({
          identity: this.participants,
        });
      }
      this.participants = null;
    },
  },
  created() {
    console.log('created quick create discussion drawer');
    this.$root.$on(this.$chatConstants.ACTION_CHAT_OPEN_QUICK_CREATE_DISCUSSION_DRAWER, this.openDrawer);
  },

  methods: {
    openDrawer() {
      this.$refs.QuickCreateDiscussionDrawer.open();
    },
    close(){
      this.participants = null;
      this.participantItem = [];
      this.fullName = '' ;
      this.$refs.QuickCreateDiscussionDrawer.close();
    },
    removeAttendee(attendee) {
      const index = this.participantItem.findIndex(addedAttendee => {
        return attendee.identity.remoteId === addedAttendee.identity.remoteId
            && attendee.identity.providerId === addedAttendee.identity.providerId;
      });
      if (index >= 0) {
        this.participantItem.splice(index, 1);
      }
    },
    displayAlert(message, type) {
      document.dispatchEvent(new CustomEvent('notification-alert', {detail: {
        message,
        type: type || 'success',
      }}));
    },
    quickCreateChatDiscussion() {
      const remoteId = this.participantItem[0].identity.remoteId;
      if(remoteId) {
        this.close();
        this.$matrixService.openDMRoom(eXo.env.portal.userName, remoteId, matrixServerName);
      }
    }
  }
};
</script>
