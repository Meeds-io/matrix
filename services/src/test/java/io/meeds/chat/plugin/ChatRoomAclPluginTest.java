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
package io.meeds.chat.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.security.Identity;

import io.meeds.chat.model.Room;
import io.meeds.chat.service.MatrixService;

class ChatRoomAclPluginTest {

  private static final String ROOM_ID = "!room:server";

  @Mock
  private PortalContainer    container;

  @Mock
  private MatrixService      matrixService;

  @InjectMocks
  private ChatRoomAclPlugin  plugin;

  @BeforeEach
  void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  void shouldExposeChatRoomObjectType() {
    assertEquals("chatRoom", ChatRoomAclPlugin.OBJECT_TYPE);
    assertEquals("chatRoom", plugin.getObjectType());
  }

  @Test
  void shouldRegisterItselfIntoUserAclOnInit() {
    UserACL userACL = org.mockito.Mockito.mock(UserACL.class);
    when(container.getComponentInstanceOfType(UserACL.class)).thenReturn(userACL);

    plugin.init();

    verify(userACL).addAclPlugin(plugin);
  }

  @Test
  void shouldDenyWhenObjectIdIsNull() {
    assertFalse(plugin.hasPermission(null, "VIEW", new Identity("john")));
    verify(matrixService, never()).getById(anyString());
  }

  @Test
  void shouldDenyWhenIdentityIsNull() {
    assertFalse(plugin.hasPermission(ROOM_ID, "VIEW", null));
    verify(matrixService, never()).getById(anyString());
  }

  @Test
  void shouldDenyWhenRoomDoesNotExist() {
    when(matrixService.getById(ROOM_ID)).thenReturn(null);

    assertFalse(plugin.hasPermission(ROOM_ID, "VIEW", new Identity("john")));
  }

  @Test
  void shouldDenyWhenUserCannotAccessRoom() {
    Room room = new Room();
    room.setRoomId(ROOM_ID);
    when(matrixService.getById(ROOM_ID)).thenReturn(room);
    when(matrixService.canAccess(room, "john")).thenReturn(false);

    assertFalse(plugin.hasPermission(ROOM_ID, "VIEW", new Identity("john")));
  }

  @Test
  void shouldAllowWhenUserCanAccessRoom() {
    Room room = new Room();
    room.setRoomId(ROOM_ID);
    when(matrixService.getById(ROOM_ID)).thenReturn(room);
    when(matrixService.canAccess(any(Room.class), anyString())).thenReturn(true);

    assertTrue(plugin.hasPermission(ROOM_ID, "VIEW", new Identity("john")));
  }

}
