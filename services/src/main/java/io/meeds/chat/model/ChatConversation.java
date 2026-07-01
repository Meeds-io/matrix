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
 * A chat conversation the current user participates in, exposed to AI agents
 * through the {@code list_chat_conversations} MCP tool.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatConversation {

  /** Matrix room identifier (e.g. {@code !abcd:server}). */
  private String roomId;

  /** Conversation kind: {@code "dm"} for direct messaging, {@code "space"} for a space room. */
  private String type;

  /** Human readable title: the other participant's name for a DM, or the space display name. */
  private String title;

  /** Meeds space identifier for space rooms, {@code null} for direct messages. */
  private Long spaceId;

}
