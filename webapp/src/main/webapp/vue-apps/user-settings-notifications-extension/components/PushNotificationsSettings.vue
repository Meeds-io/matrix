/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io
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
 <div>
   <v-list-item two-line>
     <v-list-item-content>
       <v-list-item-title class="text-color">
         {{ $t('UINotification.name.push.notification') }}
       </v-list-item-title>
       <v-list-item-subtitle>
         {{ $t('UINotification.description.push.notification') }}
       </v-list-item-subtitle>
     </v-list-item-content>
     <v-list-item-action>
       <v-switch
         v-model="active"
         :loading="saving"
         @change="save" />
     </v-list-item-action>
   </v-list-item>
   <v-divider class="mx-4" />
 </div>
</template>

<script>
export default {
  data: () => ({
    active: false,
    saving: false,
  }),
  created() {
    this.saving = true;
    this.$notificationsSettingsService.isPushNotificationsEnabled(eXo.env.portal.userName)
      .then(enabled => {
        this.active = enabled === 'true';
      })
      .finally(() => this.saving = false);
  },
  methods: {
    save() {
      this.saving = true;
      this.$notificationsSettingsService.updatePushNotificationsSettings(eXo.env.portal.userName, this.active)
        .finally(() => this.saving = false);
    }
  }
};
</script>