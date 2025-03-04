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
package io.meeds.chat.listeners;

import io.meeds.chat.model.Room;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.MatrixUserPermission;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.space.SpaceListenerPlugin;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceLifeCycleEvent;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

import static io.meeds.chat.service.utils.MatrixConstants.*;

@Component
public class MatrixSpaceListener extends SpaceListenerPlugin {

  private static final Log LOG = ExoLogger.getLogger(MatrixSpaceListener.class);

  @Autowired
  private MatrixService    matrixService;

  @Autowired
  private SpaceService     spaceService;

  @Autowired
  OrganizationService      organizationService;

  @PostConstruct
  public void init() {
    spaceService.registerSpaceListenerPlugin(this);
  }

  @Override
  public void spaceCreated(SpaceLifeCycleEvent event) {
    if(!matrixService.isServiceAvailable()) {
      return;
    }
    Space space = event.getSpace();
    try {
      String matrixRoomId = matrixService.createRoom(space);

      if (StringUtils.isNotBlank(matrixRoomId)) {
        List<String> members = new ArrayList<>(Arrays.asList(space.getMembers()));
        for (String manager : space.getManagers()) {
          String matrixIdOfUser = matrixService.getMatrixIdForUser(manager);
          if (StringUtils.isNotBlank(matrixRoomId) && StringUtils.isNotBlank(matrixIdOfUser)) {
            matrixService.joinUserToRoom(matrixRoomId, matrixIdOfUser);
            updateMemberRoleInSpace(space, matrixIdOfUser, MANAGER_ROLE);
            members.remove(manager);
          }
        }
        for (String member : members) {
          String matrixIdOfUser = matrixService.getMatrixIdForUser(member);
          if (StringUtils.isNotBlank(matrixRoomId) && StringUtils.isNotBlank(matrixIdOfUser)) {
            matrixService.joinUserToRoom(matrixRoomId, matrixIdOfUser);
          }
        }
      }
    } catch (Exception e) {
      LOG.error("Matrix integration: Could not create a room for space {}", space.getDisplayName(), e);
    }
  }

  @Override
  public void spaceRenamed(SpaceLifeCycleEvent event) {
    if(!matrixService.isServiceAvailable()) {
      return;
    }
    Space space = event.getSpace();
    String spaceDisplayName = space.getDisplayName();
    Room room = matrixService.getRoomBySpace(space);
    if (StringUtils.isNotBlank(room.getRoomId())) {
      try {
        matrixService.renameRoom(room.getRoomId(), spaceDisplayName);
      } catch (Exception e) {
        LOG.error("Could not rename the room linked to the space {}", space.getDisplayName(), e);
      }
    }
  }

  @Override
  public void joined(SpaceLifeCycleEvent event) {
    if(!matrixService.isServiceAvailable()) {
      return;
    }
    Space space = event.getSpace();
    String userId = event.getTarget();
    String restrictedGroupOfUsers = PropertyManager.getProperty(MATRIX_RESTRICTED_USERS_GROUP);
    String matrixUserAdmin = PropertyManager.getProperty(MATRIX_ADMIN_USERNAME);
    String matrixIdOfUser = matrixService.getMatrixIdForUser(userId);
    if (StringUtils.isBlank(matrixIdOfUser) && StringUtils.isNotBlank(restrictedGroupOfUsers)
        && restrictedGroupOfUsers.equals(space.getGroupId()) && !userId.equals(matrixUserAdmin)) {
      User user;
      try {
        user = organizationService.getUserHandler().findUserByName(userId);
        matrixIdOfUser = matrixService.saveUserAccount(user, true, false);
      } catch (Exception e) {
        LOG.error("Could not retrieve the user {}", userId, e);
      }
    }
    Room room = matrixService.getRoomBySpace(space);
    String roomId = room.getRoomId();
    if (StringUtils.isNotBlank(roomId) && StringUtils.isNotBlank(matrixIdOfUser)) {
      try {
        matrixService.joinUserToRoom(roomId, matrixIdOfUser);
      } catch (Exception e) {
        LOG.error("Could not join the user {} to the room of the space {} on Matrix", userId, space.getDisplayName(), e);
      }
    }
  }

  @Override
  public void left(SpaceLifeCycleEvent event) {
    if(!matrixService.isServiceAvailable()) {
      return;
    }
    Space space = event.getSpace();
    String userId = event.getTarget();
    Room room = matrixService.getRoomBySpace(space);
    String roomId = room.getRoomId();
    String matrixIdOfUser = matrixService.getMatrixIdForUser(userId);
    if (StringUtils.isNotBlank(roomId) && StringUtils.isNotBlank(matrixIdOfUser)) {
      try {
        matrixService.kickUserFromRoom(roomId, matrixIdOfUser, MESSAGE_USER_KICKED_SPACE.formatted(space.getDisplayName()));
      } catch (Exception e) {
        LOG.error("Could not kick the user {] from the room of the space {}", userId, space.getDisplayName(), e);
      }
    }
  }

  @Override
  public void grantedLead(SpaceLifeCycleEvent event) {
    if(!matrixService.isServiceAvailable()) {
      return;
    }
    Space space = event.getSpace();
    String matrixIdOfUser = matrixService.getMatrixIdForUser(event.getTarget());
    updateMemberRoleInSpace(space, matrixIdOfUser, MANAGER_ROLE);
  }

  @Override
  public void revokedLead(SpaceLifeCycleEvent event) {
    if(!matrixService.isServiceAvailable()) {
      return;
    }
    Space space = event.getSpace();
    String matrixIdOfUser = matrixService.getMatrixIdForUser(event.getTarget());
    updateMemberRoleInSpace(space, matrixIdOfUser, SIMPLE_USER_ROLE);
  }

  private boolean updateMemberRoleInSpace(Space space, String matrixIdOfUser, String userRole) {
    Room room = matrixService.getRoomBySpace(space);
    String roomId = room.getRoomId();
    if (StringUtils.isNotBlank(roomId)) {
      try {
        // Disable inviting user but for Moderators
        MatrixRoomPermissions matrixRoomPermissions = matrixService.getRoomSettings(roomId);
        if (matrixRoomPermissions != null) {
          if (SIMPLE_USER_ROLE.equals(userRole)) {
            for (MatrixUserPermission userPermission : matrixRoomPermissions.getUsers()) {
              String fullMatrixUserId = "@%s:%s".formatted(matrixIdOfUser, PropertyManager.getProperty(MATRIX_SERVER_NAME));
              if (fullMatrixUserId.equals(userPermission.getUserName())) {
                userPermission.setUserRole(userRole);
              }
            }
          } else {
            MatrixUserPermission matrixUserPermission =
                    new MatrixUserPermission("@%s:%s".formatted(matrixIdOfUser,
                            PropertyManager.getProperty(MATRIX_SERVER_NAME)),
                            userRole);
            matrixRoomPermissions.getUsers().add(matrixUserPermission);
          }
        }
        return matrixService.updateRoomSettings(roomId, matrixRoomPermissions);
      } catch (Exception e) {
        LOG.error("Could not update member roles in the space {}", space.getDisplayName(), e);
      }
    }
    return false;
  }

  @Override
  public void spaceAvatarEdited(SpaceLifeCycleEvent event) {
    if(!matrixService.isServiceAvailable()) {
      return;
    }
    Space space = event.getSpace();
    try {
      Room room = matrixService.getRoomBySpace(space);
      String roomId = room.getRoomId();
      matrixService.updateRoomAvatar(space, roomId);
    } catch (Exception e) {
      LOG.error("Could not update the room avatar on Matrix", e);
    }
  }

  @Override
  public void spaceDescriptionEdited(SpaceLifeCycleEvent event) {
    if(!matrixService.isServiceAvailable()) {
      return;
    }
    Space space = event.getSpace();
    Room room = matrixService.getRoomBySpace(space);
    String roomId = room.getRoomId();    try {
      if (StringUtils.isNotBlank(roomId)) {
        matrixService.updateRoomDescription(roomId, space.getDescription());
      }
    } catch (Exception e) {
      LOG.error("Could not save the description of space {} ", space.getDisplayName(), e);
    }
  }

  @Override
  public void spaceRemoved(SpaceLifeCycleEvent event) {
    if(!matrixService.isServiceAvailable()) {
      return;
    }
    Space space = event.getSpace();
    Room room = matrixService.getRoomBySpace(space);
    String roomId = room.getRoomId();
    if (StringUtils.isNotBlank(roomId)) {
      try {
        matrixService.deleteRoom(roomId);
      } catch (Exception e) {
        LOG.error("Could not delete the room {} linked to the space {}", roomId, space.getDisplayName());
      }
    }
  }
}
