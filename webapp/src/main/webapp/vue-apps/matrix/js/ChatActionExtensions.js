/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

/*
 * What a chat message — and the composer's "+" menu — can be extended with, as
 * registry extensions rather than entries hard-coded in this app: another add-on
 * contributes its own action without the chat knowing about it, the same way the
 * email connector lets other add-ons contribute to its attachment menu.
 *
 * Two extension points, one contract:
 *
 *   extensionRegistry.registerExtension('chat', 'message-action', descriptor);
 *   extensionRegistry.registerExtension('chat', 'composer-action', descriptor);
 *
 * 'message-action' entries render in the ⋮ menu of a message, between the built-in
 * entries and Delete. 'composer-action' entries render in the composer's "+" menu,
 * after Image and Attachment. The descriptor is the same for both:
 *
 *   {
 *     id: 'my-action',            // unique, also the menu item key
 *     rank: 50,                   // order among contributed entries, ascending
 *     icon: 'fas fa-magic',       // any platform font-awesome icon
 *     labelKey: 'my.label.key',   // resolved against the matrix bundle — a
 *                                 // contributor ships its own key in a
 *                                 // locale/portlet/matrix_en.properties of its
 *                                 // own, the platform merges same-named bundles
 *                                 // across webapps (label: a pre-resolved string,
 *                                 // as a fallback when a key is impractical)
 *     enabled: context => true,   // absent means always; an action whose
 *                                 // enabled() answers false is never rendered
 *     click: context => {},       // the action itself
 *   }
 *
 * A contributor needing more than an icon, a label and a click registers a
 * `vueComponent` name instead of icon/labelKey/click; the globally-registered
 * component is rendered in place with the same `context` as a prop and may emit
 * `close` to close the menu.
 *
 * The `context` a message action receives:
 *   - message, room, isMyMessage: what the menu itself knows;
 *   - attachment: {fileName, mimeType, size, mxcUrl} for a file/image/video/audio
 *     message, null otherwise;
 *   - getAttachmentFile(): Promise<File> — the attachment's bytes, fetched the
 *     authenticated way (client/v1 media + Bearer token). Consumers must use this
 *     rather than build media URLs: the legacy unauthenticated media endpoints
 *     401 on modern Synapse, and the token is this app's to hold.
 *
 * The `context` a composer action receives:
 *   - room: where the composer writes;
 *   - sendFile(file): Promise — uploads the File to the Matrix media store and
 *     sends it as a file message into the room, through the very path the "+"
 *     menu's own Image/Attachment entries take (same size gate, same progress on
 *     the "+" button, same statistics). Consumers must not reimplement the upload.
 *
 * DISCOVERY — the part that silently fails when skipped. Registration code lives
 * in the contributing add-on's own JS module, and a gatein module is only defined,
 * never executed, until something requires it. So the contract is:
 *   - the contributor names its gatein module with a name CONTAINING
 *     "ChatActionExtension" (e.g. EmailConnectorChatActionExtension) and puts it
 *     in the matrixGRP load-group, so it is present wherever the chat renders;
 *   - this app requires every page module whose name contains that suffix (and
 *     calls its exported init(), when there is one) before reading the registry —
 *     the same includeExtensions() contract the platform's favorites drawer and
 *     Task app use;
 *   - late registrations are still caught: the registry dispatches
 *     'extension-chat-<type>-updated' on document for every registerExtension,
 *     and both menus listen to it.
 */

const EXTENSION_TYPE = 'chat';

export const MESSAGE_ACTION_EXTENSION = 'message-action';

export const COMPOSER_ACTION_EXTENSION = 'composer-action';

/**
 * The document events the registry fires when an action lands, one per point —
 * what the menus listen to so a module loaded after they mounted still shows up.
 */
export const MESSAGE_ACTION_UPDATED_EVENT = `extension-${EXTENSION_TYPE}-${MESSAGE_ACTION_EXTENSION}-updated`;

export const COMPOSER_ACTION_UPDATED_EVENT = `extension-${EXTENSION_TYPE}-${COMPOSER_ACTION_EXTENSION}-updated`;

// The module-name suffix a contributor MUST carry to be discovered.
const EXTENSION_MODULE_SUFFIX = 'ChatActionExtension';

// The modules already required, so repeated discovery calls (each menu triggers
// one) require each contributor exactly once.
const includedModules = new Set();

/**
 * Requires every module of the page whose name carries the discovery suffix, and
 * calls its exported init() when it has one. Idempotent per module, cheap when
 * there is nothing new — both menus call it freely on creation.
 *
 * @returns {void}
 */
export function includeChatActionExtensions() {
  (window.requireJsModules || [])
    .filter(name => typeof name === 'string' && name.includes(EXTENSION_MODULE_SUFFIX))
    .filter(name => !includedModules.has(name))
    .forEach(name => {
      includedModules.add(name);
      window.require([name], module => module?.init?.());
    });
}

/**
 * The applicable actions of one extension point, in rank order — loadExtensions
 * already sorts by rank, an absent rank sinking last.
 *
 * @param {string} extensionName the extension point, message-action or composer-action
 * @param {object} context the context the actions will receive, handed to enabled()
 * @returns {Array} the applicable action descriptors
 */
function getActions(extensionName, context) {
  return (extensionRegistry?.loadExtensions(EXTENSION_TYPE, extensionName) || [])
    .filter(action => !action.enabled || action.enabled(context));
}

/**
 * The contributed actions that apply to one message, for its ⋮ menu.
 *
 * @param {object} context the message action context, see the contract above
 * @returns {Array} the applicable action descriptors, in rank order
 */
export function getMessageActions(context) {
  return getActions(MESSAGE_ACTION_EXTENSION, context);
}

/**
 * The contributed actions that apply to the composer, for its "+" menu.
 *
 * @param {object} context the composer action context, see the contract above
 * @returns {Array} the applicable action descriptors, in rank order
 */
export function getComposerActions(context) {
  return getActions(COMPOSER_ACTION_EXTENSION, context);
}
