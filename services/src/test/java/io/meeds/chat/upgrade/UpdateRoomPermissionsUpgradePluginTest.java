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
package io.meeds.chat.upgrade;

import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.Room;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.container.xml.InitParams;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static io.meeds.chat.service.utils.MatrixConstants.ADMIN_ROLE;
import static io.meeds.chat.service.utils.MatrixConstants.SIMPLE_USER_ROLE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UpdateRoomPermissionsUpgradePluginTest {

  @Test
  void processUpgrade() {
    try {
      InitParams initParams = new InitParams();
      MatrixService matrixService = mock(MatrixService.class);
      when(matrixService.getSpaceRooms()).thenReturn(createSpaceRooms(3));
      when(matrixService.getRoomSettings(anyString())).thenReturn(createMatrixRoomPermission(SIMPLE_USER_ROLE),
                                                                  createMatrixRoomPermission(SIMPLE_USER_ROLE),
                                                                  createMatrixRoomPermission(SIMPLE_USER_ROLE));
      UpdateRoomPermissionsUpgradePlugin updateRoomPermissionsUpgradePlugin =
                                                                            new UpdateRoomPermissionsUpgradePlugin(initParams,
                                                                                                                   matrixService);
      updateRoomPermissionsUpgradePlugin.processUpgrade("version1", "version2");
      verify(matrixService, times(3)).updateRoomSettings(anyString(), any(MatrixRoomPermissions.class));
    } catch (Exception e) {
      fail();
    }
  }

  @Test
  void processUpgradeSomeRooms() {
    try {
      InitParams initParams = new InitParams();
      MatrixService matrixService = mock(MatrixService.class);
      when(matrixService.getSpaceRooms()).thenReturn(createSpaceRooms(3));
      when(matrixService.getRoomSettings(anyString())).thenReturn(createMatrixRoomPermission(SIMPLE_USER_ROLE),
                                                                  createMatrixRoomPermission(ADMIN_ROLE),
                                                                  createMatrixRoomPermission(SIMPLE_USER_ROLE));
      UpdateRoomPermissionsUpgradePlugin updateRoomPermissionsUpgradePlugin =
                                                                            new UpdateRoomPermissionsUpgradePlugin(initParams,
                                                                                                                   matrixService);
      updateRoomPermissionsUpgradePlugin.processUpgrade("version1", "version2");
      verify(matrixService, times(2)).updateRoomSettings(anyString(), any(MatrixRoomPermissions.class));
    } catch (Exception e) {
      fail();
    }
  }

  @Test
  void processUpgradeWithError() {
    try {
      InitParams initParams = new InitParams();
      MatrixService matrixService = mock(MatrixService.class);
      when(matrixService.getSpaceRooms()).thenReturn(createSpaceRooms(1));
      when(matrixService.getRoomSettings(anyString())).thenThrow(new Exception("Exception"));
      UpdateRoomPermissionsUpgradePlugin updateRoomPermissionsUpgradePlugin =
                                                                            new UpdateRoomPermissionsUpgradePlugin(initParams,
                                                                                                                   matrixService);
      updateRoomPermissionsUpgradePlugin.processUpgrade("version1", "version2");
      fail();
    } catch (Exception e) {
      //Expected
    }
  }

  private Room createSpaceRoom() {
    Random random = new Random();
    int randomInt = random.nextInt(100);
    Room spaceRoom = new Room();
    spaceRoom.setRoomId("RandomRoomId" + randomInt);
    spaceRoom.setSpaceId((long) randomInt);
    return spaceRoom;
  }

  private List<Room> createSpaceRooms(int count) {
    List<Room> spaceRooms = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      spaceRooms.add(createSpaceRoom());
    }
    return spaceRooms;
  }

  private MatrixRoomPermissions createMatrixRoomPermission(String role) {
    MatrixRoomPermissions matrixRoomPermissions = new MatrixRoomPermissions();
    matrixRoomPermissions.setInvite(role);
    return matrixRoomPermissions;
  }
}
