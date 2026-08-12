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

/** Id of the app mounted by the chat portlet in the topbar. */
const TOPBAR_APP_ID = 'matrixChatButton';

/** Id of the app mounted here when the topbar one is absent. */
const QUICK_ACTION_APP_ID = 'chat-quick-actions';

/**
 * Single mount guard: several callers may need the chat application at the
 * same moment — the unread badge of every rendered Chat tile, plus the quick
 * action itself — and only one instance must ever exist.
 */
let mountingPromise = null;

/**
 * Ensures one chat application is present on the page, mounting a hidden one
 * when needed.
 * <p>
 * The chat application owns a Matrix client: an authenticated session, a sync
 * loop, the room list and the unread counters. Mounting a second one alongside
 * the topbar instance would double the sync traffic and let the two fight over
 * unread state — so an already present instance is reused, and a new one is
 * mounted only when the chat portlet is not on the page (its topbar item can be
 * unpinned by an administrator).
 *
 * @returns {Promise} resolved once an instance is present
 */
export function ensureChatApp() {
  if (document.querySelector(`#${TOPBAR_APP_ID}`) || document.querySelector(`#${QUICK_ACTION_APP_ID}`)) {
    return Promise.resolve();
  }
  if (!mountingPromise) {
    mountingPromise = new Promise(resolve =>
      window.require(['SHARED/eXoVueI18n', 'PORTLET/matrix/Matrix'], exoi18n =>
        initChatApp(exoi18n).then(resolve)));
  }
  return mountingPromise;
}

/**
 * Mounts the chat application with its button hidden: everything the drawers
 * and the badge need comes along — the sync loop, the rooms, the presence
 * polling — without a second chat icon appearing next to the quick actions.
 */
function initChatApp(exoi18n) {
  const lang = eXo.env.portal.language;
  const urls = [
    `${eXo.env.portal.context}/${eXo.env.portal.rest}/i18n/bundle/locale.portlet.matrix-${lang}.json`,
    `/social/i18n/locale.portlet.Portlets?lang=${lang}`,
  ];
  const parent = document.createElement('div');
  parent.id = QUICK_ACTION_APP_ID;
  document.querySelector('#vuetify-apps').appendChild(parent);

  return new Promise(resolve => exoi18n.loadLanguageAsync(lang, urls)
    .then(i18n => Vue.createApp({
      template: `
        <matrix-chat-button
          id="${QUICK_ACTION_APP_ID}Instance"
          :server-name="serverName"
          hidden-button />
      `,
      data: () => ({
        // Same root context the chat portlet provides in its own main.js: the
        // drawers read these off $root. The globals come from
        // UIMatrixHeadTemplate.gtmpl, which runs on every page — so they are
        // available here even though the chat portlet itself is not displayed.
        serverName: typeof matrixServerName === 'undefined' ? null : matrixServerName,
        channel: new BroadcastChannel(TOPBAR_APP_ID),
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
      }),
      computed: {
        isMobile() {
          return this.$vuetify?.breakpoint?.mobile;
        },
        canCreateSpaceRooms() {
          // `meedsChat` is a const in the head template, so it lives in the
          // global lexical scope and is not reachable through `window`
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
      mounted() {
        document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
        resolve();
      },
      methods: {
        // Same resolution the chat portlet performs in its own main.js, so the
        // standalone drawer offers the very same room-creation affordances
        async checkCanCreateSpaceRooms() {
          const templates = await this.$spaceTemplateService.getSpaceTemplates(false);
          const circleTemplate = templates?.find(template => template.system && template.layout === 'circle' && !template.deleted);
          this.spaceCircleTemplate = ((circleTemplate && !circleTemplate.extendedProperties)
            || (circleTemplate.extendedProperties
              && circleTemplate.extendedProperties['meeds.chat.authorized'] === 'true'
              && circleTemplate.extendedProperties['meeds.chat.enabledByDefault'] === 'true'))
            && circleTemplate || null;
          if (this.spaceCircleTemplate) {
            const subspaceTemplateIds = await this.$spaceTemplateService.getSubspaceTemplateIds() || [];
            this.isSubspaceTemplate = subspaceTemplateIds.includes(this.spaceCircleTemplate.id);
          }
        },
      },
      vuetify: Vue.prototype.vuetifyOptions,
      i18n,
    }, `#${parent.id}`, 'Chat Quick Action')));
}
