/*
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
*/

/**
 * Opening a chat attachment, shared by the conversation's file messages and the rows
 * of the room attachments drawer, so a click means the same thing in both places, the
 * same way the email connector shares one behaviour across the mail list and drawer.
 *
 * Three outcomes, the way email opens a received attachment: an image opens in the
 * platform preview dialog straight from its URL, without copying anything; a document
 * OnlyOffice can render opens read only in the editor (when the Documents add-on is
 * installed); anything else — and the case where Documents is absent — downloads. The
 * attachment passed to each method is the shape loadRoomAttachments returns:
 * { eventId, name, mxcUrl, mimetype }.
 */
export default {
  methods: {
    /**
     * Opens the attachment the way email does: an image previews, a document OnlyOffice
     * can render opens read only in the editor, anything else downloads.
     *
     * @param {Object} attachment the attachment that was clicked
     * @returns {void}
     */
    openChatAttachment(attachment) {
      if (!attachment || attachment.opening || attachment.downloading) {
        return;
      }
      if (this.isChatImageAttachment(attachment)) {
        this.previewChatImage(attachment);
        return;
      }
      if (this.$matrixService.isDocumentsDeployed() && this.$matrixService.isEditorPreviewable(attachment)) {
        this.openChatAttachmentInEditor(attachment, 'view');
        return;
      }
      this.downloadChatAttachment(attachment);
    },
    /**
     * Whether the attachment is an image, hence previewable from its URL alone.
     *
     * @param {Object} attachment the attachment
     * @returns {Boolean} true for an image
     */
    isChatImageAttachment(attachment) {
      return (attachment?.mimetype || '').toLowerCase().startsWith('image/');
    },
    /**
     * Opens an image in the platform preview dialog straight from its URL, nothing is
     * copied into the Drive. Reuses the very dialog and event the conversation's inline
     * images already open with, so the two behave the same.
     *
     * @param {Object} attachment the image attachment to preview
     * @returns {void}
     */
    previewChatImage(attachment) {
      const imageId = (attachment.mxcUrl || '').replace(`mxc://${matrixServerName}/`, '');
      if (!imageId) {
        return;
      }
      const downloadUrl = `/_matrix/media/v3/download/${matrixServerName}/${imageId}?allow_redirect=true`;
      document.dispatchEvent(new CustomEvent('open-attachments-preview', {
        detail: {
          id: imageId,
          attachments: [{
            id: imageId,
            name: attachment.name || '',
            filename: attachment.name || '',
            size: attachment.size || 0,
            mimetype: attachment.mimetype || '',
            alt: attachment.name || '',
            thumbnailUrl: downloadUrl,
            downloadUrl,
          }],
        },
      }));
    },
    /**
     * Stores the attachment in the Drive and hands the document to OnlyOffice. Read
     * only on purpose — the editor needs a document to address, so a throwaway copy is
     * stored under Chat Attachments/Received just to open it; editing means Save in
     * Documents first, into a folder the user picked. The tab is opened on the click
     * itself, before the (asynchronous) store, so the browser does not treat the later
     * navigation as a blocked pop-up.
     *
     * @param {Object} attachment the attachment to open
     * @param {String} mode 'view' to open read only, editable when absent
     * @returns {void}
     */
    openChatAttachmentInEditor(attachment, mode) {
      if (attachment.opening) {
        return;
      }
      this.$set(attachment, 'opening', true);
      const editorTab = window.open('', '_blank');
      this.showChatAttachmentOpeningPlaceholder(editorTab, attachment);
      this.$matrixService.materialiseChatAttachment(attachment)
        .then(documentId => {
          const url = this.$matrixService.getEditorUrl(documentId, mode);
          if (editorTab) {
            editorTab.location = url;
          } else {
            window.location.href = url;
          }
        })
        .catch(() => {
          if (editorTab) {
            editorTab.close();
          }
          this.$root.$emit('alert-message', this.$t('matrix.room.attachments.preview.error'), 'error');
          this.downloadChatAttachment(attachment);
        })
        .finally(() => this.$set(attachment, 'opening', false));
    },
    /**
     * Downloads the attachment to the device, fetching its bytes the authenticated way.
     *
     * @param {Object} attachment the attachment to download
     * @returns {Promise} resolved once the download has been triggered
     */
    async downloadChatAttachment(attachment) {
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
     * Fills the freshly opened tab with a spinner while the document is being stored,
     * so the gap between the click and the editor is not a blank white tab.
     *
     * @param {Window} editorTab the tab opened for the editor, null when pop-ups blocked
     * @param {Object} attachment the attachment being opened
     * @returns {void}
     */
    showChatAttachmentOpeningPlaceholder(editorTab, attachment) {
      if (!editorTab) {
        return;
      }
      // the name comes from a chat message, so it is not to be trusted as markup
      const name = document.createElement('div');
      name.textContent = attachment.name;
      const label = this.$t('matrix.room.attachments.opening', { 0: name.innerHTML });
      editorTab.document.write(`<!doctype html>
<html><head><meta charset="utf-8"><title>${name.innerHTML}</title></head>
<body style="margin:0;height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:16px;font-family:Helvetica,Arial,sans-serif;color:#4d5466;background:#fff">
<div style="width:36px;height:36px;border:3px solid #e1e8ee;border-top-color:#476a9c;border-radius:50%;animation:s 1s linear infinite"></div>
<div>${label}</div>
<style>@keyframes s{to{transform:rotate(360deg)}}</style>
</body></html>`);
      editorTab.document.close();
    },
  },
};
