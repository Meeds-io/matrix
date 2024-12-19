package io.meeds.chat.listeners;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.service.utils.MatrixConstants;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserEventListener;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static io.meeds.chat.service.utils.MatrixConstants.*;

@Component
public class MatrixUserListener extends UserEventListener {

  @Autowired
  private IdentityManager     identityManager;

  @Autowired
  private MatrixService       matrixService;

  @Autowired
  private OrganizationService organizationService;

  @Autowired
  private SpaceService        spaceService;

  @PostConstruct
  public void init() {
    this.organizationService.getUserHandler().addUserEventListener(this);
  }

  @Override
  public void postSave(User user, boolean isNew) throws Exception {
    String matrixRestrictedGroup = PropertyManager.getProperty(MATRIX_RESTRICTED_USERS_GROUP);
    if (StringUtils.isNotBlank(matrixRestrictedGroup)
        && !this.matrixService.isUserMemberOfGroup(user.getUserName(), matrixRestrictedGroup)) {
      return;
    }
    matrixService.saveUserAccount(user, isNew, false);
  }

  @Override
  public void postSetEnabled(User user) throws Exception {
    if (identityManager != null) {
      Profile userProfile = identityManager.getProfile(identityManager.getOrCreateUserIdentity(user.getUserName()));
      String matrixUserId = (String) userProfile.getProperty(USER_MATRIX_ID);
      if (StringUtils.isNotBlank(matrixUserId)) {
        if (!user.isEnabled()) {
          String matrixUsername =
                                "@" + user.getUserName() + ":" + PropertyManager.getProperty(MatrixConstants.MATRIX_SERVER_NAME);
          matrixService.disableAccount(matrixUsername);
        } else {
          matrixService.saveUserAccount(user, false, true);
          ListAccess<Space> spaces = spaceService.getMemberSpaces(user.getUserName());
          Space[] spacesArray = spaces.load(0, spaces.getSize());
          for (Space space : spacesArray) {
            String roomId = matrixService.getRoomBySpace(space);
            if (StringUtils.isNotBlank(roomId)) {
              matrixService.joinUserToRoom(roomId, matrixUserId);
            }
          }

        }
      }
    }
  }
}
