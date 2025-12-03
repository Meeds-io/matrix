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
  <div
    :id="`message-content-${message.event_id}`"
    class="text-no-wrap max-width-fit">
    <a
      ref="downloadLink"
      :alt="fileName"
      class="d-flex text-decoration-none"
      :download="fileName"
      @click.prevent="download">
      <div
        class="size-7 white rounded-circle d-flex justify-center me-3 text-decoration-none">
        <v-icon :size="16" :color="fileIcon.color">
          {{ fileIcon.class }}
        </v-icon>
      </div>
      <div class="message-file-name align-self-center text-truncate">
        {{ fileName }}
      </div>
    </a>
    <div v-if="isMediaDeleted" class="red--text mt-1">
      {{ $t('matrix.chat.file.no.available') }}
    </div>
  </div>
</template>

<script>

export default {
  props: {
    message: {
      type: Object,
      default: null
    }
  },
  data() {
    return {
      blobUrl: null,
      fileIcon: null,
      isDownloading: false
    };
  },
  computed: {
    isMediaDeleted() {
      return this.message?.mediaDeleted;
    },
    fileName() {
      return this.message?.content?.body;
    }
  },
  created() {
    this.fileIcon = this.getFileIcon(this.message.content?.info?.mimetype);
  },
  beforeDestroy() {
    if (this.blobUrl) {
      URL.revokeObjectURL(this.blobUrl);
    }
  },
  methods: {
    async download() {
      if (this.isDownloading || this.isMediaDeleted) {
        return;
      }
      this.isDownloading = true;

      try {
        const url = this.message.content.url;

        const blobUrl = await this.$matrixService.getMediaBlobUrl(url);

        if (!blobUrl) {
          this.isDownloading = false;
          return;
        }

        const link = document.createElement('a');
        link.href = blobUrl;
        link.download = this.fileName ?? 'file';

        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);

        if (this.blobUrl) {
          URL.revokeObjectURL(this.blobUrl);
        }
        this.blobUrl = blobUrl;

      } finally {
        this.isDownloading = false;
      }
    },
    getFileIcon(mimeType) {
      const extensions = Vue.prototype.$filesIconsExtension;
      let extension = extensions[0].get(mimeType);
      if (!extension) {
        extension = extensions[0].get('file');
      }
      return extension;
    }
  },
};
</script>
