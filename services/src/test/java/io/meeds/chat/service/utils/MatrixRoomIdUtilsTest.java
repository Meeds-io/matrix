/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
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
package io.meeds.chat.service.utils;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_SERVER_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.commons.utils.PropertyManager;

class MatrixRoomIdUtilsTest {

  // A room id returned by createRoom on matrixdotorg/synapse:v1.165.0 (default room version 12)
  private static final String ROOM_V12_ID = "!-GWecwzq27coEHKwQSgJnr2PzFDU_Pm6opoUwNkL_s8";

  @BeforeEach
  void setUp() {
    PropertyManager.setProperty(MATRIX_SERVER_NAME, "matrix.exo.com");
  }

  @Test
  void isHashRoomIdRecognisesOnlyRoomVersion12Ids() {
    assertTrue(MatrixRoomIdUtils.isHashRoomId(ROOM_V12_ID));
    assertFalse(MatrixRoomIdUtils.isHashRoomId("!AbCdEfGhIjKlMnOpQr"));
    assertFalse(MatrixRoomIdUtils.isHashRoomId("!AbCdEfGhIjKlMnOpQr:matrix.exo.com"));
    assertFalse(MatrixRoomIdUtils.isHashRoomId(ROOM_V12_ID + "x"));
    assertFalse(MatrixRoomIdUtils.isHashRoomId(ROOM_V12_ID.substring(0, 43)));
    assertFalse(MatrixRoomIdUtils.isHashRoomId("!-GWecwzq27coEHKwQSgJnr2PzFDU+Pm6opoUwNkL/s8"));
    assertFalse(MatrixRoomIdUtils.isHashRoomId(null));
  }

  @Test
  void toFullRoomIdAppendsTheServerNameToALegacyStoredIdOnly() {
    assertEquals("!AbCdEfGhIjKlMnOpQr:matrix.exo.com", MatrixRoomIdUtils.toFullRoomId("!AbCdEfGhIjKlMnOpQr"));
    assertEquals(ROOM_V12_ID, MatrixRoomIdUtils.toFullRoomId(ROOM_V12_ID));
    assertEquals("!dmRoom:matrix.exo.com", MatrixRoomIdUtils.toFullRoomId("!dmRoom:matrix.exo.com"));
    assertEquals("!dmRoom:other.server", MatrixRoomIdUtils.toFullRoomId("!dmRoom:other.server"));
    assertNull(MatrixRoomIdUtils.toFullRoomId(null));
  }

  @Test
  void toStoredRoomIdRemovesOnlyThisServersName() {
    assertEquals("!AbCdEfGhIjKlMnOpQr", MatrixRoomIdUtils.toStoredRoomId("!AbCdEfGhIjKlMnOpQr:matrix.exo.com"));
    assertEquals(ROOM_V12_ID, MatrixRoomIdUtils.toStoredRoomId(ROOM_V12_ID));
    assertEquals("!dmRoom:other.server", MatrixRoomIdUtils.toStoredRoomId("!dmRoom:other.server"));
    assertNull(MatrixRoomIdUtils.toStoredRoomId(null));
  }

  @Test
  void aStoredIdMapsBackToTheIdSynapseReturned() {
    String legacyRoomId = "!AbCdEfGhIjKlMnOpQr:matrix.exo.com";
    assertEquals(legacyRoomId, MatrixRoomIdUtils.toFullRoomId(MatrixRoomIdUtils.toStoredRoomId(legacyRoomId)));
    assertEquals(ROOM_V12_ID, MatrixRoomIdUtils.toFullRoomId(MatrixRoomIdUtils.toStoredRoomId(ROOM_V12_ID)));
  }
}
