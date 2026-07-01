/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2025 Meeds Association contact@meeds.io
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

// Contributes a "Chat" surface to the AI addon's UX-binding administration,
// from the Matrix side (loaded into the AI admin via the aiUxBindingExtenionsGRP
// load-group). Mirrors the AI "notes" parent/children shape so the existing
// default admin rendering picks it up. No AI-repo change required.
// `extensionRegistry` is a dependency-injected global (NOT on window) — same as
// the AI/matrix extension scripts use it. typeof guard avoids a ReferenceError
// if it ever loads out of order.
function registerChatUxBindingSurface() {
  if (typeof extensionRegistry === 'undefined' || !extensionRegistry) {
    return;
  }
  // nameKey is shown verbatim when no translation exists (admin does
  // `$te(nameKey) ? $t(nameKey) : nameKey`), so a readable string doubles as the
  // label without needing the Matrix i18n bundle on the AI admin page.
  // Parent node (groups the chat surfaces in the admin sidebar)
  extensionRegistry.registerExtension('ai-agent', 'ux-binding', {
    id: 'chat',
    parentId: null,
    rank: 500,
    nameKey: 'Chat',
  });
  // Conversation-list binding (Ask AI from the chat drawer header)
  extensionRegistry.registerExtension('ai-agent', 'ux-binding', {
    id: 'chatList',
    parentId: 'chat',
    applicationId: 'chat',
    extensionId: 'chatList',
    rank: 501,
    nameKey: 'Conversation list',
  });
  // Per-room binding (Ask AI from the room header)
  extensionRegistry.registerExtension('ai-agent', 'ux-binding', {
    id: 'chatRoom',
    parentId: 'chat',
    applicationId: 'chat',
    extensionId: 'chatRoom',
    rank: 502,
    nameKey: 'Chat room',
  });
  // Per-message binding (Ask AI from a message). Uses the list-and-transcription
  // main component so admins also get an "Activate Voice transcription" toggle +
  // agent picker here (same widget as concierge). That manages a uxBinding at
  // applicationId 'chat' / extensionId 'chatMessage-transcription', which the chat
  // audio "Transcribe" action reads to know which transcription agent to use.
  extensionRegistry.registerExtension('ai-agent', 'ux-binding', {
    id: 'chatMessage',
    parentId: 'chat',
    applicationId: 'chat',
    extensionId: 'chatMessage',
    transcriptionExtensionId: 'chatMessage-transcription',
    component: 'ai-ux-bindings-list-and-transcription',
    rank: 503,
    nameKey: 'Chat message',
  });
}

// Runs when the module is required (e.g. by includeExtensions('AiUxBindingExtension')).
registerChatUxBindingSurface();

// The includeExtensions resolver also calls module.init() on the required module.
export function init() {
  registerChatUxBindingSurface();
}
