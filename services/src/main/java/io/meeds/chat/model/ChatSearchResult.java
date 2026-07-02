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
package io.meeds.chat.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single full-text search hit: a message matching the query, together with the
 * conversation it belongs to. Exposed to AI agents through the
 * {@code search_chat_messages} MCP tool and to the chat UI through the
 * {@code /matrix/search} REST endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSearchResult {

  /** Matrix room id (local part) of the conversation the match belongs to. */
  private String conversationId;

  /** Matrix event id of the matching message (used to jump to it). */
  private String eventId;

  /** Human readable conversation title, when it can be resolved. */
  private String conversationTitle;

  /** Sender identifier (the Matrix local part, e.g. {@code u123}). */
  private String sender;

  /** Plain text body of the matching message. */
  private String text;

  /** Origin server timestamp in milliseconds since the epoch. */
  private long timestamp;

}
