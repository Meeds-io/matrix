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
  <div class="chat-message-content-body py-2 px-3"
    :class="[messageContentClass, {'mt-0-5':!displaySender}]"
    :style="isImage && {
              'background-image': 'url(' + imageThumbnailURL(message) + ')',
              'background-size': 'contain',
              'height': imageThumbnailMaxHeight + 'px',
              'width': imageThumbnailMaxWidth + 'px',
              'cursor': 'pointer',
             }"
    @click="isImage && openImagePreview(message)">
    <div
      v-if="isText"
      :id="`message-content-${message.event_id}`"
      :key="message.event_id"
      class="chat-message-content-text"
      v-sanitized-html="formattedMessage" >
    </div>
    <div
      v-if="isImage"
      :id="`message-content-${message.event_id}`"
      :key="message.event_id">
    </div>
    <audio-message
      v-if="isAudio"
      :message="message"
      :next-message="nextMessage" />
    <v-tooltip bottom>
      <template #activator="{on, bind}">
        <div v-on="on"
           v-bind="bind"
           v-show="displayTimestamp"
           class="text-font-small-size chat-message-content-timestamp">
             <v-chip v-if="isImage" x-small class="text-font-small-size pa-1 chat-message-timestamp-chip">
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
      }
    },
    data() {
      return {
        defaultThumbnailMaxWidth: 250,
        defaultThumbnailMaxHeight: 250,
      };
    },
    computed: {
      isImage() {
        return this.message.content.msgtype === 'm.image';
      },
      isText() {
        return this.message.content.msgtype === 'm.text';
      },
      isAudio() {
        return this.message.content.msgtype === 'm.audio';
      },
      formattedMessage() {
        let formatMessage = this.message.content.format === 'org.matrix.custom.html'
                            && this.message.content.formatted_body
                            || this.message.content.body.replace(/\n/g, '<br />')
                            || '';
        return this.$matrixService.formatMentionsInMessage(formatMessage);
      },
      imageThumbnailMaxWidth() {
        const width = this.message.content.info.w || this.message.content.w;
        const height = this.message.content.info.h || this.message.content.h;
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
        return this.message.content.info.w / this.message.content.info.h;
      },
    },
    methods: {
      imageThumbnailURL(message) {
        if(message.content?.info?.thumbnail_url) {
          const imageId = message.content?.info?.thumbnail_url.replace(`mxc://${matrixServerName}/`,'');
          return `/_matrix/media/v3/thumbnail/${matrixServerName}/${imageId}?width=800&height=600&method=scale&allow_redirect=true`;
        } else {
          const imageId = message.content?.url.replace(`mxc://${matrixServerName}/`,'');
          return `/_matrix/media/v3/download/${matrixServerName}/${imageId}?allow_redirect=true`
        }
      },
      imageId(message) {
        return message.content?.info?.thumbnail_url.replace(`mxc://${matrixServerName}/`,'');
      },
      openImagePreview(message) {
        const imageId = message.content?.info?.thumbnail_url && message.content?.info?.thumbnail_url.replace(`mxc://${matrixServerName}/`,'') || message.content?.url?.replace(`mxc://${matrixServerName}/`,'');
        const images = [{
          id: imageId,
          name: message.content.body,
          filename: message.content.body,
          size: message.content.info.size,
          mimetype: message.content.info.mimetype,
          updated: message.origin_server_ts,
          alt: message.content.body,
          thumbnailUrl: this.imageThumbnailURL(message),
          downloadUrl: `/_matrix/media/v3/download/${matrixServerName}/${imageId}`,
        }];
        document.dispatchEvent(new CustomEvent('open-attachments-preview', {detail: {'attachments': images || [],'id': imageId }}));
      }
    }
  }
</script>
