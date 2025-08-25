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
package io.meeds.chat.service;

import io.meeds.chat.model.Room;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profile.ProfileFilter;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MatrixSynchronizationServiceTest {

  MatrixSynchronizationService matrixSynchronizationService;

  MatrixService                matrixService;

  private SpaceService         spaceService;

  private IdentityManager      identityManager;


  @BeforeEach
  void setUp() throws Exception {
    spaceService = mock(SpaceService.class);
    identityManager = mock(IdentityManager.class);
    matrixService = mock(MatrixService.class);
    matrixSynchronizationService = mock(MatrixSynchronizationService.class);
    ListAccess<Identity> usersListAccess = mock(ListAccess.class);
    when(usersListAccess.getSize()).thenReturn(3);
    Identity user1Identity = new Identity("user1");
    user1Identity.setRemoteId("user1");
    Identity user2Identity = new Identity("user2");
    user2Identity.setRemoteId("user2");
    Identity user3Identity = new Identity("user3");
    user3Identity.setRemoteId("user3");
    Profile user1Profile = new Profile(user1Identity);
    Profile user2Profile = new Profile(user2Identity);
    Profile user3Profile = new Profile(user3Identity);
    user1Profile.setProperty(USER_MATRIX_ID, "user1");
    user2Profile.setProperty(USER_MATRIX_ID, "user2");
    user3Profile.setProperty(USER_MATRIX_ID, "user3");
    user1Identity.setProfile(user1Profile);
    user2Identity.setProfile(user2Profile);
    user3Identity.setProfile(user3Profile);
    when(usersListAccess.load(anyInt(), anyInt())).thenReturn(new Identity[] { user1Identity, user2Identity, user3Identity });
    when(identityManager.getIdentitiesByProfileFilter(eq(OrganizationIdentityProvider.NAME), any(ProfileFilter.class), anyBoolean())).thenReturn(usersListAccess);

    when(identityManager.getOrCreateUserIdentity("user1")).thenReturn(user1Identity);
    when(identityManager.getOrCreateUserIdentity("user2")).thenReturn(user2Identity);
    when(identityManager.getOrCreateUserIdentity("user3")).thenReturn(user3Identity);

    Space space = new Space();
    space.setId(1);
    space.setMembers(new String[] { "user1", "user2", "user3" });
    ListAccess<Space> spaces = mock(ListAccess.class);
    when(spaces.getSize()).thenReturn(1);
    when(spaces.load(anyInt(), anyInt())).thenReturn(new Space[] { space });
    when(spaceService.getMemberSpaces(anyString())).thenReturn(spaces);

    // spaces data
    when(spaceService.getAllSpacesByFilter(any())).thenReturn(spaces);

    matrixSynchronizationService = new MatrixSynchronizationService(matrixService, spaceService, identityManager);

  }


  @Test
  void synchronizeSpaces() throws Exception {
    when(matrixService.createRoom(any())).thenReturn("!indexOfCreatedRoom:matrix.server.tn");
    matrixSynchronizationService.synchronizeSpaces();
    verify(matrixService, times(1)).createRoom(any());
    verify(matrixService, times(3)).joinUserToRoom(anyString(), anyString());
    verify(matrixService, times(1)).updateRoomAvatar(any(), anyString());

    Room room = new Room();
    room.setRoomId("!ThisIsAnIdentifierOfARoom:matrix.exo.tn");
    room.setSpaceId("1");
    when(matrixService.getRoomBySpace(any())).thenReturn(room);


    matrixSynchronizationService.synchronizeSpaces();
    verify(matrixService, times(1)).createRoom(any());
    verify(matrixService, times(2)).updateRoomAvatar(any(), anyString());
  }

  @Test
  void synchronizeUsers() throws JsonException, IOException, InterruptedException {
    matrixSynchronizationService.synchronizeUsers();
    verify(matrixService, times(3)).updateUserAvatar(any(), anyString());
  }
}
