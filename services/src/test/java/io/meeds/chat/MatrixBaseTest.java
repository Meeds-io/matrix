/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.chat;

import static io.meeds.chat.service.utils.MatrixConstants.MANAGER_ROLE;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_ADMIN_USERNAME;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_ASYNC_ENABLED;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_JWT_SECRET;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_SERVER_NAME;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_SERVER_URL;
import static io.meeds.chat.service.utils.MatrixConstants.SIMPLE_USER_ROLE;
import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.component.test.ConfigurationUnit;
import org.exoplatform.component.test.ConfiguredBy;
import org.exoplatform.component.test.ContainerScope;
import org.exoplatform.component.test.KernelBootstrap;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.jpa.search.ProfileSearchConnector;
import org.exoplatform.social.core.jpa.storage.RDBMSIdentityStorageImpl;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.storage.api.IdentityStorage;

import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.MatrixUserPermission;
import io.meeds.chat.service.MatrixService;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.kernel.test.AbstractSpringTest;
import io.meeds.kernel.test.KernelExtension;
import io.meeds.spring.AvailableIntegration;

@ExtendWith({
  SpringExtension.class,
  KernelExtension.class,
  MockitoExtension.class
})
@SpringBootApplication(scanBasePackages = {
  MatrixBaseTest.MODULE_NAME,
  "io.meeds.pwa",
  AvailableIntegration.KERNEL_TEST_MODULE,
  AvailableIntegration.JPA_MODULE,
  AvailableIntegration.LIQUIBASE_MODULE,
  AvailableIntegration.WEB_MODULE,
})
@EnableJpaRepositories(basePackages = MatrixBaseTest.MODULE_NAME)
@TestPropertySource(properties = {
  "spring.liquibase.change-log=" + MatrixBaseTest.CHANGELOG_PATH,
  "spring.profiles.active=matrix",
})
@ConfiguredBy({
  @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
  @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/matrix-test-configuration.xml"),
})
public class MatrixBaseTest extends AbstractSpringTest {

  public static final String     MODULE_NAME    = "io.meeds.chat";

  public List<Space>             spacesToDelete = new ArrayList<>();

  public String                  matrixRoomId   = "!thisIsACreatedRoom:matrix.meeds.tn";

  public String                  accessToken    = "ThisIsAnAccessToken";

  @Autowired
  public SpaceService            spaceService;

  @Autowired
  public IdentityStorage         identityStorage;

  @Autowired
  public IdentityManager         identityManager;

  @Autowired
  public MatrixService           matrixService;

  @MockitoBean
  public ProfileSearchConnector  profileSearchConnector;

  @MockitoBean
  public MatrixHttpClient        matrixHttpClient;

  private static KernelBootstrap bootstrap;

  public static final String     CHANGELOG_PATH = "classpath:db/changelog/matrix-rdbms.db.changelog-master.xml";

  @BeforeAll
  static void beforeAll() {
    PropertyManager.setProperty(MATRIX_JWT_SECRET, "ThisIsAJWTSecretOfMatrixForTestingPurposes");
    PropertyManager.setProperty(MATRIX_SERVER_URL, "https://matrix.exo.tn");
    PropertyManager.setProperty(MATRIX_SERVER_NAME, "matrix.exo.tn");
    PropertyManager.setProperty(MATRIX_ADMIN_USERNAME, "root");
    // run space chat enable/disable side effects synchronously in tests: the
    // production code spawns them on a detached thread that tearDown() never
    // waits on, so a leftover thread from one test can race with the next
    // one's own profile updates (e.g. USER_MATRIX_ID) under certain
    // execution orders.
    PropertyManager.setProperty(MATRIX_ASYNC_ENABLED, "false");
  }

  public PortalContainer getContainer() {
    return bootstrap == null ? bootContainer() : bootstrap.getContainer();
  }

  protected PortalContainer bootContainer() {
    PortalContainer container = bootContainer(getClass());
    ExoContainerContext.setCurrentContainer(container);
    return container;
  }

  @BeforeEach
  protected void setUp() throws Exception {
    begin();
    PropertyManager.setProperty(MATRIX_ADMIN_USERNAME, "demo");
    // "demo" is reused suite-wide both as a regular space member (expecting a
    // matrixId derived from the per-test mock, e.g. "@demo:matrix.meeds.tn")
    // and, since it's set as MATRIX_ADMIN_USERNAME above, as the account that
    // matrixService.init() bootstraps using the real MATRIX_SERVER_NAME
    // (e.g. "@demo:matrix.exo.tn" - see MatrixServiceTest#init). Its profile
    // is a suite-wide singleton, and saveUserAccount() never overwrites an
    // already-set matrixId (correct for a real, immutable Matrix account), so
    // whichever of those two flows runs first for "demo" would otherwise
    // stick for every later test in the same JVM fork. Reset it before each
    // test so every test starts from its own expectations.
    Profile demoProfile = identityManager.getOrCreateUserIdentity("demo").getProfile();
    if (demoProfile.getProperty(USER_MATRIX_ID) != null) {
      demoProfile.getProperties().remove(USER_MATRIX_ID);
      identityManager.updateProfile(demoProfile);
    }
    when(profileSearchConnector.search(any(), any(), any(), anyLong(), anyLong())).thenReturn(List.of("1", "2"));
    when(profileSearchConnector.count(any(), any(), any())).thenReturn(2);
    if (identityStorage instanceof RDBMSIdentityStorageImpl rdbmsIdentityStorageImpl) {
      rdbmsIdentityStorageImpl.setProfileSearchConnector(profileSearchConnector);
    }
    when(matrixHttpClient.getAccessToken(anyString())).thenReturn(accessToken);

    when(matrixHttpClient.createRoom(anyString(), anyString(), anyString())).thenReturn(matrixRoomId);
    when(matrixHttpClient.deleteRoom(anyString(), anyString())).thenReturn(true);
    MatrixUserPermission matrixUserPermission = new MatrixUserPermission();
    matrixUserPermission.setUserName("demo");
    matrixUserPermission.setUserRole(MANAGER_ROLE);
    MatrixUserPermission raulUserPermission = new MatrixUserPermission();
    raulUserPermission.setUserName("raul");
    raulUserPermission.setUserRole(SIMPLE_USER_ROLE);
    MatrixRoomPermissions matrixRoomPermissions = new MatrixRoomPermissions();
    matrixRoomPermissions.setUsers(new ArrayList(List.of(new MatrixUserPermission[] { matrixUserPermission,
        raulUserPermission })));
    when(matrixHttpClient.getRoomSettings(anyString(), anyString())).thenReturn(matrixRoomPermissions);
    when(matrixHttpClient.saveUserAccount(any(), anyString(), anyBoolean(), anyString())).thenAnswer(invocation -> {
      String matrixUserId = invocation.getArgument(1);
      return "@" + matrixUserId + ":matrix.meeds.tn";
    });
    when(matrixHttpClient.saveUserAccount(any(),
                                          anyString(),
                                          anyBoolean(),
                                          anyString(),
                                          anyBoolean(),
                                          anyBoolean())).thenAnswer(invocation -> {
                                            String matrixUserId = invocation.getArgument(1);
                                            return "@" + matrixUserId + ":matrix.meeds.tn";
                                          });
    when(matrixHttpClient.getAccessToken(anyString())).thenReturn(accessToken);
  }

  @AfterEach
  protected void tearDown() {
    for (Space space : spacesToDelete) {
      try {
        this.spaceService.deleteSpace(space);
      } catch (Exception e) {
        // Nothing to do
      }
    }
    end();
  }

  protected void begin() {
    PortalContainer container = getContainer();
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }

  protected void end() {
    RequestLifeCycle.end();
  }

  protected Space getSpaceInstance(int number) {
    Space space = new Space();
    space.setDisplayName("my space " + number);
    space.setPrettyName(space.getDisplayName());
    space.setRegistration(Space.OPEN);
    space.setDescription("add new space " + number);
    space.setVisibility(Space.PUBLIC);
    space.setRegistration(Space.VALIDATION);
    Identity spaceIdentity = new Identity();
    spaceIdentity.setRemoteId(space.getPrettyName());
    spaceIdentity.setProviderId(SpaceIdentityProvider.NAME);
    identityStorage.saveIdentity(spaceIdentity);
    Space createdSpace = this.spaceService.createSpace(space, "root");
    String[] managers = new String[] { "demo", "tom" };
    String[] members = new String[] { "demo", "raul", "ghost", "dragon" };
    String[] invitedUsers = new String[] { "register1", "mary" };
    String[] pendingUsers = new String[] { "jame", "paul", "hacker" };
    Arrays.stream(pendingUsers).forEach(u -> spaceService.addPendingUser(createdSpace, u));
    Arrays.stream(invitedUsers).forEach(u -> spaceService.addInvitedUser(createdSpace, u));
    Arrays.stream(members).forEach(u -> spaceService.addMember(createdSpace, u));
    Arrays.stream(managers).forEach(u -> spaceService.addMember(createdSpace, u));
    Arrays.stream(managers).forEach(u -> spaceService.setManager(createdSpace, u, true));
    spacesToDelete.add(createdSpace);
    return createdSpace;
  }
}
