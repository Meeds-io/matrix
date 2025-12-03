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
  <v-sheet
    :class="[
      cssClass,
      {'mt-0-5':!displaySender},
      {'px-3 py-2':!isImage},
      {
        'my-message-text': isSelfMessage && !isImage,
        'others-message-text': !isSelfMessage && !isImage
      },
      {'clickable overflow-hidden': isImage}]"
    :height="isImage && imageThumbnailMaxHeight || undefined"
    :width="messageContentWidth"
    :max-width="expanded && messageMaxWidth || undefined"
    class="chat-message-content-body text-break"
    @click="isImage && openImagePreview(message)">
    <matrix-message-reply-quote
      v-if="message?.replyTo && !isRedacted"
      :message="message"
      :room="room"
      class="mb-2" />
    <div
      v-if="isText"
      :id="`message-content-${message.event_id}`"
      :key="message.event_id"
      v-sanitized-html="formattedMessage">
    </div>
    <div
      v-if="isRedacted"
      :id="`message-content-${message.event_id}`"
      :key="`${message.event_id}-${message.redacted_because.redacts}`"
      class="d-flex flex-no-wrap">
      <v-icon
        size="16"
        class="ma-auto me-2">
        fas fa-trash
      </v-icon>
      <div
        :title="$t('matrix.chat.message.deleted')"
        class="text-truncate">
        {{ $t('matrix.chat.message.deleted') }}
      </div>
    </div>
    <div
      v-if="isImage"
      :id="`message-content-${message.event_id}`"
      :key="message.event_id"
      @click="openImagePreview(message)"
      @focus="hover = true"
      @blur="hover = false"
      @mouseenter="hover = true"
      @mouseleave="hover = false">
      <v-img
        :src="imageThumbnailURL"
        :alt="message.content.body"
        :width="imageThumbnailMaxWidth"
        :height="imageThumbnailMaxHeight"
        :aspect-ratio="imageRatio"
        class="position-absolute" />
      <div v-if="isGifImage" class="position-absolute transparent border-radius ms-2 mt-2">
        <v-chip
          label
          small>
          GIF
        </v-chip>
      </div>
    </div>
    <matrix-file-message
      v-if="isFile"
      :message="message" />
    <matrix-audio-message
      v-if="isAudioMessage"
      :id="`message-content-${message.event_id}`"
      :key="message.event_id"
      :message="message"
      :expanded="expanded"
      :container-max-width="messageMaxWidth"
      :next-message="nextMessage" />
    <div class="d-flex full-width justify-end">
      <v-tooltip
        v-if="message?.edited"
        open-delay="800"
        bottom>
        <template #activator="{on, bind}">
          <div
            v-on="on"
            v-bind="bind"
            class="text-font-small-size chat-message-content-timestamp">
            <span>
              {{ $t('matrix.message.edited.label') }}
            </span>
          </div>
        </template>
        <date-format
          :value="message.updatedAt"
          :format="dateTimeFormat" />
      </v-tooltip>
      <v-icon
        v-if="message?.edited && displayTimestamp"
        size="3"
        :class="{'text-color': !isSelfMessage, 'white--text': isSelfMessage }"
        class="ms-2 me-2 align-center">
        fas fa-circle
      </v-icon>
      <v-tooltip
        open-delay="800"
        bottom>
        <template #activator="{on, bind}">
          <div
            v-on="on"
            v-bind="bind"
            v-show="displayTimestamp"
            class="text-font-small-size chat-message-content-timestamp">
            <v-chip
              v-if="isImage"
              class="text-font-small-size pa-1 ma-1 chat-message-timestamp-chip"
              x-small>
              {{ timestamp }}
            </v-chip>
            <div v-else>
              {{ timestamp }}
            </div>
          </div>
        </template>
        <date-format
          :value="message.origin_server_ts"
          :format="dateFormat" />
      </v-tooltip>
    </div>
  </v-sheet>
</template>

<script>
export default {
  props: {
    message: {
      type: Object,
      default: {},
    },
    displaySender: {
      type: Boolean,
      default: false
    },
    displayTimestamp: {
      type: Boolean,
      default: false
    },
    timestamp: {
      type: String,
      default: false
    },
    nextMessage: {
      type: Object,
      default: null
    },
    isSelfMessage: {
      type: Boolean,
      default: false
    },
    cssClass: {
      type: String,
      default: ''
    },
    room: {
      type: Object,
      default: null
    },
    isRedacted: {
      type: Boolean,
      default: false
    }
  },
  data() {
    return {
      collapsedImageSize: 250,
      expandedImageMaxHeight: 500,
      hover: false,
      defaultSize: false,
      dateTimeFormat: {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
      },
    };
  },
  watch: {
    hover() {
      this.defaultSize = !!(this.hover && this.isAnimatedImage);
    },
  },
  computed: {
    defaultThumbnailMaxWidth() {
      return this.expanded ?  this.parentWidth * 0.5  : this.collapsedImageSize;
    },
    defaultThumbnailMaxHeight() {
      return this.expanded ? this.expandedImageMaxHeight : this.collapsedImageSize;
    },
    expanded() {
      return this.$root?.fullPageMode;
    },
    parentWidth() {
      return this.$root?.fullPageMessagesContainerWidth;
    },
    messageMaxWidth() {
      return this.parentWidth * 0.8;
    },
    messageContentWidth() {
      if (this.isAudio && this.expanded) {
        return this.messageMaxWidth;
      }
      return this.isImage ? this.imageThumbnailMaxWidth  : undefined;
    },
    isImage() {
      return this.message.content.msgtype === 'm.image';
    },
    isText() {
      return this.message.content.msgtype === 'm.text';
    },
    isVideo() {
      return this.message?.content?.msgtype === 'm.video';
    },
    isAudio() {
      return this.message?.content?.msgtype === 'm.audio';
    },
    isAudioMessage() {
      return this.isAudio && !this.isUploadedAudioFile;
    },
    isUploadedAudioFile() {
      return this.isAudio && (!this.message?.content?.['org.matrix.msc3245.voice']
                            && !this.message?.content?.['org.matrix.msc2516.voice']);
    },
    isFile() {
      return this.message.content.msgtype === 'm.file' || this.isUploadedAudioFile || this.isVideo;
    },
    isAnimatedImage() {
      return this.message.content?.info?.mimetype === 'image/gif' || this.message.content?.info?.mimetype === 'image/webp';
    },
    isGifImage() {
      return this.message.content?.info?.mimetype === 'image/gif';
    },
    formattedMessage() {
      return this.message?.formattedMessage;
    },
    imageThumbnailMaxWidth() {
      const width = this.message.content.info.w || this.message.content.w;
      const height = this.message.content.info.h || this.message.content.h;
      if (width <= 60) {
        return 60;
      }
      if (this.message.content.info.w < this.defaultThumbnailMaxWidth) {
        if (this.message.content.info.h < this.defaultThumbnailMaxHeight) {
          return this.message.content.info.w;
        } else {
          return this.defaultThumbnailMaxHeight / (height / width);
        }
      } else if (this.message.content.info.w >= this.message.content.info.h) {
        return this.defaultThumbnailMaxWidth;
      } else {
        return this.defaultThumbnailMaxHeight / (height / width);
      }
    },
    imageThumbnailMaxHeight() {
      const width = this.message.content.info.w || this.message.content.w;
      const height = this.message.content.info.h || this.message.content.h;
      if (height <= 60) {
        return 60;
      }
      if (this.message.content.info.h < this.defaultThumbnailMaxHeight){
        if (this.message.content.info.w < this.defaultThumbnailMaxWidth) {
          return this.message.content.info.h;
        } else {
          return this.defaultThumbnailMaxWidth / (width / height);
        }
      } else if (this.message.content.info.w >= this.message.content.info.h) {
        return this.defaultThumbnailMaxWidth / (width / height);
      } else {
        return this.defaultThumbnailMaxHeight;
      }
    },
    imageRatio() {
      const w = this.message.content.info?.w || this.message.content.w || 1;
      const h = this.message.content.info?.h || this.message.content.h || 1;
      return w / h;
    },
    imageThumbnailURL() {
      if (this.message.content?.info?.thumbnail_url && !this.defaultSize && !this.isSmallImage(this.message)) {
        const imageId = this.message.content?.info?.thumbnail_url.replace(`mxc://${matrixServerName}/`,'');
        return `/_matrix/media/v3/thumbnail/${matrixServerName}/${imageId}?width=800&height=600&method=scale&allow_redirect=true`;
      } else {
        const imageId = this.message.content?.url.replace(`mxc://${matrixServerName}/`,'');
        return `/_matrix/media/v3/download/${matrixServerName}/${imageId}?allow_redirect=true`;
      }
    },
  },
  methods: {
    isSmallImage(message) {
      return message.content?.info?.size < 1000000;
    },
    imageDownloadURL(message) {
      const imageId = message.content?.url.replace(`mxc://${matrixServerName}/`,'');
      return `/_matrix/media/v3/download/${matrixServerName}/${imageId}?allow_redirect=true`;
    },
    openImagePreview(message) {
      if (!message?.content) {
        return;
      }

      const content = message.content;
      const info = content.info || {};
      const imageId =
          (info.thumbnail_url || content.url)?.replace(`mxc://${matrixServerName}/`, '') || '';

      if (!imageId) {
        return;
      }

      const images = [{
        id: imageId,
        name: content.body || '',
        filename: content.body || '',
        size: info.size || 0,
        mimetype: info.mimetype || '',
        updated: message.origin_server_ts,
        alt: content.body || '',
        thumbnailUrl: this.imageDownloadURL(message),
        downloadUrl: this.imageDownloadURL(message),
      }];

      document.dispatchEvent(
        new CustomEvent('open-attachments-preview', {
          detail: { attachments: images, id: imageId },
        })
      );
    }
  }
};
</script>
