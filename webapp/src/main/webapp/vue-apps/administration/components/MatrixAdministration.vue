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
import {updateChatSettings} from '../js/matrixAdministrationService';

<template>
  <v-app role="main" flat>
    <v-main v-if="!loading" class="application-body pa-5">
      <div class="text-title d-flex">
        <span class="">{{ $t('meeds.chat.enabled.title') }}</span>
        <v-switch
          v-model="chatSettings.chatEnabled"
          :ripple="false"
          color="primary"
          class="pa-0 mb-5 my-auto ml-auto"
          hide-details
          @change="updateChatFeature"/>
      </div>
      <div v-if="chatSettings.chatEnabled">
        <div class="mb-5 text-title d-flex">
          <span class="text-header">{{ $t('meeds.chat.rooms.authorized') }}</span>
        </div>
        <div>
          <v-switch
            v-model="chatSettings.privateRoomsEnabled"
            :ripple="false"
            :disabled="!chatSettings.chatEnabled"
            :label="$t('meeds.chat.enable.private.rooms')"
            color="primary"
            class="pa-0 mb-5 my-auto ml-auto"
            hide-details
            @change="updateChatFeature"/>
        </div>
        <div>
          <v-switch
            v-model="chatSettings.spaceRoomsEnabled"
            :ripple="false"
            :disabled="!chatSettings.chatEnabled"
            :label="$t('meeds.chat.enable.space.rooms')"
            color="primary"
            class="pa-0 my-auto ml-auto"
            hide-details
            @change="updateChatFeature"/>
        </div>
      </div>
      <div v-else class="d-flex justify-center align-center my-16">
        <v-icon large>fa-comment-slash</v-icon>
        <span class="ms-2">{{ $t('meeds.chat.deactivated') }}</span>
      </div>
      <div class="mt-5 d-flex" v-if="chatSettings.chatEnabled && chatSettings.spaceRoomsEnabled">
        <v-data-table
          :headers="headers"
          :items="spaceTemplates"
          :items-per-page="itemsPerPage"
          :hide-default-footer="hideFooter"
          fixed-header>
          <template slot="item" slot-scope="props">
            <tr>
              <td>
                <div class="d-flex align-center">
                  <v-card
                    class="d-flex align-center justify-center me-4"
                    min-width="35"
                    flat>
                    <v-icon size="28">{{ props.item.icon }}</v-icon>
                  </v-card>
                  <div v-sanitized-html="props.item.name" class="text-truncate"></div>
                </div>
              </td>
              <td>
                <div class="d-flex flex-column align-center">
                  <v-switch
                    v-model="props.item.authorized"
                    :ripple="false"
                    color="primary"
                    class="my-auto"
                    @change="updateSpaceTemplate(props.item)" />
                </div>
              </td>
              <td>
                <div class="d-flex flex-column align-center">
                  <v-switch
                    v-model="props.item.defaultStatus"
                    :ripple="false"
                    :disabled="!props.item.authorized"
                    color="primary"
                    class="my-auto"
                    @change="updateSpaceTemplate(props.item)" />
                </div>
              </td>
            </tr>
          </template>
        </v-data-table>
      </div>
    </v-main>
  </v-app>
</template>
<script>
export default {
  props: {
  },
  data: () => ({
    chatSettings: {
        'chatEnabled': true,
        'privateRoomsEnabled': true,
        'spaceRoomsEnabled': true,
        'spaceTemplateSetting': []
      },
    loading : false,
    itemsPerPage: 10,
    spaceTemplates: [],
    headers: []
  }),
  async created() {
    this.loading = true;
    this.headers = [
      { text: this.$t('meeds.chat.rooms.space.template'),},
      { text: this.$t('meeds.chat.rooms.space.template.authorized'), align: 'center', width:'130px' },
      { text: this.$t('meeds.chat.rooms.space.template.default'), align: 'center', width:'130px' },
    ];
    this.$matrixAdministrationService.loadSettings().then(respJson => {
      if (respJson) {
        this.chatSettings = respJson;
        this.spaceTemplates = this.chatSettings.spaceTemplateSetting;
      }
    }).finally(() => this.loading = false);
  },
  computed: {
    hideFooter() {
      return this.spaceTemplates && this.spaceTemplates.length <= this.itemsPerPage;
    },
  },
  methods: {
    updateChatFeature() {
      this.$matrixAdministrationService.updateChatSettings(this.chatSettings);
    },
    updateSpaceTemplate(item) {
      const indexOfTemplate = this.chatSettings.spaceTemplateSetting.findIndex(template => template.id === item.id);
      if(indexOfTemplate > -1) {
        this.chatSettings.spaceTemplateSetting.splice(indexOfTemplate, 1, {'id': item.id, 'authorized': !!item.authorized, 'defaultStatus': !!item.defaultStatus});
      } else {
        this.chatSettings.spaceTemplateSetting.push({'id': item.id, 'authorized': !!item.authorized, 'defaultStatus': !!item.defaultStatus});
      }
      this.updateChatFeature();
    },
  },
};
</script>