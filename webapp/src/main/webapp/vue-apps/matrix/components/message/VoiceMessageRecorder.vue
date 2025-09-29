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
  <div class="d-flex width-full">
    <v-btn
      :aria-label="$t('matrix.chat.label.cancelRecording')"
      class="me-2 mt-1 d-flex flex-column justify-end"
      icon
      @click="cancel">
      <v-icon
        size="20"
        class="icon-default-color">
        fas fa-times
      </v-icon>
    </v-btn>
    <div class="background-grey-primary border-radius-16 flex-grow-1 d-flex no-min-width">
      <v-btn
        :title="recordingLabel"
        :aria-label="recordingLabel"
        class="white my-auto ms-2"
        width="28"
        height="28"
        min-width="28"
        icon
        @click="toggleRecording">
        <v-icon
          class="icon-default-color"
          size="12">
          <template v-if="isRecording">
            fas fa-stop
          </template>
          <template v-else-if="isPlaying && !isPlaybackPaused">
            fas fa-pause
          </template>
          <template v-else>
            fas fa-play
          </template>
        </v-icon>
      </v-btn>
      <span
        class="mx-2 text-caption my-auto flex-grow-0 text-sub-title">
        {{ formatTime(isRecording ? recordingTime : currentTime) }}
      </span>
      <canvas
        ref="waveform"
        class="my-auto flex-shrink-1 d-flex no-min-width me-4 full-width"
        style="height: 30px;"
        height="30"/>
    </div>
    <div class="d-flex flex-column justify-end">
      <v-btn
        :disabled="!canSendVoice"
        :aria-label="$t('meeds.chat.send.message')"
        class="ms-2 mb-0_5"
        icon
        @click="sendVoiceMessage">
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
            color="primary"
            size="20">
            fa-paper-plane
          </v-icon>
        </template>
      </v-btn>
    </div>
  </div>
</template>

<script>

export default {
  data() {
    return {
      isUploading: false,
      mediaRecorder: null,
      audioChunks: [],
      isRecording: true,
      isStopped: false,
      audioBlob: null,
      recordingTime: 0,
      timer: null,
      analyser: null,
      source: null,
      audioCtx: null,
      animationFrame: null,
      samples: [],
      previewSamples: [],
      audioPlayer: null,
      isPlaying: false,
      isPlaybackPaused: false,
      uploadProgress: 0,
      startTimestamp: 0,
      endTimestamp: 0,
      recordDuration: 0,
      maxUploadSize: null,
      currentTime: 0,
    };
  },
  props: {
    room: {
      type: Object,
      default: null
    },
    expanded: {
      type: Boolean,
      default: false
    },
    drawerWidth: {
      type: Number,
      default: null
    }
  },
  created() {
    this.getMaxUploadSize()
  },
  computed: {
    canSendVoice() {
      return this.isRecording || this.audioBlob;
    },
    canvasWidth() {
      if (this.$root.fullPageMode) {
        return this.$root.fullPageMessagesContainerWidth;
      }
      return this.expanded && this.drawerWidth * 0.421 || 150;
    },
    recordingLabel() {
      return this.isRecording
        ? this.$t('matrix.chat.audio.stop.label')
        : this.isPlaying && !this.isPlaybackPaused
          ? this.$t('matrix.chat.audio.pause.label')
          : this.$t('matrix.chat.audio.play.label');
    }
  },
  watch: {
    expanded() {
      this.$nextTick(() => {
        cancelAnimationFrame(this.animationFrame);
        let drawSamples = this.samples;
        if (!this.isRecording) {
          const width = this.$refs.waveform?.width || 150;
          drawSamples = this.downsample(this.samples, width);
        }
        requestAnimationFrame(() => {
          if (this.isRecording) {
            this.drawWaveform();
          } else if (this.isPlaying) {
            this.animatePlayback(drawSamples);
          } else {
            this.drawStaticWaveform(drawSamples);
          }
        });
      });
    }
  },
  methods: {
    async sendVoiceMessage() {
      this.stopRecording();
      if (!this.audioBlob && this.audioChunks.length) {
        this.audioBlob = new Blob(this.audioChunks, {type: "audio/ogg; codecs=opus"});
      }

      if (!this.audioBlob) {
        return;
      }

      try {
        this.isUploading = true;
        let uploadedBytes = 0;
        const fileName = this.generateFileName();
        const file = new File([this.audioBlob], fileName, {type: "audio/ogg"});

        if (this.audioBlob.size > this.maxUploadSize) {
          this.$root.$emit('alert-message', this.$t('matrix.chat.upload.audio.size.exceed',
              {0: this.maxUploadSize / (1024 * 1024)}), 'warning');
          return;
        }

        const contentUri = await this.$matrixService.uploadMatrixFile(file, (percent) => {
          const uploadPart = (percent / 100) * file.size;
          const combinedBytes = uploadedBytes + uploadPart;
          this.uploadProgress = Math.min(Math.round((combinedBytes / file.size) * 80), 80);
        });

        const canvasWidth = this.$refs.waveform?.width || 150;
        const waveformSamples = this.downsample(this.samples, canvasWidth, 2)
            .map(v => Math.floor(v * 255));

        const durationMs = this.isRecording
          ? performance.now() - this.startTimestamp
          : this.endTimestamp - this.startTimestamp;

        this.isUploading = false;
        const payload = {
          msgtype: "m.audio",
          body: 'Voice message',
          url: contentUri,
          info: {
            mimetype: file.type,
            duration: Math.floor(durationMs),
            size: file.size
          },
          "org.matrix.msc1767.audio": {
            duration: Math.floor(durationMs),
            waveform: waveformSamples
          },
          "org.matrix.msc3245.voice": {}
        };

        this.$matrixService.sendMessage(payload, this.room.id)
        uploadedBytes += file.size;
        this.uploadProgress = Math.min(Math.round((uploadedBytes / file.size) * 100), 100);
        this.cancel();
      } catch (err) {
        console.error("Upload failed:", err);
      }
    },
    generateFileName() {
      return `voice-${Math.random().toString(36).substring(2, 10)}.ogg`;
    },
    async toggleRecording() {
      if (!this.isRecording && !this.isStopped && !this.audioBlob) {
        await this.startRecording();
      } else if (this.isRecording) {
        this.stopRecording();
      } else if (this.audioBlob) {
        this.togglePlayback();
      }
    },
    async startRecording() {
      const stream = await navigator.mediaDevices.getUserMedia({audio: true});
      this.audioCtx = new AudioContext();
      this.source = this.audioCtx.createMediaStreamSource(stream);
      this.analyser = this.audioCtx.createAnalyser();
      this.analyser.fftSize = 256;
      this.source.connect(this.analyser);

      this.mediaRecorder = new MediaRecorder(stream);
      this.mediaRecorder.onstart = () => {
        this.startTimestamp = performance.now();
      };
      this.audioChunks = [];
      this.recordingTime = 0;
      this.previewSamples = [];

      this.timer = setInterval(() => {
        this.recordingTime += 1;
      }, 1000);

      this.mediaRecorder.ondataavailable = e => {
        if (e.data.size > 0) {
          this.audioChunks.push(e.data);
        }
      };
      this.mediaRecorder.onstop = async () => {
        this.endTimestamp = performance.now();
        this.recordDuration = this.endTimestamp - this.startTimestamp;
        this.currentTime = Math.floor(this.recordDuration / 1000);
        this.audioBlob = new Blob(this.audioChunks, {type: "audio/ogg; codecs=opus"});
        this.createAudioPlayer();
        clearInterval(this.timer);
        cancelAnimationFrame(this.animationFrame);

        const canvasWidth = this.$refs.waveform?.width || 150;
        this.samples = this.downsample(this.samples, canvasWidth, 2);

        this.drawStaticWaveform();
      };

      this.mediaRecorder.start(250);
      this.isRecording = true;
      this.drawWaveform();
    },
    drawWaveform() {
      const canvas = this.$refs.waveform;
      const ctx = canvas.getContext("2d");
      const bufferLength = this.analyser.fftSize;
      const dataArray = new Uint8Array(bufferLength);

      const barWidth = 2; // px
      const maxBars = Math.floor(canvas.width / barWidth);
      const minHeight = 0.02;

      if (!this.previewSamples) {
        this.previewSamples = [];
      }
      if (!this.samples) {
        this.samples = [];
      }

      let lastUpdate = performance.now();
      const updateInterval = 50;

      const draw = () => {
        this.animationFrame = requestAnimationFrame(draw);
        this.analyser.getByteTimeDomainData(dataArray);

        let max = 0;
        for (let i = 0; i < bufferLength; i++) {
          const value = Math.abs(dataArray[i] - 128);
          if (value > max) {
            max = value;
          }
        }
        let normalized = Math.pow(max / 128, 0.7);
        if (normalized < minHeight) {
          normalized = minHeight;
        }

        const now = performance.now();
        if (now - lastUpdate > updateInterval) {
          this.previewSamples.unshift(normalized);
          if (this.previewSamples.length > maxBars) {
            this.previewSamples.pop();
          }
          lastUpdate = now;
        }
        this.samples.push(normalized);

        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.fillStyle = "#707070";
        for (const [i, v] of this.previewSamples.entries()) {
          const h = v * canvas.height;
          const x = i * barWidth;
          ctx.fillRect(x, (canvas.height - h) / 2, barWidth - 1, h);
        }
      };

      draw();
    },
    stopRecording() {
      this.isRecording = false;
      this.isStopped = true;
      clearInterval(this.timer);
      cancelAnimationFrame(this.animationFrame);
      this.mediaRecorder.stop();
    },
    drawStaticWaveform(samples = this.samples) {
      const canvas = this.$refs.waveform;
      if (!canvas) {
        return;
      }
      const ctx = canvas.getContext("2d");
      const barWidth = 2;
      const minHeight = 0.02;

      if (!samples || samples.length === 0) {
        return;
      }

      const maxBars = Math.floor(canvas.width / barWidth);
      const step = Math.max(1, Math.floor(samples.length / maxBars));

      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.fillStyle = "#707070";

      for (let i = 0; i < maxBars; i++) {
        const sampleIndex = i * step;
        let v = samples[sampleIndex] || minHeight;
        v = Math.pow(v, 0.7);
        if (v < minHeight) {
          v = minHeight;
        }
        const h = v * canvas.height;
        const x = i * barWidth;
        ctx.fillRect(x, (canvas.height - h) / 2, barWidth - 1, h);
      }
    },
    animatePlayback(samples = this.samples) {
      const canvas = this.$refs.waveform;
      const ctx = canvas.getContext("2d");
      const barWidth = 2;
      const minHeight = 0.02;
      const totalBars = samples.length;

      const draw = () => {
        if (!this.isPlaying) {
          return;
        }

        const duration = this.audioPlayer.duration === Infinity
          ? this.recordDuration / 1000
          : this.audioPlayer.duration

        const currentBar = Math.floor(
            (this.audioPlayer.currentTime / duration) * totalBars
        );

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        for (const [i, v] of samples.entries()) {
          const h = Math.max(v, minHeight) * canvas.height;
          const x = i * barWidth;

          // Highlight played bars
          ctx.fillStyle = "#707070";
          ctx.globalAlpha = i < currentBar ? 1 : 0.5;
          ctx.fillRect(x, (canvas.height - h) / 2, barWidth - 1, h);
        }

        this.animationFrame = requestAnimationFrame(draw);
      };

      draw();
    },
    downsample(samples, canvasWidth, barWidth = 2) {
      const totalBars = Math.floor(canvasWidth / barWidth);
      const result = [];
      // interpolation factor
      const factor = (samples.length - 1) / (totalBars - 1);

      for (let i = 0; i < totalBars; i++) {
        const idx = i * factor;
        const lower = Math.floor(idx);
        const upper = Math.ceil(idx);
        const weight = idx - lower;

        const value = lower === upper
          ? samples[lower]
          : samples[lower] * (1 - weight) + samples[upper] * weight;
        result.push(value || 0.02);
      }
      return result;
    },
    createAudioPlayer() {
      if (this.audioBlob) {
        this.audioPlayer = new Audio(URL.createObjectURL(this.audioBlob));
        this.audioPlayer.load();
      }
    },
    togglePlayback() {
      if (!this.audioBlob) {
        return;
      }

      this.audioPlayer.onended = () => {
        this.isPlaying = false;
        this.isPlaybackPaused = false;
        cancelAnimationFrame(this.animationFrame);
      };

      if (this.isPlaying && !this.isPlaybackPaused) {
        this.audioPlayer.pause();
        this.isPlaybackPaused = true;
        cancelAnimationFrame(this.animationFrame);
      } else {
        this.isPlaying = true;
        this.isPlaybackPaused = false;
        this.audioPlayer.play().then(() => {
          const width = this.$refs.waveform?.width || 150;
          const samples = this.downsample(this.samples, width);
          this.animatePlayback(samples);
          this.startTimer();
        });
      }
    },
    formatTime(time) {
      if (!time && time !== 0) {
        return "00:00";
      }
      const minutes = Math.floor(time / 60);
      const seconds = Math.floor(time % 60);
      return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
    },
    reset() {
      if (this.mediaRecorder && this.mediaRecorder.state !== "inactive") {
        this.mediaRecorder.stop();
      }

      if (this.audioPlayer) {
        this.audioPlayer.pause();
        this.stopTimer();
        this.audioPlayer.currentTime = 0;
      }
      clearInterval(this.timer);
      cancelAnimationFrame(this.animationFrame);

      if (this.source?.mediaStream?.getTracks) {
        for (const track of this.source.mediaStream.getTracks()) {
          track.stop();
        }
      }

      if (this.audioCtx) {
        this.audioCtx.close();
        this.audioCtx = null;
      }

      this.mediaRecorder = null;
      this.audioBlob = null;
      this.audioChunks = [];
      this.previewSamples = [];
      this.samples = [];
      this.audioPlayer = null;

      this.isRecording = false;
      this.isStopped = false;
      this.isPlaying = false;
      this.isPlaybackPaused = false;
      this.currentTime = 0;
      this.recordingTime = 0;
    },
    cancel() {
      this.reset();
      this.$emit('cancel');
    },
    startTimer() {
      const update = () => {
        this.currentTime = this.audioPlayer.currentTime;
        if (this.isPlaying) {
          this.animationFrame = requestAnimationFrame(update);
        }
      };
      update();
    },
    stopTimer() {
      cancelAnimationFrame(this.animationFrame);
    },
    getMaxUploadSize() {
      return this.$matrixService.getMaxUploadSize()
        .then(maxSize => this.maxUploadSize = maxSize)
        .catch(err => console.error('Error occurred:', err));
    },
  }
};
</script>
