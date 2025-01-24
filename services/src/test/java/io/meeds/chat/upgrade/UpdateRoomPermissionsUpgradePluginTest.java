package io.meeds.chat.upgrade;

import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.MatrixUserPermission;
import io.meeds.chat.model.SpaceRoom;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.container.xml.InitParams;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
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

  private SpaceRoom createSpaceRoom() {
    Random random = new Random();
    int randomInt = random.nextInt(100);
    SpaceRoom spaceRoom = new SpaceRoom();
    spaceRoom.setRoomId("RandomRoomId" + randomInt);
    spaceRoom.setSpaceId(String.valueOf(randomInt));
    return spaceRoom;
  }

  private List<SpaceRoom> createSpaceRooms(int count) {
    List<SpaceRoom> spaceRooms = new ArrayList<>();
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
