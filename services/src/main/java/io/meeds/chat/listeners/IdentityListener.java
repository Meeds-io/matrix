package io.meeds.chat.listeners;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.service.utils.MatrixConstants;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profile.ProfileLifeCycleEvent;
import org.exoplatform.social.core.profile.ProfileListenerPlugin;
import org.exoplatform.social.core.storage.api.IdentityStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IdentityListener extends ProfileListenerPlugin {

  @Autowired
  private IdentityStorage identityStorage;

  @Autowired
  private IdentityManager identityManager;

  @Autowired
  private MatrixService   matrixService;

  @PostConstruct
  public void init() {
    this.identityManager.registerProfileListener(this);
  }

  @Override
  public void avatarUpdated(ProfileLifeCycleEvent event) {
    Profile profile = event.getProfile();
    FileItem avatarFileItem = identityStorage.getAvatarFile(profile.getIdentity());
    String mimeType = "image/jpg";
    if (avatarFileItem != null && avatarFileItem.getFileInfo() != null) {
      if(!"application/octet-stream".equals(avatarFileItem.getFileInfo().getMimetype())) {
        mimeType = avatarFileItem.getFileInfo().getMimetype();
      }
      String userMatrixID = (String) profile.getProperty(MatrixConstants.USER_MATRIX_ID);
      String userAvatarUrl = matrixService.uploadFileOnMatrix("avatar-of-" + event.getUsername(), mimeType, avatarFileItem.getAsByte());
      if(StringUtils.isNotBlank(userMatrixID) && StringUtils.isNotBlank(userAvatarUrl)) {
        matrixService.updateUserAvatar(userMatrixID, userAvatarUrl);
      }
    }
  }
}
