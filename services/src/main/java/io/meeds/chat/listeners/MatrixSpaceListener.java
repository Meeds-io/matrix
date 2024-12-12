package io.meeds.chat.listeners;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.MatrixUserPermission;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
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
    Space space = event.getSpace();
    try {
      String teamDisplayName = space.getDisplayName();
      String description = space.getDescription() != null ? space.getDescription() : "";
      String matrixRoomId = matrixService.createRoom(teamDisplayName, description);

      if (StringUtils.isNotBlank(matrixRoomId)) {
        matrixService.createMatrixRoom(space, matrixRoomId);
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

        // Disable inviting user but for Moderators
        MatrixRoomPermissions matrixRoomPermissions = matrixService.getRoomSettings(matrixRoomId);
        matrixRoomPermissions.setInvite(ADMIN_ROLE);
        matrixService.updateRoomSettings(matrixRoomId, matrixRoomPermissions);
      }
    } catch (Exception e) {
      LOG.error("Matrix integration: Could not create a room for space {}", space.getDisplayName(), e);
    }
  }

  @Override
  public void spaceRenamed(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String spaceDisplayName = space.getDisplayName();
    String roomId = matrixService.getRoomBySpace(space);
    if (StringUtils.isNotBlank(roomId)) {
      matrixService.renameRoom(roomId, spaceDisplayName);
    }
  }

  @Override
  public void joined(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String userId = event.getTarget();
    String restrictedGroupOfUsers = PropertyManager.getProperty(MATRIX_RESTRICTED_USERS_GROUP);
    String matrixIdOfUser = matrixService.getMatrixIdForUser(userId);
    if (StringUtils.isBlank(matrixIdOfUser) && StringUtils.isNotBlank(restrictedGroupOfUsers)
        && restrictedGroupOfUsers.equals(space.getGroupId())) {
      User user;
      try {
        user = organizationService.getUserHandler().findUserByName(userId);
        matrixIdOfUser = matrixService.saveUserAccount(user, true, false);
      } catch (Exception e) {
        LOG.error("Could not retrieve the user {}", userId, e);
      }
    }
    String roomId = matrixService.getRoomBySpace(space);
    if (StringUtils.isNotBlank(roomId) && StringUtils.isNotBlank(matrixIdOfUser)) {
      matrixService.joinUserToRoom(roomId, matrixIdOfUser);
    }
  }

  @Override
  public void left(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String userId = event.getTarget();
    String roomId = matrixService.getRoomBySpace(space);
    String matrixIdOfUser = matrixService.getMatrixIdForUser(userId);
    if (StringUtils.isNotBlank(roomId) && StringUtils.isNotBlank(matrixIdOfUser)) {
      matrixService.kickUserFromRoom(roomId, matrixIdOfUser, MESSAGE_USER_KICKED_SPACE.formatted(space.getDisplayName()));
    }
  }

  @Override
  public void grantedLead(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String matrixIdOfUser = matrixService.getMatrixIdForUser(event.getTarget());
    updateMemberRoleInSpace(space, matrixIdOfUser, MANAGER_ROLE);
  }

  @Override
  public void revokedLead(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String matrixIdOfUser = matrixService.getMatrixIdForUser(event.getTarget());
    updateMemberRoleInSpace(space, matrixIdOfUser, SIMPLE_USER_ROLE);
  }

  private boolean updateMemberRoleInSpace(Space space, String matrixIdOfUser, String userRole) {
    String roomId = null;
    roomId = matrixService.getRoomBySpace(space);

    if (StringUtils.isNotBlank(roomId)) {
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
    }
    return false;
  }

  @Override
  public void spaceAvatarEdited(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String mimeType = "image/jpg";
    String roomId = matrixService.getRoomBySpace(space);
    if (StringUtils.isNotBlank(roomId) && space.getAvatarAttachment() != null
        && space.getAvatarAttachment().getImageBytes() != null) {
      byte[] imageBytes = space.getAvatarAttachment().getImageBytes();
      if (!"application/octet-stream".equals(space.getAvatarAttachment().getMimeType())) {
        mimeType = space.getAvatarAttachment().getMimeType();
      }
      String avatarURL = matrixService.uploadFileOnMatrix(space.getAvatarAttachment().getFileName(), mimeType, imageBytes);
      matrixService.updateRoomAvatar(roomId, avatarURL);
    }
  }

  @Override
  public void spaceDescriptionEdited(SpaceLifeCycleEvent event) {
    Space space = event.getSpace();
    String roomId = matrixService.getRoomBySpace(space);
    if (StringUtils.isNotBlank(roomId)) {
      matrixService.updateRoomDescription(roomId, space.getDescription());
    }
  }
}
