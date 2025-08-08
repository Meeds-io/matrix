package io.meeds.chat.listeners;

import io.meeds.chat.MatrixBaseTest;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.mockito.ArgumentMatchers.*;

@SpringJUnitConfig(MatrixBaseTest.class)
class MatrixListenerTest extends MatrixBaseTest {
  @Autowired
  private OrganizationService organizationService;

  @Autowired
  ListenerService             listenerService;

  @Autowired
  IdentityManager identityManager;

  @Test
  void testUserListener() throws Exception {
    User user = organizationService.getUserHandler().findUserByName("raul");
    organizationService.getUserHandler().saveUser(user, true);
    Mockito.verify(matrixHttpClient, Mockito.times(1))
           .saveUserAccount(any(), anyString(), anyBoolean(), anyString(), anyBoolean(), anyBoolean());
    // Check disabling user
    organizationService.getUserHandler().setEnabled("raul", false, true);
    Mockito.verify(matrixHttpClient, Mockito.times(1)).disableAccount(anyString(), anyBoolean(), anyString());

    // Check enabling user
    organizationService.getUserHandler().setEnabled("raul", true, true);
    Mockito.verify(matrixHttpClient, Mockito.times(2))
            .saveUserAccount(any(), anyString(), anyBoolean(), anyString(), anyBoolean(), anyBoolean());
  }

  @Test
  void testUserLoginListener() {
    Identity identity = new Identity("ghost");
    ConversationState state = new ConversationState(identity);
    listenerService.broadcast("exo.core.security.ConversationRegistry.register", this, state);
    Mockito.verify(matrixHttpClient, Mockito.times(1))
            .saveUserAccount(any(), anyString(), anyBoolean(), anyString(), anyBoolean(), anyBoolean());
  }

}
