<template>
  <div class="chat-message-content pa-3">
    <div class="d-flex" v-if="displaySender">
      <v-avatar size="32" class="me-3">
        <v-img :src="sender && sender.profile && sender.profile.avatar" eager />
      </v-avatar>
      <div class="text-truncate text-title text-subtitle-1">
        {{sender.profile && sender.profile.fullname}}
      </div>
    </div>
    <div class="chat-message-content-body pa-3 d-flex flex-column" :class="messageContentClass">
      <div class="d-flex justify-start">
      {{ message.content.body }}
      </div>
      <div class="chat-message-timestamp text-font-small-size d-flex justify-end ">
        {{ formattedTimestamp }}
      </div>
    </div>
  </div>
</template>
<script>
  export default {
    props: {
      message: {
        type: Object,
        default: {},
      },
      previousSender: {
        type: Object,
        default: {},
      }
    },
    data() {
      return {
        sender: {},
        timestamp: '',
      };
    },
    created() {
      this.$matrixService.getUserByMatrixId(this.message.sender).then(sender => {
        this.timestamp = new Date(this.message.origin_server_ts);
        this.sender = sender;
      });
    },
    computed: {
      formattedTimestamp() {
        return this.$matrixService.formatDate(this.timestamp);
      },
      messageContentClass() {
        return localStorage.getItem('matrix_user_id') === this.message.sender ? 'chat-message-from-self': 'chat-message-from-others';
      },
      displaySender() {
        return this.previousSender !== this.message.sender && this.message.sender !== localStorage.getItem('matrix_user_id');
      }
    },
    methods: {
    }
  }
</script>
