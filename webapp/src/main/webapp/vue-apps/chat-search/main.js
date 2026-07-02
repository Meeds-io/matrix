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
// We just give each hit a stable `id` (its Matrix event id).
export function formatSearchResult(results) {
  if (!Array.isArray(results)) {
    return [];
  }
  return results.map(result => ({
    ...result,
    id: result.eventId,
  }));
}
