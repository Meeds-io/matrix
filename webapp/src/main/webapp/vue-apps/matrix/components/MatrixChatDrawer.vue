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
      {{ $t('matrix.chat.discussions') }}
    </template>
    <template #content>
      <div
        :class="expanded && 'pa-4'"
        class="d-flex light-grey-background-color fill-height">
        <div
          class="singlePageApplication pa-0 d-flex fill-height">
          <matrix-chat-rooms :rooms="rooms"/>
        </div>
      </div>
    </template>
  </exo-drawer>
</template>
<script>
export default {
  props: {
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
      console.log(`drawer is expanded ${expanded}`);
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
