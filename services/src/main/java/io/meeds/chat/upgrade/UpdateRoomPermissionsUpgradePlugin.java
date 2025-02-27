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
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
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

  @Override
  public boolean shouldProceedToUpgrade(String newVersion, String previousVersion) {
    return matrixService.isServiceAvailable();
  }
}
