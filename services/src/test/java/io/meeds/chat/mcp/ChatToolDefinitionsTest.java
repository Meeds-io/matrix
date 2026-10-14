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
package io.meeds.chat.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * The chat tools' space scope: in a run limited to a space, a room can be
 * read and written to through its id, which the chat_room resolver ties to
 * the space; nothing lists or searches the user's conversations.
 */
class ChatToolDefinitionsTest {

  @Test
  void roomReadAndWriteDeclareTheirSpaceScopeAndNothingElseDoes() throws Exception {
    Map<String, JSONObject> tools = new HashMap<>();
    try (InputStream definitions = getClass().getResourceAsStream("/ai-tool-definitions.json")) {
      JSONArray array = new JSONObject(new String(definitions.readAllBytes())).getJSONArray("tools");
      for (int i = 0; i < array.length(); i++) {
        tools.put(array.getJSONObject(i).getString("name"), array.getJSONObject(i));
      }
    }

    JSONObject read = tools.get("get_chat_messages").getJSONObject("space_scope");
    assertEquals("conversation_id", read.getString("object_arg"));
    assertEquals(ChatRoomSpaceScopeResolver.NAME, read.getString("resolver"));
    assertEquals("matrix_room", read.getString("id_format"));
    assertEquals("[\"limit\"]", read.getJSONArray("allowed_args").toString());

    JSONObject write = tools.get("send_chat_message").getJSONObject("space_scope");
    assertEquals("conversation_id", write.getString("object_arg"));
    assertEquals(ChatRoomSpaceScopeResolver.NAME, write.getString("resolver"));
    assertEquals("matrix_room", write.getString("id_format"));
    assertEquals("[\"text\"]", write.getJSONArray("allowed_args").toString());
    assertTrue(tools.get("send_chat_message").getBoolean("require_approval"));

    for (String unscoped : new String[] { "search_chat_messages", "list_chat_conversations", "get_unread_chat_messages" }) {
      assertFalse(tools.get(unscoped).has("space_scope"), unscoped);
    }
  }
}
