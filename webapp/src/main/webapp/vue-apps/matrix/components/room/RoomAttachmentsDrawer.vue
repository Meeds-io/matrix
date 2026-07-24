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
  <exo-drawer
    ref="attachmentsDrawer"
    class="matrix-attachments-drawer"
    right
    @closed="reset">
    <template #title>
      {{ $t('matrix.room.attachments.title') }}
    </template>
    <template #content>
      <div
        v-if="loading"
        class="d-flex justify-center align-center pa-8">
        <v-progress-circular
          indeterminate
          color="primary"
          size="32" />
      </div>
      <div
        v-else-if="!attachments.length"
        class="d-flex flex-column align-center justify-center pa-8 text-center">
        <v-icon size="48" class="mb-3 icon-default-color">fa-paperclip</v-icon>
        <span class="text-subtitle">{{ $t('matrix.room.attachments.empty') }}</span>
      </div>
      <v-list v-else class="pa-0">
        <v-list-item
          v-for="attachment in attachments"
          :key="attachment.eventId"
          class="px-4 attachment-item">
          <v-list-item-icon class="me-3 my-auto">
            <v-icon
              :size="20"
              :color="iconFor(attachment).color">
              {{ iconFor(attachment).class }}
            </v-icon>
          </v-list-item-icon>
          <v-list-item-content>
            <v-list-item-title class="text-truncate text-color">
              {{ attachment.name }}
            </v-list-item-title>
            <v-list-item-subtitle class="d-flex align-center text-caption">
              <date-format
                :value="attachment.timestamp"
                :format="dateFormat" />
              <template v-if="attachment.size">
                <span class="mx-1">·</span>{{ formatSize(attachment.size) }}
              </template>
            </v-list-item-subtitle>
          </v-list-item-content>
          <!-- position-relative + attach: the exo-drawer forces its own z-index, so a
               teleported menu renders behind it and takes no clicks; attaching it here
               makes it open inside the drawer's stacking context (same fix as email). -->
          <v-list-item-action class="ma-0 position-relative">
            <v-menu
              offset-y
              left
              attach
              content-class="border-radius overflow-hidden"
              min-width="200">
              <template #activator="{ on, attrs }">
                <v-btn
                  v-bind="attrs"
                  :loading="attachment.downloading || attachment.saving"
                  :title="$t('matrix.room.attachments.actions')"
                  :aria-label="$t('matrix.room.attachments.actions')"
                  icon
                  v-on="on">
                  <v-icon size="18" class="icon-default-color">fa-ellipsis-v</v-icon>
                </v-btn>
              </template>
              <v-list class="pa-0" dense>
                <v-list-item @click="download(attachment)">
                  <v-list-item-icon class="me-3 my-auto">
                    <v-icon size="16" class="icon-default-color">fa-download</v-icon>
                  </v-list-item-icon>
                  <v-list-item-title>{{ $t('matrix.chat.download') }}</v-list-item-title>
                </v-list-item>
                <v-list-item
                  v-if="documentsDeployed"
                  @click="saveInDocuments(attachment)">
                  <v-list-item-icon class="me-3 my-auto">
                    <v-icon size="16" class="icon-default-color">fa-hdd</v-icon>
                  </v-list-item-icon>
                  <v-list-item-title>{{ $t('matrix.room.attachments.saveInDocuments') }}</v-list-item-title>
                </v-list-item>
              </v-list>
            </v-menu>
          </v-list-item-action>
        </v-list-item>
      </v-list>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  data() {
    return {
      room: null,
      attachments: [],
      loading: false,
      dateFormat: {
        year: 'numeric',
        month: 'short',
        day: 'numeric',
      },
    };
  },
  computed: {
    documentsDeployed() {
      return this.$matrixService.isDocumentsDeployed();
    },
  },
  created() {
    this.$root.$on('show-room-attachments', this.open);
  },
  beforeDestroy() {
    this.$root.$off('show-room-attachments', this.open);
  },
  methods: {
    open(room) {
      this.room = room;
      this.attachments = [];
      this.$refs.attachmentsDrawer.open();
      // Render above the chat drawer (which sits at z-index 1035).
      this.$nextTick(() => {
        const el = this.$refs.attachmentsDrawer?.$el;
        if (el) {
          el.style.zIndex = '2001';
        }
      });
      this.load();
    },
    load() {
      if (!this.room?.id) {
        return;
      }
      this.loading = true;
      this.$matrixService.loadRoomAttachments(this.room.id)
        .then(list => this.attachments = list)
        .catch(e => console.error('Failed to load room attachments:', e))
        .finally(() => this.loading = false);
    },
    iconFor(attachment) {
      const extensions = Vue.prototype.$filesIconsExtension;
      return extensions[0].get(attachment.mimetype) || extensions[0].get('file');
    },
    formatSize(bytes) {
      if (!bytes) {
        return '';
      }
      const units = ['B', 'KB', 'MB', 'GB'];
      let value = bytes;
      let unit = 0;
      while (value >= 1024 && unit < units.length - 1) {
        value /= 1024;
        unit++;
      }
      return `${value.toFixed(value < 10 && unit > 0 ? 1 : 0)} ${units[unit]}`;
    },
    async download(attachment) {
      if (attachment.downloading) {
        return;
      }
      this.$set(attachment, 'downloading', true);
      try {
        const blobUrl = await this.$matrixService.getMediaBlobUrl(attachment.mxcUrl);
        if (!blobUrl) {
          this.$root.$emit('alert-message', this.$t('matrix.chat.file.no.available'), 'error');
          return;
        }
        const link = document.createElement('a');
        link.href = blobUrl;
        link.download = attachment.name || 'file';
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(blobUrl);
      } finally {
        this.$set(attachment, 'downloading', false);
      }
    },
    /**
     * Opens the Documents folder picker for this attachment and, on the folder the
     * user confirms, uploads it there. The whole flow — picker, upload, "See" toast —
     * lives in the service; here we only wire the loading state and the toast texts.
     *
     * @param {Object} attachment the attachment row to save
     * @returns {void}
     */
    saveInDocuments(attachment) {
      if (attachment.saving) {
        return;
      }
      this.$set(attachment, 'saving', true);
      this.$matrixService.saveAttachmentInDocuments(attachment, this.room, {
        success: this.$t('matrix.room.attachments.saveInDocuments.success'),
        error: this.$t('matrix.room.attachments.saveInDocuments.error'),
        see: this.$t('matrix.room.attachments.saveInDocuments.see'),
      })
        .catch(() => this.$root.$emit('alert-message', this.$t('matrix.room.attachments.saveInDocuments.error'), 'error'))
        .finally(() => this.$set(attachment, 'saving', false));
    },
    reset() {
      this.attachments = [];
      this.room = null;
    },
  },
};
</script>
