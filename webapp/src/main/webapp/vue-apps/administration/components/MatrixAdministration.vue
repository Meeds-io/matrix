/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
<template>
  <v-app role="main" flat>
    <v-main class="application-body pa-5">
      <div class="mb-5 text-title d-flex">
        <span class="">{{ $t('meeds.chat.enabled.title') }}</span>
        <v-switch
          v-model="chatEnabled"
          :ripple="false"
          color="primary"
          class="pa-0 my-auto ml-auto"
          hide-details
          @change="enableChat"/>
      </div>
      <div v-if="!chatEnabled" class="d-flex justify-center align-center my-16">
        <v-icon large>fa-comment-slash</v-icon>
        <span class="ms-2">{{ $t('meeds.chat.deactivated') }}</span>
      </div>
    </v-main>
  </v-app>
</template>
<script>
export default {
  props: {
  },
  data: () => ({
    chatEnabled: true,
  }),
  created() {
    this.$matrixAdministrationService.isChatEnabled().then(respJson => {
      this.chatEnabled = respJson.enabled;
    });
  },
  methods: {
    enableChat() {
      this.$matrixAdministrationService.enableChatFeature(this.chatEnabled);
    }
  },
};
</script>