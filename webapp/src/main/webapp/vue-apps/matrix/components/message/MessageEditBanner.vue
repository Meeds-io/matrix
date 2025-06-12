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
  <v-card
    class="pa-2 border-radius-16"
    flat>
    <v-btn
      class="position-absolute t-0 r-0 ma-1"
      icon
      small
      @click="$emit('close')">
      <v-icon
        size="16">
        fa-solid fa-times
      </v-icon>
    </v-btn>
    <div class="d-flex flex-row">
      <v-icon
        class="me-2 ms-1"
        size="16">
        fa-edit
      </v-icon>
      {{ $t('matrix.chat.label.editMessageBanner') }}
    </div>
  </v-card>
</template>

export default {
}