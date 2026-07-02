/*
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
*/
import ChatSearchCard from './components/ChatSearchCard.vue';

// Registered so the unified-search app can render `chat-search-card` (uiComponent).
Vue.component('chat-search-card', ChatSearchCard);

// The unified-search app calls this (if the jsModule exports it) to turn the raw
// /matrix/rest/matrix/search response (ChatSearchResult[]) into the results array.
// We collapse the per-message hits to one card per conversation (keeping the most
// recent match as the displayed snippet) with a match count — so the grouped "Chat"
// section shows each conversation once, like the in-drawer WhatsApp-style filter.
export function formatSearchResult(results) {
  if (!Array.isArray(results)) {
    return [];
  }
  const byConversation = new Map();
  for (const result of results) {
    const existing = byConversation.get(result.conversationId);
    if (!existing) {
      byConversation.set(result.conversationId, {
        ...result,
        id: result.conversationId,
        matchCount: 1,
      });
    } else {
      existing.matchCount++;
      if ((result.timestamp || 0) > (existing.timestamp || 0)) {
        existing.text = result.text;
        existing.sender = result.sender;
        existing.eventId = result.eventId;
        existing.timestamp = result.timestamp;
      }
    }
  }
  return [...byConversation.values()];
}
