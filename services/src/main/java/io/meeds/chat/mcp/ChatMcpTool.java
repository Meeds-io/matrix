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
package io.meeds.chat.mcp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.meeds.chat.model.ChatConversation;
import io.meeds.chat.model.ChatMessage;
import io.meeds.chat.model.ChatUnread;
import io.meeds.chat.service.MatrixService;
import io.meeds.mcp.server.plugin.McpToolPlugin;

/**
 * MCP tools exposing the Matrix chat to AI agents. Discovered by the platform
 * mcp-server: a plain {@code @Service} implementing {@link McpToolPlugin} (no
 * {@code @McpTool} annotation, no custom constructor) whose public methods become
 * tools. Each method must have a matching entry, by snake_case name, in
 * {@code ai-tool-definitions.json} or it is silently ignored by the mcp-server.
 */
@Service
public class ChatMcpTool implements McpToolPlugin {

  @Autowired
  protected MatrixService matrixService;

  /**
   * Lists the chat conversations (direct messages and space rooms) the current
   * user participates in. Tool name: {@code list_chat_conversations}.
   *
   * @return the current user's conversations
   */
  public List<ChatConversation> listChatConversations() {
    return matrixService.getUserConversations(getCurrentUserName());
  }

  /**
   * Returns the most recent messages of one of the current user's conversations,
   * in chronological order. Tool name: {@code get_chat_messages}.
   *
   * @param conversationId the Matrix room id, as returned by
   *          {@code list_chat_conversations}
   * @param limit the maximum number of messages to return; defaults to 50 when
   *          omitted by the agent
   * @return the conversation messages, oldest first
   */
  public List<ChatMessage> getChatMessages(String conversationId, Integer limit) {
    return matrixService.getRoomMessages(getCurrentUserName(), conversationId, limit == null ? 50 : limit);
  }

  /**
   * Returns the current user's unread conversations together with their recent
   * messages, to help build a "catch me up" digest of what the user missed. Tool
   * name: {@code get_unread_chat_messages}.
   *
   * @return the current user's unread conversations
   */
  public List<ChatUnread> getUnreadChatMessages() {
    return matrixService.getUnreadConversations(getCurrentUserName());
  }

  /**
   * Sends a text message to one of the current user's conversations, on the user's
   * behalf. Tool name: {@code send_chat_message}.
   *
   * @param conversationId the Matrix room id, as returned by
   *          {@code list_chat_conversations}
   * @param text the plain text message to send
   * @return the created event id, or {@code null} when the message could not be sent
   */
  public String sendChatMessage(String conversationId, String text) {
    String eventId = matrixService.sendMessage(getCurrentUserName(), conversationId, text);
    if (eventId == null) {
      // Never report success on failure: the agent must not pretend the message was sent.
      return "ERROR: the message was NOT sent. The conversation may not exist, you may not have "
          + "access to it, or the chat service is unavailable. Do not tell the user it was sent.";
    }
    return "The message was sent successfully (event id: " + eventId + ").";
  }

}
