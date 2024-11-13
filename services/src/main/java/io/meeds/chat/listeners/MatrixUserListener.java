package io.meeds.chat.listeners;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.service.utils.MatrixConstants;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserEventListener;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;

@Component
public class MatrixUserListener extends UserEventListener {

  @Autowired
  private IdentityManager identityManager;

  @Autowired
  private MatrixService   matrixService;

  @Autowired
  private OrganizationService organizationService;

  @PostConstruct
  public void init() {
    this.organizationService.getUserHandler().addUserEventListener(this);
  }

  @Override
  public void postSave(User user, boolean isNew) throws Exception {
    if(identityManager != null) {
      Profile userProfile = identityManager.getProfile(identityManager.getOrCreateUserIdentity(user.getUserName()));
      String matrixId = matrixService.saveUserAccount(user, isNew);
      if(StringUtils.isNotBlank(matrixId) && userProfile.getProperty(USER_MATRIX_ID) == null || StringUtils.isBlank(userProfile.getProperty(USER_MATRIX_ID).toString())) {
        userProfile.getProperties().put(USER_MATRIX_ID, matrixId);
        identityManager.updateProfile(userProfile);
      }
    }
  }

  @Override
  public void postSetEnabled(User user) throws Exception {
    String matrixUsername = "@" + user.getUserName() + ":" + PropertyManager.getProperty(MatrixConstants.MATRIX_SERVER_NAME);
    matrixService.disableAccount(matrixUsername);
  }


}
