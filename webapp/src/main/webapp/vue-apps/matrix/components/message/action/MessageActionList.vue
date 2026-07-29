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
  <v-list
    class="ma-0 py-0 text-no-wrap width-fit-content
    border-box-sizing"
    dense>
    <v-list-item class="ma-0 height-auto px-2 py-1">
      <div class="d-flex">
        <emoji-picker-button
          use-quick-emojis
          @select-emoji="$emit('reaction', $event)" />
        <v-divider
          class="mx-2"
          vertical />
        <v-btn
          width="28"
          height="28"
          min-width="28"
          class="pa-0"
          icon
          @click="$emit('reply')">
          <v-icon
            size="16"
            class="icon-default-color">
            fas fa-reply
          </v-icon>
        </v-btn>
        <v-menu
          v-if="isMyMessage || aiConciergeEnabled || canSaveAttachment"
          v-model="showMoreActions"
          content-class="l-auto r-0 border-radius"
          :attach="`#message${message.origin_server_ts}`"
          :top="openOnTop"
          :nudge-top="openOnTop && 28 || -14"
          absolute
          open-on-click
          close-on-content-click
          offset-x
          offset-y>
          <template #activator="{ on, attrs }">
            <v-btn
              ref="activator"
              v-on="on"
              v-bind="attrs"
              width="28"
              height="28"
              min-width="28"
              :title="$t('matrix.chat.openMessageMenu')"
              icon
              @touchstart.stop="0"
              @touchend.stop="0"
              @mousedown.stop="0"
              @mouseup.stop="0"
              @click.prevent.stop>
              <v-icon
                size="16"
                class="icon-default-color">
                fa-ellipsis-v
              </v-icon>
            </v-btn>
          </template>
          <v-list class="py-1">
            <matrix-ask-ai-message-action
              v-if="aiConciergeEnabled"
              :message="message"
              @close="close" />
            <v-list-item
              v-if="isMyMessage && isText"
              class="chat-action-menu-item"
              :title="$t('matrix.chat.label.editMessage')"
              :aria-label="$t('matrix.chat.label.editMessage')"
              @click="handleEditMessage">
              <v-icon
                class="me-1"
                size="16">
                fa-edit
              </v-icon>
              {{ $t('matrix.chat.label.editMessage') }}
            </v-list-item>
            <v-list-item
              v-if="canSaveAttachment"
              class="chat-action-menu-item"
              :title="$t('matrix.room.attachments.saveInDocuments')"
              :aria-label="$t('matrix.room.attachments.saveInDocuments')"
              @click="handleSaveInDocuments">
              <v-icon
                class="me-1"
                size="16">
                fa-hdd
              </v-icon>
              {{ $t('matrix.room.attachments.saveInDocuments') }}
            </v-list-item>
            <!-- Delete stays last: the destructive action sits at the bottom of the menu. -->
            <v-list-item
              v-if="isMyMessage"
              class="chat-action-menu-item"
              :title="$t('matrix.chat.label.deleteMessage')"
              :aria-label="$t('matrix.chat.label.deleteMessage')"
              @click="handleDeleteMessage">
              <v-icon
                class="me-1 error-color"
                size="16">
                fa-trash
              </v-icon>
              <span class="error--text">
                {{ $t('matrix.chat.label.deleteMessage') }}
              </span>
            </v-list-item>
          </v-list>
        </v-menu>
      </div>
    </v-list-item>
  </v-list>
</template>

<script>

// The message types that carry a stored attachment, hence something to save.
const ATTACHMENT_MSG_TYPES = ['m.file', 'm.image', 'm.video', 'm.audio'];

export default {
  props: {
    message: {
      type: Object,
      default: {},
    },
    room: {
      type: Object,
      default: null,
    },
    isMyMessage: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      showMoreActions: false,
      openOnTop: false
    };
  },
  computed: {
    isText() {
      return this.message.content.msgtype === 'm.text';
    },
    aiConciergeEnabled() {
      return eXo.env.portal.aiConciergeEnabled;
    },
    /**
     * Whether this message carries an attachment that can be saved to Documents, and
     * the Documents add-on is there to save it to. Shown for any such message, one's
     * own or not — so the menu appears on others' attachments too, with Save as its
     * only entry.
     *
     * @returns {Boolean} true when a Save in Documents entry applies
     */
    canSaveAttachment() {
      return ATTACHMENT_MSG_TYPES.includes(this.message?.content?.msgtype)
        && !!this.message?.content?.url
        && this.$matrixService.isDocumentsDeployed();
    },
  },
  watch: {
    showMoreActions() {
      if (this.showMoreActions) {
        this.$root.$emit('message-child-menu-opened');
        this.adjustMenuPosition();
      } else {
        this.$root.$emit('message-child-menu-closed');
      }
    }
  },
  methods: {
    adjustMenuPosition() {
      this.$nextTick(() => {
        const activator = this.$refs.activator.$el;
        if (!activator) {
          return;
        }
        const activatorRect = activator.getBoundingClientRect();
        const viewportHeight = window.innerHeight;
        const spaceBelow = viewportHeight - activatorRect.bottom;
        const spaceAbove = activatorRect.top;
        const estimatedMenuHeight = 140;

        this.openOnTop = spaceBelow < estimatedMenuHeight && spaceAbove > estimatedMenuHeight;
      });
    },
    handleEditMessage() {
      this.$emit('edit', this.message);
      this.close();
    },
    handleDeleteMessage() {
      this.$emit('delete', this.message);
      this.close();
    },
    /**
     * Saves this message's attachment to Documents through the reusable folder picker,
     * the same flow the attachments drawer uses. Builds the attachment shape the
     * service expects from the message content.
     *
     * @returns {void}
     */
    handleSaveInDocuments() {
      const content = this.message?.content || {};
      const attachment = {
        eventId: this.message?.event_id,
        name: content.body,
        mxcUrl: content.url,
        mimetype: content.info?.mimetype,
        size: content.info?.size,
      };
      this.$matrixService.saveAttachmentInDocuments(attachment, this.room, {
        success: this.$t('matrix.room.attachments.saveInDocuments.success'),
        error: this.$t('matrix.room.attachments.saveInDocuments.error'),
        see: this.$t('matrix.room.attachments.saveInDocuments.see'),
      }).catch(() => this.$root.$emit('alert-message', this.$t('matrix.room.attachments.saveInDocuments.error'), 'error'));
      this.close();
    },
    close() {
      this.$emit('close');
    }
  },
};
</script>
