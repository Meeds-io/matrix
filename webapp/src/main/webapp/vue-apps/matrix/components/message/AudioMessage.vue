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
  <div
    :class="{'my-audio-message': isSelfMessage, 'others-audio-message': !isSelfMessage}"
    class="d-flex text-truncate text-no-wrap overflow-hidden align-center mb-1 audio-message">
    <v-tooltip bottom>
      <template #activator="{ on, attrs }">
        <v-btn
          v-bind="attrs"
          :loading="pendingPlay"
          class="white"
          width="28"
          height="28"
          min-width="28"
          icon
          v-on="on"
          @click="toggleAudio">
          <v-icon
            class="icon-default-color"
            size="12">
            {{ !isPlaying && 'fas fa-play' || 'fas fa-pause' }}
          </v-icon>
        </v-btn>
      </template>
      <span v-if="!isPlaying">{{ $t('matrix.chat.audio.play.label') }}</span>
      <span v-else>{{ $t('matrix.chat.audio.pause.label') }}</span>
    </v-tooltip>
    <span
      :class="{'text-sub-title': !isSelfMessage, 'white--text': isSelfMessage}"
      class="mx-2 text-caption flex-grow-1">
      {{ formattedDuration }}
    </span>
    <canvas ref="waveform" class="flex-1"></canvas>
    <audio
      ref="audio"
      :src="audioSource"
      @ended="onAudioEnded"
      @loadedmetadata="onLoadedMetadata"
      @canplay="onCanPlay" />
  </div>
</template>

<script>
export default {
  data() {
    return {
      isPlaying: false,
      animationFrameId: null,
      audioReady: false,
      isEnded: false,
      pendingPlay: false,
      currentTime: 0
    };
  },
  props: {
    message: {
      type: Object,
      default: null
    },
    nextMessage: {
      type: Object,
      default: null
    },
    expanded: {
      type: Boolean,
      default: false
    },
    containerMaxWidth: {
      type: Number,
      default: null
    }
  },
  computed: {
    isSelfMessage() {
      return matrixUserId === this.message.sender;
    },
    waveColor() {
      return this.isSelfMessage && '#ffffff' || '#707070';
    },
    audioSource() {
      const url = this.message.content.url.replace('mxc://', '');
      return `/_matrix/media/v3/download/${url}`;
    },
    formattedDuration() {
      const totalSeconds = Math.floor(this.currentTime);
      const minutes = Math.floor(totalSeconds / 60).toString().padStart(2, '0');
      const seconds = (totalSeconds % 60).toString().padStart(2, '0');
      return `${minutes}:${seconds}`;
    },
    canvasWidth() {
      return this.containerMaxWidth - (this.isSelfMessage ? 42 : 60) - (this.expanded ? 59 : 0);
    }
  },
  created() {
    this.$root.$on('auto-play-audio-message', this.handleAutoPlay)
    this.$root.$on('pause-play-audio-message', this.handlePauseAudio)
  },
  mounted() {
    this.$nextTick(() => {
      this.drawStaticWaveform();
      this.$refs?.audio?.load();
    });
  },
  watch: {
    expanded() {
      this.$nextTick(() => {
        requestAnimationFrame(() => {
          this.resizeCanvas();
        });
      });
    }
  },
  methods: {
    resizeCanvas() {
      const canvas = this.$refs.waveform;
      if (!canvas) return;

      canvas.width = this.canvasWidth;
      canvas.height = 15;

      this.drawStaticWaveform();
    },
    handleAutoPlay(target) {
      if (this.message.origin_server_ts === target.origin_server_ts) {
        this.toggleAudio();
      }
    },
    handlePauseAudio(source) {
      if (this.message.origin_server_ts !== source.origin_server_ts) {
        this.pauseAudio();
      }
    },
    toggleAudio() {
      const audio = this.$refs.audio;

      if (audio.paused) {
        if (!this.audioReady) {
          this.pendingPlay = true;
          audio.load();
          return;
        }

        if (this.isEnded) {
          audio.currentTime = 0;
          this.currentTime = 0;
        } else if (!this.currentTime && !this.isPlaying) {
          this.currentTime = 0;
        }

        this.$root.$emit('pause-play-audio-message', this.message);
        this.pendingPlay = false;

        audio.play().then(() => {
          this.isPlaying = true;
          this.isEnded = false;
          this.animateWaveform();
          this.startTimer();
        });
      } else {
        this.pauseAudio();
      }
    },
    pauseAudio() {
      const audio = this.$refs.audio;
      audio.pause();
      this.isPlaying = false;
      cancelAnimationFrame(this.animationFrameId);
      this.stopTimer();
    },
    onCanPlay() {
      this.audioReady = true;
      if (this.pendingPlay) {
        this.pendingPlay = false;
        this.toggleAudio();
      }
    },
    onLoadedMetadata() {
      const audio = this.$refs.audio;
      audio.currentTime = 0;
      this.currentTime = this.message.content.info?.duration / 1000;
    },
    updateTime() {
      const audio = this.$refs.audio;
      this.currentTime = audio.currentTime;
    },
    startTimer() {
      this.timerInterval = setInterval(() => {
        this.updateTime();
      }, 200);
    },
    stopTimer() {
      clearInterval(this.timerInterval);
    },
    onAudioEnded() {
      this.isPlaying = false;
      this.isEnded = true;
      cancelAnimationFrame(this.animationFrameId);
      this.stopTimer();
      this.drawStaticWaveform(true);
      this.$root.$emit('auto-play-audio-message', this.nextMessage);
    },
    drawStaticWaveform(isComplete = false) {
      const canvas = this.$refs.waveform;
      const ctx = canvas.getContext('2d');
      const waveform = this.message.content['org.matrix.msc1767.audio']?.waveform
          || Array.from({length: 50}, () => Math.floor(Math.random() * 100));

      canvas.width = this.canvasWidth;
      canvas.height = 15;

      const barWidth = canvas.width / waveform.length;
      const middleY = canvas.height / 2;
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      waveform.forEach((value, index) => {
        const barHeight = (value / 100) * (canvas.height / 2) + 0.5;
        const x = index * barWidth;
        ctx.fillStyle = this.waveColor;
        ctx.globalAlpha = isComplete ? 1 : 0.3;
        ctx.fillRect(x, middleY - barHeight, barWidth, barHeight * 2);
      });
    },
    animateWaveform() {
      const canvas = this.$refs.waveform;
      const ctx = canvas.getContext('2d');
      const audio = this.$refs.audio;
      const waveform = this.message.content['org.matrix.msc1767.audio']?.waveform
          || Array.from({length: 50}, () => Math.floor(Math.random() * 100));
      canvas.width = this.canvasWidth;
      canvas.height = 15;

      const barWidth = canvas.width / waveform.length;
      const middleY = canvas.height / 2;
      const draw = () => {
        if (this.isEnded) {
          return;
        }

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        const currentTime = audio.currentTime;
        const duration = this.message.content.info?.duration / 1000 - 1;
        const progress = currentTime / duration;
        const playedBars = Math.floor(progress * waveform.length);

        waveform.forEach((value, index) => {
          const barHeight = (value / 100) * (canvas.height / 2) + 0.5;
          const x = index * barWidth;
          ctx.fillStyle = this.waveColor;
          ctx.globalAlpha = index <= playedBars ? 1 : 0.3;
          ctx.fillRect(x, middleY - barHeight, barWidth, barHeight * 2);
        });
        this.animationFrameId = requestAnimationFrame(draw);
      };

      draw();
    }
  }
};
</script>
