package io.meeds.chat.listeners;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.security.ConversationRegistry;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;

@Component
public class MatrixUserLoginListener extends Listener<ConversationRegistry, ConversationState> {

  private static final Log    LOG = ExoLogger.getLogger(MatrixUserLoginListener.class);

  @Autowired
  private IdentityManager     identityManager;

  @Autowired
  private MatrixService       matrixService;

  @Autowired
  private OrganizationService organizationService;

  @Autowired
  private ListenerService     listenerService;

  @PostConstruct
  public void init() {
    listenerService.addListener("exo.core.security.ConversationRegistry.register", this);
  }

  public void onEvent(Event<ConversationRegistry, ConversationState> event) {
    RequestLifeCycle.begin(PortalContainer.getInstance());
    String userId = event.getData().getIdentity().getUserId();
    try {
      if(identityManager != null) {
        Profile userProfile = identityManager.getProfile(identityManager.getOrCreateUserIdentity(userId));
        String matrixUserId = (String) userProfile.getProperty(USER_MATRIX_ID);
        if(StringUtils.isBlank(matrixUserId)) {
          User user = organizationService.getUserHandler().findUserByName(userId);
          String matrixId = matrixService.saveUserAccount(user, true, false);
          userProfile.getProperties().put(USER_MATRIX_ID, matrixId);
          identityManager.updateProfile(userProfile);
        }
      }
    } catch (Exception e) {
      LOG.error("Could not add matrix information for user {}", userId, e);
    } finally {
      RequestLifeCycle.end();
    }
  }
}
