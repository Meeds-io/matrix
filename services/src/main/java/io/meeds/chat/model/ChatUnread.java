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

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unread state of one of the current user's conversations, exposed to AI agents
 * through the {@code get_unread_chat_messages} MCP tool to help build a
 * "catch me up" digest.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatUnread {

  /** Matrix room id of the conversation. */
  private String roomId;

  /** Human readable conversation title, when known. */
  private String title;

  /** Number of unread messages in the conversation. */
  private int unreadCount;

  /** The recent messages of the conversation, oldest first. */
  private List<ChatMessage> messages;

}
