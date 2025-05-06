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
  <v-card
    class="white pa-2 mb-2 border-radius-16 clickable"
    flat
    @click="goToMessageSource">
    <div class="d-flex flex-row">
      <div
        v-if="!isText"
        class="flex-column justify-center">
        <div
          v-if="isFile || isAudioMessage"
          class="size-7 background-grey-primary rounded-circle d-flex justify-center me-2">
          <v-icon
            :size="16"
            :class="{'icon-default-color': !isFile}"
            :color="isFile && fileIcon.color">
            {{ isFile && fileIcon.class || 'fas fa-microphone' }}
          </v-icon>
        </div>
        <div
          v-else-if="imageThumbnailURL"
          class="me-2">
          <v-img
            :src="imageThumbnailURL"
            :lazy-src="imageThumbnailURL"
            :max-height="36"
            :max-width="36"
            :aspect-ratio="thumbnailAspectRatio"
            contain />
        </div>
      </div>
      <div class="flex-column">
        <div class="flex-row">
          <quoted-message-user :user-id="targetMessage.targetUser" />
        </div>
        <v-sheet
          :max-width="isFile && 265 || 'auto'"
          class="flex-row">
          <div
            :class="{'text-truncate-3': isText, 'text-truncate': isFile}"
            class="text-color">
            {{ targetMessageBody }}
          </div>
        </v-sheet>
      </div>
    </div>
  </v-card>

</template>

<script>

export default {
  props: {
    message: {
      type: Object,
      default: null
    }
  },
  computed: {
    targetMessage() {
      return this.message?.replyTo;
    },
    targetMessageBody() {
      return this.targetMessage.body;
    },
    targetMessageType() {
      return this.targetMessage?.targetType;
    },
    isText() {
      return this.targetMessageType === 'm.text';
    },
    isAudioMessage() {
      return this.targetMessageType === 'm.audio' && !this.isUploadedAudioFile;
    },
    isUploadedAudioFile() {
      return this.targetMessage.isUploadedAudioFile;
    },
    isFile() {
      return this.targetMessageType === 'm.file' || this.isUploadedAudioFile;
    },
    thumbnailWidth() {
      return this.targetMessage?.targetThumbnailWidth;
    },
    thumbnailHeight() {
      return this.targetMessage?.targetThumbnailHeight;
    },
    thumbnailURL() {
      return this.targetMessage?.targetThumbnailURL;
    },
    url() {
      return this.targetMessage?.targetUrl;
    },
    thumbnailAspectRatio() {
      return this.thumbnailHeight && this.thumbnailWidth
                                  && this.thumbnailWidth / this.thumbnailHeight;
    },
    imageThumbnailURL() {
      const url = this.thumbnailURL || this.url;
      if (!url) {
        return null;
      }
      const imageId = url.replace(`mxc://${matrixServerName}/`, '');
      const basePath = this.thumbnailURL ? `/_matrix/media/v3/thumbnail` : `/_matrix/media/v3/download`;
      const params = this.thumbnailURL ? `?width=800&height=600&method=scale&allow_redirect=true` : `?allow_redirect=true`;

      return `${basePath}/${matrixServerName}/${imageId}${params}`;
    },
    fileMimeType() {
      return this.targetMessage?.fileMimeType;
    },
    fileIcon() {
      return this.isFile && this.getFileIcon(this.fileMimeType);
    }
  },
  methods: {
    goToMessageSource() {
      const targetId = `message-content-${this.targetMessage.targetEventId}`;
      const targetElement = document.getElementById(targetId).parentElement;
      if (targetElement) {
        targetElement.scrollIntoView({behavior: 'smooth', block: 'end'});
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
  }
};
</script>
