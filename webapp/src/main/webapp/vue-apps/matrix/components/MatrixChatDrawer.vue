<!--

 This file is part of the Meeds project (https://meeds.io/).

 Copyright (C) 2020 - 2024 Meeds Association contact@meeds.io

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
  <exo-drawer
    ref="drawer"
    :loading="loading > 0"
    class="chat-drawer"
    body-classes="hide-scroll"
    attached
    right
    @closed="$emit('closed')"
    @expand-updated="expanded = $event">
    <template slot="title">
      {{ $t('chat.discussions') }}
    </template>
    <template #content>
      <div
        :class="expanded && 'pa-4'"
        class="d-flex light-grey-background-color fill-height">
        <div
          class="singlePageApplication pa-0 d-flex fill-height">
          <matrix-chat-rooms :contacts="rooms"/>
        </div>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
    badge: {
      type: Number,
      default: () => 0,
    },
    rooms: {
      type: Array,
      default: function() { return [];}
    }
  },
  data: () =>({
    loading: 0,
  }),
  computed: {
  },
  watch: {
    loading() {
      if (this.loading === 0) {
        this.$nextTick().then(() => {
          this.$root.initialized = true;
          this.$root.$emit('chat-drawer-initialized');
        });
      }
    },
    expanded() {
      console.log(` drawer is expanded ${expanded}`);
    },
  },
  created() {
    this.$root.$on('chat-loading-start', this.incrementLoading);
    this.$root.$on('chat-loading-end', this.decrementLoading);
  },
  beforeDestroy() {
    this.$root.$off('chat-loading-start', this.incrementLoading);
    this.$root.$off('chat-loading-end', this.decrementLoading);
  },
  methods: {
    open() {
      this.$refs.drawer.open();
    },
    close() {
      this.$refs.drawer.close();
    },
    incrementLoading() {
      this.loading++;
    },
    decrementLoading() {
      this.loading--;
    },
  },
};
</script>
