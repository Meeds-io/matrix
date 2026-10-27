/**
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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.chat.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

/**
 * The collaborative activity declaration analytics reads from this jar: a
 * chat message is the one event the chat records, sent from the browser
 * through the analytics websocket (ChatButton.vue, sendMessageStatistics:
 * module Chat, operation sendMessage, the room's space as the spaceId
 * parameter). It is declared twice on purpose: as an operation, so that the
 * stored events count, and as a browser operation, so that the websocket
 * path accepts it under its declared module for a member of the space. The
 * strings are the ones the Vue component sends; the label keys are matched
 * by hand against the webapp's Portlets_en.properties.
 */
class CollaborativeActivityDeclarationTest {

  private static final String DECLARATION_FILE = "analytics-collaborative-activities.json";

  private static final String CHAT_MODULE      = "Chat";

  private static final String SEND_MESSAGE     = "sendMessage";

  @Test
  @SuppressWarnings("unchecked")
  void theDeclarationCountsTheChatMessageSentFromTheBrowser() throws IOException {
    Map<String, Object> declaration;
    try (InputStream content = getClass().getClassLoader().getResourceAsStream(DECLARATION_FILE)) {
      assertNotNull(content, "the declaration ships at the root of the matrix services jar, which lands in /lib");
      declaration = new ObjectMapper().readValue(content, Map.class);
    }

    assertEquals(List.of(CHAT_MODULE), declaration.get("modules"));
    assertEquals(List.of(SEND_MESSAGE), declaration.get("operations"));
    assertEquals(List.of(Map.of("operation", SEND_MESSAGE, "module", CHAT_MODULE)),
                 declaration.get("browserOperations"),
                 "the browser operation names the module the event is stored under, whatever the page's module");
    assertFalse(declaration.containsKey("legacyOperations"));
    assertFalse(declaration.containsKey("excludedActivityTypes"));

    Map<String, Object> labels = (Map<String, Object>) declaration.get("labels");
    assertEquals(List.of("matrix.collaborativeActivity.help.counted.sendMessage"), labels.get("counted"));
    assertEquals(List.of("matrix.collaborativeActivity.help.notCounted.directMessage"), labels.get("notCounted"));
  }

}
