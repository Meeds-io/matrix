package io.meeds.chat.listeners;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.security.ConversationRegistry;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_RESTRICTED_USERS_GROUP;

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

  @Autowired
  private UserACL             userACL;

  @PostConstruct
  public void init() {
    listenerService.addListener("exo.core.security.ConversationRegistry.register", this);
  }

  public void onEvent(Event<ConversationRegistry, ConversationState> event) {
    RequestLifeCycle.begin(PortalContainer.getInstance());
    String userId = event.getData().getIdentity().getUserId();
    Identity connectedUserIdentity = event.getData().getIdentity();
    String matrixRestrictedGroup = PropertyManager.getProperty(MATRIX_RESTRICTED_USERS_GROUP);
    if (StringUtils.isNotBlank(matrixRestrictedGroup) && !userACL.isUserInGroup(connectedUserIdentity, matrixRestrictedGroup)) {
      return;
    }
    try {
      String matrixUserId = matrixService.getMatrixIdForUser(userId);
      if (StringUtils.isBlank(matrixUserId)) {
        User user = organizationService.getUserHandler().findUserByName(userId);
        matrixService.saveUserAccount(user, true, false);
      }
    } catch (Exception e) {
      LOG.error("Could not add matrix information for user {}", userId, e);
    } finally {
      RequestLifeCycle.end();
    }
  }
}
