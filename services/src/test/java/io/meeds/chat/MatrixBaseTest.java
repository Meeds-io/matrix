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

import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.MatrixUserPermission;
import io.meeds.chat.service.MatrixService;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.kernel.test.AbstractSpringTest;
import io.meeds.kernel.test.KernelExtension;
import io.meeds.spring.AvailableIntegration;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.component.test.ConfigurationUnit;
import org.exoplatform.component.test.ConfiguredBy;
import org.exoplatform.component.test.ContainerScope;
import org.exoplatform.component.test.KernelBootstrap;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.jpa.search.ProfileSearchConnector;
import org.exoplatform.social.core.jpa.storage.RDBMSIdentityStorageImpl;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.storage.cache.CachedIdentityStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith({ SpringExtension.class, KernelExtension.class })
@SpringBootApplication(scanBasePackages = { MatrixBaseTest.MODULE_NAME, AvailableIntegration.KERNEL_TEST_MODULE,
    AvailableIntegration.JPA_MODULE, AvailableIntegration.LIQUIBASE_MODULE, AvailableIntegration.WEB_MODULE, "io.meeds.pwa" })
@EnableJpaRepositories(basePackages = MatrixBaseTest.MODULE_NAME)
@TestPropertySource(properties = { "spring.liquibase.change-log=" + MatrixBaseTest.CHANGELOG_PATH,
    "spring.profiles.active=matrix", })
@ConfiguredBy({ @ConfigurationUnit(scope = ContainerScope.ROOT, path = "conf/configuration.xml"),
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/configuration.xml"),
    @ConfigurationUnit(scope = ContainerScope.PORTAL, path = "conf/portal/matrix-test-configuration.xml"), })
public class MatrixBaseTest extends AbstractSpringTest {

  public static final String     MODULE_NAME    = "io.meeds.chat";

  public List<Space>             spacesToDelete = new ArrayList<>();

  public List<String>            roomsToDelete  = new ArrayList<>();

  public String                  matrixRoomId   = "!thisIsACreatedRoom:matrix.meeds.tn";

  public String                  accessToken    = "ThisIsAnAccessToken";

  @Autowired
  public SpaceService            spaceService;

  @Autowired
  public CachedIdentityStorage   identityStorage;

  @Autowired
  public MatrixService           matrixService;

  @MockBean
  public ProfileSearchConnector  profileSearchConnector;

  @MockBean
  public MatrixHttpClient        matrixHttpClient;

  private static KernelBootstrap bootstrap;

  public static final String     CHANGELOG_PATH = "classpath:db/changelog/matrix-rdbms.db.changelog-master.xml";

  @BeforeAll
  static void beforeAll() {
    PropertyManager.setProperty(MATRIX_JWT_SECRET, "ThisIsAJWTSecretOfMatrixForTestingPurposes");
    PropertyManager.setProperty(MATRIX_SERVER_URL, "https://matrix.exo.tn");
    PropertyManager.setProperty(MATRIX_SERVER_NAME, "matrix.exo.tn");
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
  void setUp() throws Exception {
    begin();
    PropertyManager.setProperty(MATRIX_ADMIN_USERNAME, "demo");
    when(profileSearchConnector.search(any(), any(), any(), anyLong(), anyLong())).thenReturn(List.of("1", "2"));
    when(profileSearchConnector.count(any(), any(), any())).thenReturn(2);
    ((RDBMSIdentityStorageImpl) identityStorage.getStorage()).setProfileSearchConnector(profileSearchConnector);
    when(matrixHttpClient.getAdminAccessToken(anyString())).thenReturn(accessToken);

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
    when(matrixHttpClient.saveUserAccount(any(), anyString(), anyBoolean(), anyString())).thenReturn("@demo:matrix.meeds.tn");
    when(matrixHttpClient.saveUserAccount(any(),
                                          anyString(),
                                          anyBoolean(),
                                          anyString(),
                                          anyBoolean(),
                                          anyBoolean())).thenReturn("@demo:matrix.meeds.tn");
  }

  @AfterEach
  void tearDown() {
    for (Space space : spacesToDelete) {
      try {
        this.spaceService.deleteSpace(space);
      } catch (Exception e) {
        // Nothing to do
      }
    }
    for (String roomId : roomsToDelete) {
      try {
        this.matrixService.deleteRoom(roomId);
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
