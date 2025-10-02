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
    class="d-flex text-truncate text-no-wrap overflow-hidden align-center mb-1 full-width">
    <v-tooltip bottom>
      <template #activator="{ on, attrs }">
        <v-btn
          v-bind="attrs"
          :loading="pendingPlay"
          :aria-label="!isPlaying && $t('matrix.chat.audio.play.label') 
            || $t('matrix.chat.audio.pause.label')"
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
    <canvas 
      ref="waveform" 
      class="my-auto flex-shrink-1 d-flex no-min-width full-width"
      style="height: 30px;"
      height="30" />
    <audio
      ref="audio"
      :src="audioSource"
      preload="metadata"
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
    }
  },
  created() {
    this.$root.$on('auto-play-audio-message', this.handleAutoPlay)
    this.$root.$on('pause-play-audio-message', this.handlePauseAudio)
    window.addEventListener('resize', this.resizeCanvas);
  },
  beforeDestroy() {
    this.$root.$off('auto-play-audio-message', this.handleAutoPlay)
    this.$root.$off('pause-play-audio-message', this.handlePauseAudio)
    window.removeEventListener('resize', this.resizeCanvas);
    
    cancelAnimationFrame(this.animationFrameId);
    this.stopTimer();
  },
  mounted() {
    this.$nextTick(() => {
      this.drawStaticWaveform();
      this.$refs?.audio?.load();
    });
  },
  watch: {
    expanded() {
      clearTimeout(this._resizeTimer)
      this._resizeTimer = setTimeout(() => {
        this.resizeCanvas()
      }, 300)
    }
  },
  methods: {
    resizeCanvas() {
      this.$nextTick(() => {
        requestAnimationFrame(() => {
          const canvas = this.$refs.waveform;
          if (!canvas) {
            return;
          }
          this.drawStaticWaveform();
        });
      });

    },
    handleAutoPlay(target) {
      if (this.message.event_id === target.event_id) {
        this.toggleAudio();
      }
    },
    handlePauseAudio(source) {
      if (this.message.event_id !== source.event_id) {
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
    startTimer() {
      const audio = this.$refs.audio;
      const update = () => {
        this.currentTime = audio.currentTime;
        if (this.isPlaying) {
          this.animationFrameId = requestAnimationFrame(update);
        }
      };
      update();
    },
    stopTimer() {
      cancelAnimationFrame(this.animationFrameId);
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
          || Array.from({ length: 50 }, () => Math.random());

      canvas.width = canvas.clientWidth;
      canvas.height = 30;

      const canvasPadding = 2;
      const middleY = canvas.height / 2;
      const maxValue = Math.max(...waveform);
      const amplifyFactor = 1.5;

      const barPixelWidth = 2;
      const barGap = 2;
      const totalBars = Math.floor(canvas.width / (barPixelWidth + barGap));

      ctx.clearRect(0, 0, canvas.width, canvas.height);

      const minBarHeight = 1;
      const maxBarHeight = middleY - canvasPadding;

      for (let i = 0; i < totalBars; i++) {
        const waveIndex = Math.floor((i / totalBars) * waveform.length);
        const value = waveform[waveIndex];
        const normalized = (value / maxValue) * amplifyFactor;
        const barHeight = Math.min(Math.max(normalized * maxBarHeight, minBarHeight), maxBarHeight);
        const x = i * (barPixelWidth + barGap);
        this.drawBar(ctx, x, middleY, barHeight, barPixelWidth, this.waveColor, isComplete ? 1 : 0.5);
      }
    },
    animateWaveform() {
      const canvas = this.$refs.waveform;
      const ctx = canvas.getContext('2d');
      const audio = this.$refs.audio;
      const waveform = this.message.content['org.matrix.msc1767.audio']?.waveform
          || Array.from({ length: 50 }, () => Math.random());

          
      canvas.width = canvas.clientWidth;
      canvas.height = 30;

      const canvasPadding = 2;
      const middleY = canvas.height / 2;
      const maxValue = Math.max(...waveform);
      const amplifyFactor = 1.5;

      const barPixelWidth = 2;
      const barGap = 2;
      const totalBars = Math.floor(canvas.width / (barPixelWidth + barGap));

      const draw = () => {
        if (!this.isPlaying) {
          return;
        }

        ctx.clearRect(0, 0, canvas.width, canvas.height);

        const minBarHeight = 1;
        const maxBarHeight = middleY - canvasPadding;
        
        const duration = audio.duration === Infinity
          ? this.message.content.info?.duration / 1000
          : audio.duration;

        const progress = audio.currentTime / duration;
        const playedBars = Math.floor(progress * totalBars);

        for (let i = 0; i < totalBars; i++) {
          const waveIndex = Math.floor((i / totalBars) * waveform.length);
          const value = waveform[waveIndex];
          const normalized = (value / maxValue) * amplifyFactor;
          const barHeight = Math.min(Math.max(normalized * maxBarHeight, minBarHeight), maxBarHeight);
          const x = i * (barPixelWidth + barGap);
          this.drawBar(ctx, x, middleY, barHeight, barPixelWidth, this.waveColor, i <= playedBars ? 1 : 0.5);
       }

        this.animationFrameId = requestAnimationFrame(draw);
      };

      draw();
    },
    drawBar(ctx, x, middleY, barHeight, barPixelWidth, color, alpha = 1) {
      ctx.beginPath();
      ctx.strokeStyle = color;
      ctx.globalAlpha = alpha;
      ctx.lineWidth = barPixelWidth;
      ctx.lineCap = "round";
      ctx.moveTo(x + barPixelWidth / 2, middleY - barHeight);
      ctx.lineTo(x + barPixelWidth / 2, middleY + barHeight);
      ctx.stroke();
    }
  }
};
</script>
