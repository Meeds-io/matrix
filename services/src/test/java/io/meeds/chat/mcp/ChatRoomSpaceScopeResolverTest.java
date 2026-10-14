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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import io.meeds.chat.model.Room;
import io.meeds.chat.service.MatrixService;

@ExtendWith(MockitoExtension.class)
class ChatRoomSpaceScopeResolverTest {

  private static final String        SERVER = ":matrix.exo.tn";

  @Mock
  private MatrixService              matrixService;

  @InjectMocks
  private ChatRoomSpaceScopeResolver resolver;

  @BeforeEach
  void setUp() {
    // MatrixService#extractRoomId strips the deployment's server name only
    lenient().when(matrixService.extractRoomId(anyString())).thenAnswer(invocation -> {
      String id = invocation.getArgument(0);
      return id.startsWith("!") && id.endsWith(SERVER) ? id.substring(0, id.indexOf(':')) : id;
    });
  }

  @Test
  void name() {
    assertEquals("chat_room", resolver.getName());
  }

  /** A space room the platform created is stored as its local part, and is asked for in either shape. */
  @Test
  void spaceRoomStoredAsItsLocalPartAnswersInBothShapes() {
    Room room = room("!abc", 7L);
    when(matrixService.getById("!abc")).thenReturn(room);
    when(matrixService.getById("!abc" + SERVER)).thenReturn(room);

    assertEquals(7L, resolver.spaceOf("!abc"));
    assertEquals(7L, resolver.spaceOf("!abc" + SERVER));
  }

  @Test
  void directMessageStoredInFullAnswersNoSpace() {
    when(matrixService.getById("!dm" + SERVER)).thenReturn(room("!dm" + SERVER, null));

    assertNull(resolver.spaceOf("!dm" + SERVER));
  }

  /** The lookup returns enabled rooms only: unknown and disabled rooms both come back null. */
  @Test
  void unknownOrDisabledRoomAnswersNoSpace() {
    when(matrixService.getById("!gone")).thenReturn(null);

    assertNull(resolver.spaceOf("!gone"));
    assertNull(resolver.spaceOf(""));
    assertNull(resolver.spaceOf(null));
  }

  /** The lookup matches a prefix: a room found for "!a" whose id is "!abc" is another room. */
  @Test
  void roomFoundByPrefixOnlyAnswersNoSpace() {
    when(matrixService.getById("!a")).thenReturn(room("!abc", 7L));

    assertNull(resolver.spaceOf("!a"));
  }

  @Test
  void ambiguousPrefixAnswersNoSpaceWithoutThrowing() {
    when(matrixService.getById("!ab")).thenThrow(new IncorrectResultSizeDataAccessException(1, 2));

    assertNull(resolver.spaceOf("!ab"));
  }

  private Room room(String roomId, Long spaceId) {
    Room room = new Room();
    room.setRoomId(roomId);
    room.setSpaceId(spaceId);
    return room;
  }
}
