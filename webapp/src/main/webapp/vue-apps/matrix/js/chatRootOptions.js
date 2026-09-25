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

/**
 * The root context the chat drawers read off `$root`.
 *
 * Shared by the two places a chat application can be mounted — the topbar
 * portlet, and the hidden instance the Application Center badge mounts when
 * that portlet is not on the page — so the same drawer cannot behave
 * differently depending on which one mounted it. The two copies had already
 * drifted on `isMobile`.
 *
 * @param {String} serverName the Matrix server name
 * @param {BroadcastChannel} channel the cross-tab channel of the chat instance
 * @returns {Object} Vue root options to spread into `Vue.createApp`
 */
export function chatRootOptions(serverName, channel) {
  return {
    data() {
      return {
        serverName: serverName,
        channel: channel,
        fullPageMode: false,
        fullPageMessagesContainerWidth: 420,
        defaultRoomListContainerWidth: 404,
        spaceCircleTemplate: null,
        isSubspaceTemplate: false,
        statusMap: {
          available: '#2eb58c',
          donotdisturb: '#bc4343',
          offline: '#707070',
          invisible: '#707070',
        },
      };
    },
    computed: {
      isMobile() {
        return this.$vuetify.breakpoint.name === 'xs' || this.$vuetify.breakpoint.name === 'sm';
      },
      canCreateSpaceRooms() {
        // `meedsChat` is a const in the head template, so it lives in the global
        // lexical scope and is not reachable through `window`
        return !!this.spaceCircleTemplate
          && typeof meedsChat !== 'undefined' && !!meedsChat.spaceRoomsEnabled;
      },
      canCreatePrivateRooms() {
        return typeof meedsChat !== 'undefined' && !!meedsChat.privateRoomsEnabled;
      },
      canCreateRooms() {
        return this.canCreateSpaceRooms || this.canCreatePrivateRooms;
      },
    },
    created() {
      this.checkCanCreateSpaceRooms();
    },
    methods: {
      async checkCanCreateSpaceRooms() {
        const templates = await this.$spaceTemplateService.getSpaceTemplates(false);
        const circleTemplate = templates?.find(template => template.system && template.layout === 'circle' && !template.deleted);
        // `find` returns undefined when the deployment ships no system circle
        // template, so the template has to be tested before its properties are
        // read at all
        this.spaceCircleTemplate = circleTemplate
          && (!circleTemplate.extendedProperties
            || (circleTemplate.extendedProperties['meeds.chat.authorized'] === 'true'
              && circleTemplate.extendedProperties['meeds.chat.enabledByDefault'] === 'true'))
          && circleTemplate || null;
        if (this.spaceCircleTemplate) {
          const subspaceTemplateIds = await this.$spaceTemplateService.getSubspaceTemplateIds() || [];
          this.isSubspaceTemplate = subspaceTemplateIds.includes(this.spaceCircleTemplate.id);
        }
      },
    },
  };
}
