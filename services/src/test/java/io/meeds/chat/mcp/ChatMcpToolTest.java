/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.chat.mcp;

import io.meeds.chat.model.ChatConversation;
import io.meeds.chat.model.ChatMessage;
import io.meeds.chat.model.ChatUnread;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatMcpToolTest {

  private static final String USER = "demo";

  MatrixService               matrixService;

  ChatMcpTool                 chatMcpTool;

  @BeforeEach
  void setUp() {
    matrixService = mock(MatrixService.class);
    chatMcpTool = new ChatMcpTool();
    chatMcpTool.matrixService = matrixService;
    ConversationState.setCurrent(new ConversationState(new Identity(USER)));
  }

  @AfterEach
  void tearDown() {
    ConversationState.setCurrent(null);
  }

  @Test
  void listChatConversations() {
    List<ChatConversation> conversations = List.of(new ChatConversation());
    when(matrixService.getUserConversations(USER, null, null, null)).thenReturn(conversations);
    assertSame(conversations, chatMcpTool.listChatConversations(null, null, null));
    verify(matrixService).getUserConversations(USER, null, null, null);
  }

  /**
   * The type, query and limit the agent passes reach the service as they are,
   * for the current user.
   */
  @Test
  void listChatConversationsPassesTheFilters() {
    List<ChatConversation> conversations = List.of(new ChatConversation());
    when(matrixService.getUserConversations(USER, "dm", "Morgan", 5)).thenReturn(conversations);
    assertSame(conversations, chatMcpTool.listChatConversations("dm", "Morgan", 5));
  }

  @Test
  void sendDirectMessageSuccess() throws Exception {
    when(matrixService.sendDirectMessage(USER, "morgan", "Hello")).thenReturn("$evt2");
    String result = chatMcpTool.sendDirectMessage("morgan", "Hello");
    assertTrue(result.contains("$evt2"));
    assertTrue(result.contains("sent successfully"));
  }

  /**
   * Each refusal is told apart for the agent, and none claims the message was
   * sent.
   */
  @Test
  void sendDirectMessageRefusalsDoNotClaimSuccess() throws Exception {
    when(matrixService.sendDirectMessage(USER, "nobody", "Hi")).thenThrow(new ObjectNotFoundException("No user nobody"));
    String unknown = chatMcpTool.sendDirectMessage("nobody", "Hi");
    assertTrue(unknown.startsWith("ERROR"));
    assertTrue(unknown.contains("No user has the username 'nobody'"));

    when(matrixService.sendDirectMessage(USER, "outsider", "Hi")).thenThrow(new IllegalAccessException("User outsider is not an active user"));
    String refused = chatMcpTool.sendDirectMessage("outsider", "Hi");
    assertTrue(refused.startsWith("ERROR"));
    assertTrue(refused.contains("cannot send a direct message to 'outsider': User outsider is not an active user."));

    when(matrixService.sendDirectMessage(USER, USER, "Hi")).thenThrow(new IllegalArgumentException("chat.directMessage.selfNotAllowed"));
    String self = chatMcpTool.sendDirectMessage(USER, "Hi");
    assertTrue(self.startsWith("ERROR"));
    assertTrue(self.contains("other than the current user's"));

    when(matrixService.sendDirectMessage(USER, "morgan", "Hi")).thenThrow(new IllegalArgumentException("The URL of the Matrix server is required"));
    String misconfigured = chatMcpTool.sendDirectMessage("morgan", "Hi");
    assertTrue(misconfigured.startsWith("ERROR"));
    assertTrue(misconfigured.contains("chat service is unavailable"));

    when(matrixService.sendDirectMessage(USER, "slow", "Hi")).thenThrow(new InterruptedException());
    try {
      String interrupted = chatMcpTool.sendDirectMessage("slow", "Hi");
      assertTrue(interrupted.startsWith("ERROR"));
      assertTrue(interrupted.contains("NOT sent"));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }

    when(matrixService.sendDirectMessage(USER, "morgan", "Hello")).thenReturn(null);
    String failed = chatMcpTool.sendDirectMessage("morgan", "Hello");
    assertTrue(failed.startsWith("ERROR"));
    assertTrue(failed.contains("NOT sent"));
  }

  @Test
  void getChatMessagesUsesDefaultLimitWhenNull() {
    List<ChatMessage> messages = List.of(new ChatMessage());
    when(matrixService.getRoomMessages(USER, "!room1", 50)).thenReturn(messages);
    assertSame(messages, chatMcpTool.getChatMessages("!room1", null));
    verify(matrixService).getRoomMessages(USER, "!room1", 50);
  }

  @Test
  void getChatMessagesUsesProvidedLimit() {
    when(matrixService.getRoomMessages(USER, "!room1", 10)).thenReturn(List.of());
    chatMcpTool.getChatMessages("!room1", 10);
    verify(matrixService).getRoomMessages(USER, "!room1", 10);
  }

  @Test
  void getUnreadChatMessages() {
    List<ChatUnread> unread = List.of(new ChatUnread());
    when(matrixService.getUnreadConversations(USER)).thenReturn(unread);
    assertSame(unread, chatMcpTool.getUnreadChatMessages());
    verify(matrixService).getUnreadConversations(USER);
  }

  @Test
  void sendChatMessageSuccess() {
    when(matrixService.sendMessage(USER, "!room1", "Hello")).thenReturn("$evt1");
    String result = chatMcpTool.sendChatMessage("!room1", "Hello");
    assertTrue(result.contains("$evt1"));
    assertTrue(result.toLowerCase().contains("sent"));
  }

  @Test
  void sendChatMessageFailureDoesNotClaimSuccess() {
    when(matrixService.sendMessage(USER, "!room1", "Hello")).thenReturn(null);
    String result = chatMcpTool.sendChatMessage("!room1", "Hello");
    assertTrue(result.startsWith("ERROR"));
    assertTrue(result.contains("NOT sent"));
  }

}
