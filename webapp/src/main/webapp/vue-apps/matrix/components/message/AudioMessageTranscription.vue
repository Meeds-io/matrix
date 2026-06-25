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
    v-if="transcriptionAgentNameId || transcription || transcribing"
    :class="isSelfMessage ? 'white--text' : 'text-sub-title'"
    class="full-width">
    <v-btn
      v-if="transcriptionAgentNameId && !transcription && !transcribing"
      :aria-label="$t('matrix.chat.audio.transcribe.label')"
      :title="$t('matrix.chat.audio.transcribe.label')"
      :class="isSelfMessage ? 'white--text' : 'icon-default-color'"
      x-small
      text
      class="px-1 text-caption text-none"
      @click="transcribe">
      <v-icon
        size="14"
        class="me-1"
        :color="isSelfMessage ? 'white' : ''">
        fa-closed-captioning
      </v-icon>
      {{ $t('matrix.chat.audio.transcribe.label') }}
    </v-btn>
    <div
      v-else-if="transcribing"
      class="d-flex align-center text-caption mt-1">
      <v-progress-circular
        indeterminate
        size="14"
        width="2"
        class="me-2" />
      {{ $t('matrix.chat.audio.transcribing.label') }}
    </div>
    <div
      v-else
      class="text-caption rounded pa-2 mt-1">
      <v-icon
        size="12"
        class="me-1"
        :color="isSelfMessage ? 'white' : ''">
        fa-closed-captioning
      </v-icon>
      <span class="font-italic">{{ transcription }}</span>
    </div>
  </div>
</template>
<script>
// Resolve (once per session, shared by all audio messages) the transcription agent
// the current user is allowed to use. This is the matrix-owned "chatMessage-transcription"
// binding, configured by admins under Chat message in the AI UX-binding administration
// (Activate Voice transcription + agent picker). getUxBindings is permission-filtered
// server-side and excludes disabled bindings, so a returned binding means the admin
// enabled it AND its transcription agent exists and is accessible to the current user.
let transcriptionAgentPromise;
function resolveTranscriptionAgentNameId(vm) {
  if (!transcriptionAgentPromise) {
    transcriptionAgentPromise = new Promise(resolve => window.require(['SHARED/AiAgentCommon'], resolve))
      .then(() => vm.$aiUxBindingService.getUxBindings('chat', 'chatMessage-transcription'))
      .then(bindings => bindings?.[0]?.agentNameId || null)
      .catch(() => null);
  }
  return transcriptionAgentPromise;
}

export default {
  props: {
    message: {
      type: Object,
      default: null,
    },
    audioSrc: {
      type: String,
      default: null,
    },
    isSelfMessage: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    transcribing: false,
    transcription: null,
    transcriptionAgentNameId: null,
  }),
  computed: {
    // A previously computed transcription is cached per message so it is shown again
    // when coming back to the conversation, without having to transcribe a second time.
    cacheKey() {
      return this.message?.event_id ? `matrix-transcript-${this.message.event_id}` : null;
    },
  },
  created() {
    if (this.cacheKey) {
      const cached = window.localStorage.getItem(this.cacheKey);
      if (cached !== null) {
        this.transcription = cached || this.$t('matrix.chat.audio.transcribe.empty');
      }
    }
    // Only the AI addon, when deployed and enabled, exposes the transcription binding.
    if (eXo.env.portal.aiConciergeEnabled) {
      resolveTranscriptionAgentNameId(this).then(agentNameId => this.transcriptionAgentNameId = agentNameId);
    }
  },
  methods: {
    async transcribe() {
      if (!this.transcriptionAgentNameId) {
        return;
      }
      this.transcribing = true;
      try {
        await new Promise(resolve => window.require(['SHARED/AiAgentCommon'], resolve));
        // Download the voice message audio, upload it to the platform, then transcribe it.
        // Matrix uses authenticated media: the legacy /_matrix/media/v3/download endpoint
        // returns 404, so hit /_matrix/client/v1/media/download with the user's token
        // (same Bearer the rest of the chat front-end uses). A bearer token also avoids
        // the wildcard-CORS-with-credentials rejection.
        const downloadUrl = this.audioSrc?.replace('/_matrix/media/v3/download/', '/_matrix/client/v1/media/download/');
        const response = await fetch(downloadUrl, {
          headers: {
            'Authorization': `Bearer ${localStorage.getItem('matrix_access_token')}`,
          },
        });
        if (!response.ok) {
          throw new Error(`Media download failed: ${response.status}`);
        }
        const blob = await response.blob();
        const file = new File([blob], `voice-${this.message?.event_id || 'message'}`, {
          type: blob.type || 'audio/ogg',
        });
        const uploadId = `audio-${(this.message?.event_id || '').replace(/[^a-zA-Z0-9]/g, '')}-${Date.now()}`;
        await this.$uploadService.upload(file, uploadId);
        await this.waitForUpload(uploadId);
        const duration = Math.round((this.message?.content?.info?.duration || 0) / 1000);
        const text = await this.$aiAgentTranscriptionService.transcriptVoice({
          uploadId,
          agentNameId: this.transcriptionAgentNameId,
          duration,
        });
        const transcript = text?.trim?.() || '';
        this.transcription = transcript || this.$t('matrix.chat.audio.transcribe.empty');
        // Persist so the transcription is restored next time the conversation is opened.
        if (this.cacheKey) {
          window.localStorage.setItem(this.cacheKey, transcript);
        }
      } catch (e) {
        this.$root.$emit('alert-message', this.$t('matrix.chat.audio.transcribe.error'), 'error');
      } finally {
        this.transcribing = false;
      }
    },
    waitForUpload(uploadId) {
      return new Promise((resolve, reject) => {
        let attempts = 0;
        const poll = () => {
          this.$uploadService.getUploadProgress(uploadId)
            .then(percent => {
              if (Number(percent) >= 100) {
                resolve();
              } else if (++attempts > 100) {
                reject(new Error('Upload timed out'));
              } else {
                window.setTimeout(poll, 200);
              }
            })
            .catch(reject);
        };
        poll();
      });
    },
  },
};
</script>
