<template>
  <div
    :id="`message-content-${message.event_id}`"
    class="text-no-wrap max-width-fit">
    <a
      :href="blobUrl"
      :alt="fileName"
      class="d-flex text-decoration-none"
      :download="fileName">
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
    <div v-if="!blobUrl" class="red--text mt-1">
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
      fileIcon: null
    };
  },
  computed: {
    fileDownloadLink() {
      const url = this.message.content.url.replace('mxc://', '');
      return `/_matrix/client/v1/media/download/${url}?allow_redirect=true`;
    },
    fileName() {
      return this.message?.content?.body;
    }
  },
  created() {
    this.fileIcon = this.getFileIcon(this.message.content?.info?.mimetype);
  },
  mounted() {
    this.getDownloadUrl();
  },
  methods: {
    async getDownloadUrl() {
      this.blobUrl = await this.$matrixService.getMediaBlobUrl(this.fileDownloadLink);
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
