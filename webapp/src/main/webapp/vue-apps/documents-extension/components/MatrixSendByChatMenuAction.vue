<!--
 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2026 Meeds Association contact@meeds.io

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
  <document-action-item
    icon="fa fa-comments"
    :label="$t('documents.label.sendByChat')"
    @click="sendByChat" />
</template>
<script>
export default {
  props: {
    file: {
      type: Object,
      default: null,
    }
  },
  methods: {
    /**
     * Sends this document into a conversation as a LINK, not as a copy.
     * <p>
     * Uploading the bytes would hand the content to everyone in the room whatever
     * the document's own permissions say, and the copy would go stale the moment
     * somebody edited the original. A link keeps both where they belong: the
     * server checks the reader's access when they follow it, and what they open
     * is the current version. It also means no upload cap — a large document is
     * shared as easily as a small one.
     *
     * @returns {void}
     */
    sendByChat() {
      if (!this.file) {
        return;
      }
      // The same address the Documents menu's own "Copy link" hands out, chosen
      // the same way: a file an editor understands opens straight in that editor
      // — in edit mode when the reader may edit, read-only otherwise — and
      // anything else opens in its folder, previewed. Built through Documents'
      // own helpers rather than assembled here, so it follows whatever that app
      // decides a document's address is.
      //
      // Not the download URL, which is short-lived: a link reading "access
      // expired" by the time somebody opens the conversation is worse than no
      // link. And not a URL read off the node — a document node carries none,
      // which is how this first shipped a link to /undefined.
      const url = encodeURI(`${window.location.origin}${this.documentPath()}`);
      document.dispatchEvent(new CustomEvent('meeds-chat-share', {
        detail: {link: {url, title: this.file.name}},
      }));
    },
    /**
     * Where this document lives, as a path: the editor when an editor handles
     * its type, its folder with the file previewed when none does.
     * <p>
     * The reader's own rights still decide what they get — the mode here only
     * says what the SENDER could do, and the editor re-checks on open.
     *
     * @returns {String} the portal path to link to
     */
    documentPath() {
      const supported = this.$supportedDocuments || [];
      const editable = supported.some(type => type.edit && type.mimeType === this.file.mimeType);
      const readable = supported.some(type => type.mimeType === this.file.mimeType);
      if (editable && this.file?.acl?.canEdit) {
        // No mode means edit: getEditorUrl only appends &mode= when one is given.
        return this.$documentsUtils.getEditorUrl(this.file, null);
      } else if (editable || readable) {
        return this.$documentsUtils.getEditorUrl(this.file, 'view');
      }
      return `${this.$documentsUtils.getParentFolderUrl(this.file)}?documentPreviewId=${this.file.id}`;
    },
  },
};
</script>
