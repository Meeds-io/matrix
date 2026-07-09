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
package io.meeds.chat.listeners;

import io.meeds.chat.MatrixBaseTest;
import io.meeds.chat.model.Room;
import io.meeds.social.space.plugin.SpaceExtendedPropertiesLifeCycleEvent;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

import static io.meeds.chat.service.utils.MatrixConstants.SPACE_CHAT_AUTHORIZED;
import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class MatrixSpaceListenerTest extends MatrixBaseTest {

  @Autowired
  private MatrixSpaceListener matrixSpaceListener;

  @Autowired
  private IdentityManager     identityManager;

  private String setMatrixId(String userName, String matrixId) {
    Identity identity = identityManager.getOrCreateUserIdentity(userName);
    Profile profile = identity.getProfile();
    profile.setProperty(USER_MATRIX_ID, matrixId);
    identityManager.updateProfile(profile);
    return matrixId;
  }

  @Test
  void spaceRenamedRenamesLinkedRoom() throws Exception {
    Space space = getSpaceInstance(1);
    clearInvocations(matrixHttpClient);

    matrixSpaceListener.spaceRenamed(new SpaceLifeCycleEvent(space, "demo", SpaceLifeCycleEvent.Type.SPACE_RENAMED));
    verify(matrixHttpClient, times(1)).renameRoom(eq(matrixRoomId), eq(space.getDisplayName()), eq(accessToken));
  }

  @Test
  void spaceRenamedWithoutLinkedRoomDoesNothing() throws Exception {
    Space space = new Space();
    space.setId("999888777");
    space.setDisplayName("Unlinked space");
    clearInvocations(matrixHttpClient);

    matrixSpaceListener.spaceRenamed(new SpaceLifeCycleEvent(space, "demo", SpaceLifeCycleEvent.Type.SPACE_RENAMED));
    verify(matrixHttpClient, never()).renameRoom(anyString(), anyString(), anyString());
  }

  @Test
  void joinedJoinsUserToLinkedRoom() throws Exception {
    Space space = getSpaceInstance(1);
    String raulMatrixId = setMatrixId("raul", "@raul:matrix.exo.tn");
    clearInvocations(matrixHttpClient);

    matrixSpaceListener.joined(new SpaceLifeCycleEvent(space, "raul", SpaceLifeCycleEvent.Type.JOINED));

    verify(matrixHttpClient, times(1)).joinUserToRoom(eq(matrixRoomId), eq(raulMatrixId), eq(accessToken));
  }

  @Test
  void leftKicksUserFromLinkedRoom() throws Exception {
    Space space = getSpaceInstance(1);
    String raulMatrixId = setMatrixId("raul", "@raul:matrix.exo.tn");
    clearInvocations(matrixHttpClient);

    matrixSpaceListener.left(new SpaceLifeCycleEvent(space, "raul", SpaceLifeCycleEvent.Type.LEFT));

    verify(matrixHttpClient, times(1)).kickUserFromRoom(eq(matrixRoomId), eq(raulMatrixId), anyString(), eq(accessToken));
  }

  @Test
  void grantedLeadPromotesUserInRoomPermissions() throws Exception {
    Space space = getSpaceInstance(1);
    String raulMatrixId = setMatrixId("raul", "@raul:matrix.exo.tn");
    clearInvocations(matrixHttpClient);

    matrixSpaceListener.grantedLead(new SpaceLifeCycleEvent(space, "raul", SpaceLifeCycleEvent.Type.GRANTED_LEAD));

    verify(matrixHttpClient, times(1)).getRoomSettings(eq(matrixRoomId), eq(accessToken));
    verify(matrixHttpClient, times(1)).updateRoomSettings(eq(matrixRoomId), any(), eq(accessToken));
  }

  @Test
  void revokedLeadDemotesUserInRoomPermissions() throws Exception {
    Space space = getSpaceInstance(1);
    setMatrixId("demo", "@demo:matrix.exo.tn");
    clearInvocations(matrixHttpClient);

    matrixSpaceListener.revokedLead(new SpaceLifeCycleEvent(space, "demo", SpaceLifeCycleEvent.Type.REVOKED_LEAD));

    verify(matrixHttpClient, times(1)).getRoomSettings(eq(matrixRoomId), eq(accessToken));
    verify(matrixHttpClient, times(1)).updateRoomSettings(eq(matrixRoomId), any(), eq(accessToken));
  }

  @Test
  void spaceDescriptionEditedUpdatesLinkedRoom() throws Exception {
    Space space = getSpaceInstance(1);
    clearInvocations(matrixHttpClient);

    matrixSpaceListener.spaceDescriptionEdited(new SpaceLifeCycleEvent(space,
                                                                        "demo",
                                                                        SpaceLifeCycleEvent.Type.SPACE_DESCRIPTION_EDITED));

    verify(matrixHttpClient, times(1)).updateRoomDescription(eq(matrixRoomId), eq(space.getDescription()), eq(accessToken));
  }

  @Test
  void spaceRemovedDeletesLinkedRoom() throws Exception {
    Space space = getSpaceInstance(1);
    clearInvocations(matrixHttpClient);

    matrixSpaceListener.spaceRemoved(new SpaceLifeCycleEvent(space, "demo", SpaceLifeCycleEvent.Type.SPACE_REMOVED));

    verify(matrixHttpClient, times(1)).deleteRoom(eq(matrixRoomId), eq(accessToken));
  }

  @Test
  void extendedPropertiesUpdatedIgnoresUnrelatedChanges() throws Exception {
    Space space = new Space();
    space.setId("999888777");
    space.setDisplayName("Unlinked space");
    clearInvocations(matrixHttpClient);

    SpaceExtendedPropertiesLifeCycleEvent event =
                                               new SpaceExtendedPropertiesLifeCycleEvent(space,
                                                                                          "demo",
                                                                                          SpaceLifeCycleEvent.Type.EXTENDED_PROPERTIES_UPDATED,
                                                                                          List.of("some.other.property"));
    matrixSpaceListener.extendedPropertiesUpdated(event);

    verify(matrixHttpClient, never()).createRoom(anyString(), anyString(), anyString());
  }

  @Test
  void extendedPropertiesUpdatedCreatesMissingRoom() throws Exception {
    Space space = new Space();
    space.setId("999888778");
    space.setDisplayName("Space needing a room");
    space.setMembers(new String[0]);
    space.setExtendedProperties(Map.of(SPACE_CHAT_AUTHORIZED, "true"));
    clearInvocations(matrixHttpClient);

    SpaceExtendedPropertiesLifeCycleEvent event =
                                               new SpaceExtendedPropertiesLifeCycleEvent(space,
                                                                                          "demo",
                                                                                          SpaceLifeCycleEvent.Type.EXTENDED_PROPERTIES_UPDATED,
                                                                                          List.of(SPACE_CHAT_AUTHORIZED));
    matrixSpaceListener.extendedPropertiesUpdated(event);

    verify(matrixHttpClient, timeout(3000)).createRoom(eq(space.getDisplayName()), anyString(), eq(accessToken));
    Room room = matrixService.getRoomBySpace(space, true);
    assertNotNull(room);
    assertEquals(matrixRoomId, room.getRoomId());
  }

}
