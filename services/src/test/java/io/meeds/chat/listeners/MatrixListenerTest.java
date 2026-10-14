package io.meeds.chat.listeners;

import io.meeds.chat.MatrixBaseTest;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_RESTRICTED_USERS_GROUP;
import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;
import static org.mockito.ArgumentMatchers.*;

class MatrixListenerTest extends MatrixBaseTest {
  @Autowired
  private OrganizationService organizationService;

  @Autowired
  ListenerService             listenerService;

  @Autowired
  IdentityManager             identityManager;

  @Test
  void testUserListener() throws Exception {
    User user = organizationService.getUserHandler().findUserByName("raul");
    organizationService.getUserHandler().saveUser(user, true);
    Mockito.verify(matrixHttpClient, Mockito.times(1))
           .saveUserAccount(any(), anyString(), anyBoolean(), anyString(), anyBoolean(), anyBoolean());
    // Check disabling user
    Mockito.clearInvocations(agentPusherService);
    organizationService.getUserHandler().setEnabled("raul", false, true);
    Mockito.verify(matrixHttpClient, Mockito.times(1)).disableAccount(anyString(), anyBoolean(), anyString());
    Mockito.verify(agentPusherService, Mockito.never()).enqueueIfDesired(anyString());

    // Check enabling user: a re-enabled digital worker's pusher turn is queued after the re-activation
    organizationService.getUserHandler().setEnabled("raul", true, true);
    Mockito.verify(matrixHttpClient, Mockito.times(2))
           .saveUserAccount(any(), anyString(), anyBoolean(), anyString(), anyBoolean(), anyBoolean());
    Mockito.verify(agentPusherService, Mockito.times(1)).enqueueIfDesired("raul");
  }

  @Test
  void testUserLoginListener() {
    Identity identity = new Identity("ghost");
    ConversationState state = new ConversationState(identity);
    org.exoplatform.social.core.identity.model.Identity ghostIdentity = identityManager.getOrCreateUserIdentity("ghost");
    Profile profile = ghostIdentity.getProfile();
    profile.setProperty(USER_MATRIX_ID, "");
    identityManager.updateProfile(profile);
    listenerService.broadcast("exo.core.security.ConversationRegistry.register", this, state);
    Mockito.verify(matrixHttpClient, Mockito.times(1))
           .saveUserAccount(any(), anyString(), anyBoolean(), anyString(), anyBoolean(), anyBoolean());
  }


  /** Where chat is restricted, a user outside the groups gets no chat account on save or login. */
  @Test
  void restrictedChatSkipsUsersOutsideTheGroups() throws Exception {
    String restriction = org.exoplatform.commons.utils.PropertyManager.getProperty(MATRIX_RESTRICTED_USERS_GROUP);
    org.exoplatform.commons.utils.PropertyManager.setProperty(MATRIX_RESTRICTED_USERS_GROUP, "/platform/administrators");
    try {
      Mockito.clearInvocations(matrixHttpClient);
      organizationService.getUserHandler().saveUser(organizationService.getUserHandler().findUserByName("raul"), true);

      org.exoplatform.social.core.identity.model.Identity ghostIdentity = identityManager.getOrCreateUserIdentity("ghost");
      ghostIdentity.getProfile().setProperty(USER_MATRIX_ID, "");
      identityManager.updateProfile(ghostIdentity.getProfile());
      listenerService.broadcast("exo.core.security.ConversationRegistry.register", this, new ConversationState(new Identity("ghost")));

      Mockito.verify(matrixHttpClient, Mockito.never())
             .saveUserAccount(any(), anyString(), anyBoolean(), anyString(), anyBoolean(), anyBoolean());
    } finally {
      org.exoplatform.commons.utils.PropertyManager.setProperty(MATRIX_RESTRICTED_USERS_GROUP, restriction == null ? "" : restriction);
    }
  }
}
