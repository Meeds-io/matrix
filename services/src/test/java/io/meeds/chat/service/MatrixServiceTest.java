package io.meeds.chat.service;

import io.meeds.chat.MatrixBaseTest;
import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.MatrixUserPermission;
import io.meeds.chat.rest.model.RoomList;
import io.meeds.chat.service.utils.MatrixHttpClient;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.jpa.search.ProfileSearchConnector;
import org.exoplatform.social.core.jpa.storage.RDBMSIdentityStorageImpl;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.storage.cache.CachedIdentityStorage;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(MatrixBaseTest.class)
class MatrixServiceTest extends MatrixBaseTest {

  @MockBean
  MatrixHttpClient       matrixHttpClient;

  @Autowired
  MatrixService          matrixService;

  @Autowired
  SpaceService           spaceService;

  @Autowired
  IdentityManager        identityManager;

  @Autowired
  CachedIdentityStorage  identityStorage;

  @MockBean
  ProfileSearchConnector profileSearchConnector;

  @Test
  void init() {
    try {
      this.matrixService.init();
    } catch (Exception e) {
      fail();
    }
  }

  @Test
  void updateUserDisplayName() {
  }

  @Test
  void processRooms() {
    Profile demoIProfile = identityManager.getOrCreateUserIdentity("demo").getProfile();
    demoIProfile.setProperty(USER_MATRIX_ID, "demo");
    identityManager.updateProfile(demoIProfile);
    RoomList roomList = createRoomsList(5);
    String initialContent = roomList.getRooms().getFirst().getLastMessage().getContent();
    roomList = this.matrixService.processRooms(roomList, "root");
    assertNotNull(roomList);
    assertEquals(5, roomList.getRooms().size());
    assertNotEquals(initialContent, roomList.getRooms().getFirst().getLastMessage().getContent());
  }

  @BeforeEach
  void setUp() throws Exception {
    getContainer();
    begin();
    when(profileSearchConnector.search(any(), any(), any(), anyLong(), anyLong())).thenReturn(List.of("1", "2"));
    when(profileSearchConnector.count(any(), any(), any())).thenReturn(2);
    ((RDBMSIdentityStorageImpl) identityStorage.getStorage()).setProfileSearchConnector(profileSearchConnector);
    when(matrixHttpClient.getAdminAccessToken(anyString())).thenReturn("ThisIsAnAccessToken");
  }

  @AfterEach
  void tearDown() {
    end();
  }

  @Test
  void createRoom() throws Exception {
    String matrixRoomId = "!thisIsACreatedRoom:matrix.exo.tn";
    when(matrixHttpClient.createRoom(anyString(), anyString(), anyString())).thenReturn(matrixRoomId);
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
    Space space = getSpaceInstance(1);
    String returnedMatrixRoomId = matrixService.createRoom(space);
    assertNotNull(matrixRoomId);
    assertEquals(matrixRoomId, returnedMatrixRoomId);
  }

  private Space getSpaceInstance(int number) {
    Space space = new Space();
    space.setDisplayName("my space " + number);
    space.setPrettyName(space.getDisplayName());
    space.setRegistration(Space.OPEN);
    space.setDescription("add new space " + number);
    space.setVisibility(Space.PUBLIC);
    space.setRegistration(Space.VALIDATION);
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
    return createdSpace;
  }

    @Test
    void updateUserPresence() throws JsonException, IOException, InterruptedException {
      when(matrixHttpClient.setUserPresence(anyString(), anyString(), anyString(), anyString())).thenReturn("online");

      String presence = matrixService.updateUserPresence("@user:matrix.exo.tn", "online", "I am available");
      assertNotNull(presence);
      assertEquals("online", presence);

      when(matrixHttpClient.setUserPresence(anyString(), anyString(), anyString(), anyString())).thenThrow(new JsonException("Error"));

      presence = matrixService.updateUserPresence("@user:matrix.exo.tn", "online", "I am available");
      assertNull(presence);
    }
}
