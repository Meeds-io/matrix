package io.meeds.chat.listeners;

import jakarta.annotation.PostConstruct;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profile.ProfileLifeCycleEvent;
import org.exoplatform.social.core.profile.ProfileListenerPlugin;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;

@Component
public class IdentityListener extends ProfileListenerPlugin {

  private static final Log LOG = ExoLogger.getLogger(MatrixService.class);

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
    String userMatrixId = (String) profile.getProperty(USER_MATRIX_ID);
    try {
      matrixService.updateUserAvatar(profile, userMatrixId);
    } catch (JsonException | IOException | InterruptedException e) {
      LOG.error("Could not update the avatar of the user {} on Matrix", profile.getFullName(), e);
    }
  }
}
