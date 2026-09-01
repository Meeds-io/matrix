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
          v-if="isMyMessage || aiConciergeEnabled || canSaveAttachment || messageActions.length"
          v-model="showMoreActions"
          content-class="border-radius no-max-width"
          :attach="`#message${message.origin_server_ts} .chat-message-hover-menu`"
          :top="openOnTop"
          nudge-bottom="4"
          nudge-right="8"
          open-on-click
          close-on-content-click
          left
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
            <!-- Contributed entries, discovered through the ('chat', 'message-action')
                 extension point — see js/ChatActionExtensions.js for the contract.
                 They render between the built-in entries and Delete. -->
            <template v-for="action in messageActions">
              <component
                :is="action.vueComponent"
                v-if="action.vueComponent"
                :key="action.id"
                :context="messageActionContext"
                @close="close" />
              <v-list-item
                v-else
                :key="action.id"
                class="chat-action-menu-item"
                :title="actionLabel(action)"
                :aria-label="actionLabel(action)"
                @click="handleExtensionAction(action)">
                <v-icon
                  class="me-1"
                  size="16">
                  {{ action.icon }}
                </v-icon>
                {{ actionLabel(action) }}
              </v-list-item>
            </template>
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
import {getMessageActions, includeChatActionExtensions, MESSAGE_ACTION_UPDATED_EVENT} from '../../../js/ChatActionExtensions.js';

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
      openOnTop: false,
      // Bumped when the registry announces a new contribution, so the
      // messageActions computed re-reads a registry Vue cannot observe.
      actionsStamp: 0
    };
  },
  created() {
    includeChatActionExtensions();
    document.addEventListener(MESSAGE_ACTION_UPDATED_EVENT, this.refreshActions);
  },
  beforeDestroy() {
    document.removeEventListener(MESSAGE_ACTION_UPDATED_EVENT, this.refreshActions);
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
    /**
     * The attachment this message carries, in the shape the extension contract
     * promises — or null for a plain message, which is itself part of the
     * contract: an action gates itself on the attachment's presence and type.
     *
     * @returns {Object} {fileName, mimeType, size, mxcUrl}, or null
     */
    attachment() {
      const content = this.message?.content;
      if (!ATTACHMENT_MSG_TYPES.includes(content?.msgtype) || !content?.url) {
        return null;
      }
      return {
        fileName: content.filename || content.body,
        mimeType: content.info?.mimetype,
        size: content.info?.size,
        mxcUrl: content.url,
      };
    },
    /**
     * What a contributed action receives — everything it may act on, so a
     * consumer never reaches into this app's internals. See
     * js/ChatActionExtensions.js for the published contract.
     *
     * @returns {Object} the message action context
     */
    messageActionContext() {
      return {
        message: this.message,
        room: this.room,
        isMyMessage: this.isMyMessage,
        attachment: this.attachment,
        getAttachmentFile: this.getAttachmentFile,
      };
    },
    /**
     * The contributed actions that apply to this message, re-read when the
     * registry announces a new contribution (the stamp) or the context changes.
     *
     * @returns {Array} the applicable action descriptors, in rank order
     */
    messageActions() {
      return this.actionsStamp >= 0 && getMessageActions(this.messageActionContext) || [];
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
    /**
     * Re-evaluates the contributed actions after the registry changed under us —
     * the stamp is the computed's only way to know the registry moved.
     *
     * @returns {void}
     */
    refreshActions() {
      this.actionsStamp++;
    },
    /**
     * Reads this message's attachment back as a File, the authenticated way —
     * handed to contributed actions so none of them touches media URLs or the
     * access token.
     *
     * @returns {Promise} resolved with the attachment content as a File
     */
    getAttachmentFile() {
      const attachment = this.attachment;
      if (!attachment) {
        return Promise.reject(new Error('This message carries no attachment'));
      }
      return this.$matrixService.getMediaFile(attachment.mxcUrl, attachment.fileName, attachment.mimeType);
    },
    /**
     * The label of a contributed entry: its key resolved against the matrix
     * bundle (merged across webapps, so contributors ship their own keys), or
     * the pre-resolved label a contributor may hand instead.
     *
     * @param {Object} action the contributed descriptor
     * @returns {String} the label to render
     */
    actionLabel(action) {
      return action.labelKey && this.$t(action.labelKey) || action.label || '';
    },
    /**
     * Runs a contributed action with the published context, then closes the
     * menu — same lifecycle as the built-in entries.
     *
     * @param {Object} action the contributed descriptor
     * @returns {void}
     */
    handleExtensionAction(action) {
      if (action.click) {
        action.click(this.messageActionContext);
      }
      this.close();
    },
    adjustMenuPosition() {
      this.$nextTick(() => {
        const activator = this.$refs.activator?.$el;
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
