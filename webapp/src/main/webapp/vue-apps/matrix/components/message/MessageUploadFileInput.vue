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
      :key="imagesOnly"
      :accept="accept"
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
          @click="openFileExplorer(true)">
          <v-list-item-icon class="me-1 ms-0 my-auto">
            <v-icon size="16">
              fa-solid fa-image
            </v-icon>
          </v-list-item-icon>
          <v-list-item-title>
            {{ $t('matrix.chat.image.label') }}
          </v-list-item-title>
        </v-list-item>
        <v-list-item
          class="ma-0 height-auto px-3 py-2"
          @click="openFileExplorer(false)">
          <v-list-item-icon class="me-1 ms-0 my-auto">
            <v-icon size="16">
              fas fa-paperclip
            </v-icon>
          </v-list-item-icon>
          <v-list-item-title>
            {{ $t('matrix.chat.attachment.label') }}
          </v-list-item-title>
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
      dropTargetElement: null,
      imagesOnly: false
    };
  },
  props: {
    room: {
      type: Object,
      default: null
    },
    pasteTarget: {
      type: String,
      default: null
    },
    dropTarget: {
      type: String,
      default: null
    }
  },
  created() {
    this.getMaxUploadSize();
  },
  mounted() {
    this.addPasteEventListener();
    this.addDropEventListener();
  },
  beforeDestroy() {
    this.pasteTargetElement?.removeEventListener('paste', this.handlePaste);
    this.dropTargetElement?.removeEventListener('dragover', this.handleDragOver);
    this.dropTargetElement?.removeEventListener('drop', this.handleDrop);
  },
  computed: {
    accept() {
      return this.imagesOnly && '.png,.jpg,.jpeg,.webp,.gif,.bmp' || '*/*';
    }
  },
  methods: {
    getMaxUploadSize() {
      return this.$matrixService.getMaxUploadSize()
        .then(maxSize => this.maxUploadSize = maxSize)
        .catch(err => console.error('Error occurred:', err));
    },
    openFileExplorer(imagesOnly) {
      this.imagesOnly = imagesOnly;
      this.$nextTick(() => {
        this.$refs.fileInput?.$el?.querySelector('input')?.click();
      });
    },
    async handleFileChange(files) {
      this.isUploading = true;

      const totalBytes = this.getTotalBytes(files);
      let uploadedBytes = 0;

      for (const file of files) {
        if (file.size > this.maxUploadSize) {
          this.ignoredFiles++;
          continue;
        }

        const { msgtype, info } = await this.extractFileMetadata(file);

        try {
          const mxcUri = await this.$matrixService.uploadMatrixFile(file, (percent) => {
            const uploadPart = (percent / 100) * file.size;
            const combinedBytes = uploadedBytes + uploadPart;
            this.uploadProgress = Math.min(Math.round((combinedBytes / totalBytes) * 80), 80);
          });

          const payload = {
            msgtype,
            body: file.name,
            url: mxcUri,
            info
          };

          await this.$matrixService.sendMessage(payload, this.room.id);
          uploadedBytes += file.size;
          this.uploadProgress = Math.min(Math.round((uploadedBytes / totalBytes) * 100), 100);
          this.uploadedFiles += 1;
          this.sendMessageStatistics(payload);
        } catch (error) {
          console.error('File upload failed:', error);
          this.$root.$emit('alert-message', this.$t('matrix.chat.upload.image.error.message', {0: this.ignoredFiles}), 'error');
        }
      }
      this.$emit('file-sent');
      this.isUploading = false;
      if (this.ignoredFiles) {
        this.$root.$emit('alert-message', this.$t('matrix.chat.upload.image.ignored.message',
          {0: this.ignoredFiles, 1: this.maxUploadSize / (1024 * 1024)}), 'warning');
      }
      this.resetInput();
    },
    resetInput() {
      this.$refs.fileInput.$el.querySelector('input').value = '';
      this.uploadProgress = 0;
      this.uploadedFiles = 0;
      this.ignoredFiles = 0;
    },
    handlePastePlainText(text) {
      const selection = window.getSelection();
      if (!selection.rangeCount) {
        return;
      }

      const range = selection.getRangeAt(0);
      range.deleteContents();

      const textNode = document.createTextNode(text);
      range.insertNode(textNode);

      range.setStartAfter(textNode);
      range.setEndAfter(textNode);
      selection.removeAllRanges();
      selection.addRange(range);
      this.pasteTargetElement.dispatchEvent(new Event('input', {bubbles: true}));
    },
    handlePaste(event) {
      const text = event.clipboardData.getData('text/plain');
      if (text) {
        event.preventDefault();
        this.handlePastePlainText(text);
        return;
      }
      const files = [];
      for (const item of event.clipboardData.items) {
        const file = item.getAsFile();
        if (file) {
          files.push(file);
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
    },
    async extractFileMetadata(file) {
      const type = file.type;
      let msgtype = 'm.file';
      const info = {
        mimetype: type,
        size: file.size
      };
      try {
        if (type.startsWith('image/')) {
          msgtype = 'm.image';
          const image = new Image();
          image.src = URL.createObjectURL(file);
          await image.decode();
          info.w = image.width;
          info.h = image.height;
        } else if (type.startsWith('video/')) {
          msgtype = 'm.video';
          const video = document.createElement('video');
          video.preload = 'metadata';
          video.src = URL.createObjectURL(file);
          await new Promise((resolve, reject) => {
            video.onloadedmetadata = () => {
              info.w = video.videoWidth;
              info.h = video.videoHeight;
              info.duration = Math.round(video.duration * 1000);
              resolve();
            };
            video.onerror = reject;
          });
        } else if (type.startsWith('audio/')) {
          msgtype = 'm.audio';
          info.uAudio = true;
          const audio = document.createElement('audio');
          audio.preload = 'metadata';
          audio.src = URL.createObjectURL(file);
          await new Promise((resolve, reject) => {
            audio.onloadedmetadata = () => {
              info.duration = Math.round(audio.duration * 1000);
              resolve();
            };
            audio.onerror = reject;
          });
        }
      } catch (e) {
        console.warn(`Failed to extract metadata for ${file.name}:`, e);
      }
      return {msgtype, info};
    },
    addDropEventListener() {
      const dropTarget = document.getElementById(this.dropTarget);
      if (dropTarget) {
        this.dropTargetElement = dropTarget;
        this.dropTargetElement?.addEventListener('dragover', this.handleDragOver);
        this.dropTargetElement?.addEventListener('drop', this.handleDrop);
      }
    },
    handleDragOver(event) {
      event.preventDefault();
    },
    handleDrop(event) {
      event.preventDefault();

      const data = event.dataTransfer;
      if (!data || !data.files) {
        return;
      }
      const files = Array.from(data.files);
      if (files.length) {
        this.handleFileChange(files);
      }
    },
    sendMessageStatistics(message) {
      if (message?.info?.uAudio) {
        message.msgtype = 'u.audio';
      }
      this.$root.$emit('message-sent-statistics', message, this.room);
    }
  }
};
</script>
