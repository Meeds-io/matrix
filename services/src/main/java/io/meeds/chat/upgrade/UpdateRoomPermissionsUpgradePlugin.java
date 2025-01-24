package io.meeds.chat.upgrade;

import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.SpaceRoom;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import java.util.List;

import static io.meeds.chat.service.utils.MatrixConstants.ADMIN_ROLE;

public class UpdateRoomPermissionsUpgradePlugin extends UpgradeProductPlugin {

  private static final Log LOG = ExoLogger.getExoLogger(UpdateRoomPermissionsUpgradePlugin.class);

  private MatrixService    matrixService;

  public UpdateRoomPermissionsUpgradePlugin(InitParams initParams, MatrixService matrixService) {
    super(initParams);
    this.matrixService = matrixService;
  }

  @Override
  public void processUpgrade(String s, String s1) {
    LOG.info("Start:: update room permissions on Matrix");
    long startupTime = System.currentTimeMillis();

    List<SpaceRoom> spaceRooms = matrixService.getSpaceRooms();
    int updatedRoomsCount = 0;
    for(SpaceRoom spaceRoom : spaceRooms) {
      try {
        MatrixRoomPermissions matrixRoomPermissions = matrixService.getRoomSettings(spaceRoom.getRoomId());
        if (!ADMIN_ROLE.equals(matrixRoomPermissions.getInvite())) {
          matrixRoomPermissions.setInvite(ADMIN_ROLE);
          matrixService.updateRoomSettings(spaceRoom.getRoomId(), matrixRoomPermissions);
          updatedRoomsCount ++;
        }
      } catch (Exception e) {
        throw new RuntimeException("Error while updating room permissions", e);
      }
    }

    LOG.info("Summary :: updated successfully the  permissions of {} rooms on Matrix of a total of {} existing rooms !",
            updatedRoomsCount,
            spaceRooms.size());
    LOG.info("End:: update room permissions on Matrix took {}", System.currentTimeMillis() - startupTime);
  }
}
