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
<div>
  <v-file-input
    ref="fileInput"
    accept=".png,.jpg,.jpeg,.webp,.gif,.bmp"
    class="d-none position-absolute"
    multiple
    hide-input
    @change="handleFileChange" />
  <v-menu
    v-model="menu"
    close-on-content-click
    content-class="mt-n2"
    close-delay="500"
    offset-y
    top>
    <template #activator="{ on, attrs }">
      <v-btn
        v-bind="attrs"
        icon
        :disabled="isUploading"
        v-on="on">
        <template v-if="isUploading">
          <v-progress-circular
            :indeterminate="uploadProgress >= 100"
            :value="uploadProgress < 100 ? uploadProgress : undefined"
            color="primary"
            size="36"
            width="2">
            <span
              class="text-caption">
              {{ uploadProgress }}%
            </span>
          </v-progress-circular>
        </template>
        <template v-else>
          <v-icon
            size="20"
            class="icon-default-color">
            fas fa-plus
          </v-icon>
        </template>
      </v-btn>
    </template>
    <v-list
      class="ma-0 py-0 text-no-wrap width-fit-content border-box-sizing border-radius">
      <v-list-item
        class="ma-0 height-auto px-3 py-2"
        @click="openFileExplorer">
        <v-list-item-icon class="me-1 ms-0 my-auto">
          <v-icon size="16">
            fa-solid fa-image
          </v-icon>
        </v-list-item-icon>
        <v-list-item-title>Image</v-list-item-title>
      </v-list-item>
    </v-list>
  </v-menu>
</div>
</template>

<script>

export default {
  data() {
    return {
      menu: false,
      file: null,
      maxUploadSize: null,
      isUploading: false,
      uploadProgress: 0,
      uploadedFiles: 0,
      ignoredFiles: 0,
      pasteTargetElement: null,
    };
  },
  props: {
    roomId: {
      type: String,
      default: null
    },
    pasteTarget: {
      type: String,
      default: null
    }
  },
  created() {
    this.getMaxUploadSize();
  },
  mounted() {
    this.addPasteEventListener()
  },
  beforeDestroy() {
    this.pasteTargetElement?.removeEventListener('paste', this.handlePaste);
  },
  methods: {
    getMaxUploadSize() {
      return this.$matrixService.getMaxUploadSize()
          .then(maxSize => this.maxUploadSize = maxSize)
          .catch(err => console.error('Error occurred:', err));
    },
    openFileExplorer() {
      this.$refs.fileInput.$el.querySelector('input').click();
    },
    async handleFileChange(files) {
      this.isUploading = true;

      let totalBytes = this.getTotalBytes(files);
      let uploadedBytes = 0;

      for (const file of files) {
        if (file.size > this.maxUploadSize) {
          this.ignoredFiles++;
          continue;
        }

        const image = new Image();
        image.src = URL.createObjectURL(file);
        await image.decode();

        try {
          const mxcUri = await this.$matrixService.uploadMatrixImage(file, (percent) => {
            const uploadPart = (percent / 100) * file.size;
            const combinedBytes = uploadedBytes + uploadPart;
            this.uploadProgress = Math.min(Math.round((combinedBytes / totalBytes) * 80), 80);
          });

          const payload = {
            msgtype: 'm.image',
            body: file.name,
            url: mxcUri,
            info: {
              mimetype: file.type,
              size: file.size,
              w: image.width,
              h: image.height,
            },
          };
          await this.$matrixService.sendMessage(payload, this.roomId);
          uploadedBytes += file.size;
          this.uploadProgress = Math.min(Math.round((uploadedBytes / totalBytes) * 100), 100);
          this.uploadedFiles += 1;
        } catch (error) {
          console.error('File upload failed:', error);
          this.$root.$emit('alert-message', this.$t('matrix.chat.upload.image.error.message', {0: this.ignoredFiles}), 'error');
        }
      }
      this.isUploading = false;
      if (this.ignoredFiles) {
        this.$root.$emit('alert-message', this.$t('matrix.chat.upload.image.ignored.message',
            {0: this.ignoredFiles, 1: this.maxUploadSize / (1024 * 1024)}), 'warning');
      }
      this.resetInput();
    },
    resetInput() {
      this.$refs.fileInput.$el.querySelector('input').value = '';
      this.uploadProgress = 0
      this.uploadedFiles = 0
      this.ignoredFiles = 0
    },
    handlePaste(event) {
      const files = [];
      for (const item of event.clipboardData.items) {
        if (item.type.startsWith('image/')) {
          const file = item.getAsFile();
          if (file) {
            files.push(file);
          }
        }
      }
      if (files.length) {
        event.preventDefault();
        this.handleFileChange(files);
      }
    },
    addPasteEventListener() {
      const pasteTarget = document.getElementById(this.pasteTarget);
      if (pasteTarget) {
        this.pasteTargetElement = pasteTarget;
        this.pasteTargetElement?.addEventListener('paste', this.handlePaste);
      }
    },
    getTotalBytes(files) {
      let totalBytes = 0;
      for (const file of files) {
        if (file.size > this.maxUploadSize) {
          continue;
        }
        totalBytes += file.size;
      }
      return totalBytes;
    }
  }
};
</script>
