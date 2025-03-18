package io.meeds.chat.upgrade;

import io.meeds.chat.model.Room;
import io.meeds.chat.service.MatrixService;
import io.meeds.chat.service.MatrixSynchronizationService;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.commons.utils.ListAccessImpl;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserHandler;
import org.exoplatform.services.organization.impl.UserImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.SpaceFilter;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatrixRoomAndAccountsUpgradePluginTest {

  private SpaceService                 spaceService;

  private MatrixService                matrixService;

  private MatrixSynchronizationService matrixSynchronizationService;

  private IdentityManager              identityManager;

  private OrganizationService          organizationService;

  @BeforeEach
  void setUp() throws Exception {
    spaceService = mock(SpaceService.class);
    identityManager = mock(IdentityManager.class);
    matrixService = mock(MatrixService.class);
    matrixSynchronizationService = mock(MatrixSynchronizationService.class);
    organizationService = mock(OrganizationService.class);
    UserHandler userHandler = mock(UserHandler.class);
    ListAccess<User> usersListAccess = mock(ListAccess.class);
    when(usersListAccess.getSize()).thenReturn(3);
    User user1 = new UserImpl("user1");
    User user2 = new UserImpl("user2");
    User user3 = new UserImpl("user3");
    Identity user1Identity = mock(Identity.class);
    Identity user2Identity = mock(Identity.class);
    Identity user3Identity = mock(Identity.class);
    Profile user1Profile = mock(Profile.class);
    Profile user2Profile = mock(Profile.class);
    Profile user3Profile = mock(Profile.class);
    when(user1Profile.getProperty(eq(USER_MATRIX_ID))).thenReturn("user1");
    when(user2Profile.getProperty(eq(USER_MATRIX_ID))).thenReturn("user2");
    when(user3Profile.getProperty(eq(USER_MATRIX_ID))).thenReturn("user3");
    when(user1Identity.getProfile()).thenReturn(user1Profile);
    when(user2Identity.getProfile()).thenReturn(user2Profile);
    when(user3Identity.getProfile()).thenReturn(user3Profile);
    when(usersListAccess.load(anyInt(), anyInt())).thenReturn(new User[] { user1, user2, user3 });
    when(userHandler.findAllUsers()).thenReturn(usersListAccess);
    when(organizationService.getUserHandler()).thenReturn(userHandler);

    when(identityManager.getOrCreateUserIdentity(eq("user1"))).thenReturn(user1Identity);
    when(identityManager.getOrCreateUserIdentity(eq("user2"))).thenReturn(user2Identity);
    when(identityManager.getOrCreateUserIdentity(eq("user3"))).thenReturn(user3Identity);

    Space space = new Space();
    space.setId(1);
    space.setMembers(new String[] { "user1", "user2", "user3" });
    ListAccess<Space> spaces = mock(ListAccess.class);
    when(spaces.getSize()).thenReturn(1);
    when(spaces.load(anyInt(), anyInt())).thenReturn(new Space[] { space });
    when(spaceService.getMemberSpaces(anyString())).thenReturn(spaces);
    Room room = new Room();
    room.setRoomId("!ThisIsAnIdentifierOfARoom:matrix.exo.tn");
    room.setSpaceId("1");
    when(matrixService.getRoomBySpace(eq(space))).thenReturn(room);

    // spaces data
    when(spaceService.getAllSpacesByFilter(any())).thenReturn(spaces);

  }

  @Test
  void processUpgrade() {
    InitParams initParams = new InitParams();
    MatrixRoomAndAccountsUpgradePlugin matrixRoomAndAccountsUpgradePlugin =
                                                                          new MatrixRoomAndAccountsUpgradePlugin(initParams,
                                                                                                                 matrixService,
                                                                                                                 matrixSynchronizationService);
    matrixRoomAndAccountsUpgradePlugin.processUpgrade("versionSource", "versionTarget");
  }
}
