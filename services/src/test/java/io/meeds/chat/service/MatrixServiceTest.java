package io.meeds.chat.service;

import io.meeds.chat.MatrixBaseTest;
import io.meeds.chat.entity.RoomStatus;
import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.MatrixUserPermission;
import io.meeds.chat.model.Room;
import io.meeds.chat.rest.model.LastMessage;
import io.meeds.chat.rest.model.Message;
import io.meeds.chat.rest.model.RoomEntity;
import io.meeds.chat.rest.model.RoomList;
import io.meeds.chat.service.utils.MatrixHttpClient;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.jpa.search.ProfileSearchConnector;
import org.exoplatform.social.core.jpa.storage.RDBMSIdentityStorageImpl;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.model.AvatarAttachment;
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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

  private List<Space>    spacesToDelete = new ArrayList<>();

  private List<String>   roomsToDelete  = new ArrayList<>();

  private String         matrixRoomId   = "!thisIsACreatedRoom:matrix.meeds.tn";

  private String         accessToken    = "ThisIsAnAccessToken";

  @Test
  void init() {
    try {
      this.matrixService.init();
    } catch (Exception e) {
      fail();
    }
  }

  @Test
  void updateUserDisplayName() throws JsonException, IOException, InterruptedException {
    String userId = "@testuser:matrix.meeds.com";
    when(matrixHttpClient.getUserDisplayName(eq(userId), anyString())).thenReturn("Test User");
    matrixService.updateUserDisplayName(userId, "Chat Bot");
    verify(matrixHttpClient, times(1)).updateUserDisplayName(userId, "Chat Bot", accessToken);
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

  @Test
  void createRoom() throws Exception {
    Space space = getSpaceInstance(1);
    Room spaceRoom = matrixService.getRoomBySpace(space);
    assertNotNull(spaceRoom);
    roomsToDelete.add(spaceRoom.getRoomId());
    assertEquals(matrixRoomId, spaceRoom.getRoomId());
  }

  private Space getSpaceInstance(int number) {
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

  @Test
  void updateUserPresence() throws JsonException, IOException, InterruptedException {
    when(matrixHttpClient.setUserPresence(anyString(), anyString(), anyString(), anyString())).thenReturn("online");

    String presence = matrixService.updateUserPresence("@user:matrix.meeds.tn", "online", "I am available");
    assertNotNull(presence);
    assertEquals("online", presence);

    when(matrixHttpClient.setUserPresence(anyString(),
                                          anyString(),
                                          anyString(),
                                          anyString())).thenThrow(new JsonException("Error"));

    presence = matrixService.updateUserPresence("@user:matrix.meeds.tn", "online", "I am available");
    assertNull(presence);
  }

  public RoomList createRoomsList(int numberOfRooms) {
    List<RoomEntity> rooms = new ArrayList<>();
    for (int i = 0; i < numberOfRooms; i++) {
      Space space = getSpaceInstance(i);
      Room room = matrixService.getRoomBySpace(space);
      RoomEntity roomEntity = toRoomEntity(room, space);
      rooms.add(roomEntity);
    }
    RoomList roomList = new RoomList();
    roomList.setTotalUnreadMessages(20);
    roomList.setRooms(rooms);
    return roomList;
  }

  public RoomEntity toRoomEntity(Room room, Space space) {
    RoomEntity roomEntity = new RoomEntity();
    roomEntity.setId(room.getRoomId());
    roomEntity.setTopic(space.getDescription());
    roomEntity.setAvatarUrl(space.getAvatarUrl());
    LastMessage lastMessage = new LastMessage();
    lastMessage.setContent("last message of " + space.getDisplayName());
    lastMessage.setSender("root");
    roomEntity.setLastMessage(lastMessage);
    roomEntity.setUnreadMessages(5);
    roomEntity.setDirectChat(false);
    return roomEntity;
  }

  @Test
  void getRoomBySpace() throws Exception {
    Space space = getSpaceInstance(1);
    Room room = matrixService.getRoomBySpace(space);
    roomsToDelete.add(room.getRoomId());
    assertNotNull(room);
    assertEquals(matrixRoomId, room.getRoomId());
  }

  @Test
  void updateUserAvatar() throws JsonException, IOException, InterruptedException {
    Profile demoProfile = identityManager.getOrCreateUserIdentity("demo").getProfile();
    String demoIdOnMatrix = "@demo:matrix.meeds.tn";

    InputStream inputStream = getClass().getClassLoader().getResourceAsStream("meeds.png");

    AvatarAttachment attachment = new AvatarAttachment(null, "meeds.png", "image/png", inputStream, System.currentTimeMillis());
    when(matrixHttpClient.uploadFile("avatar-of-demo.jpg",
                                     "image/png",
                                     attachment.getImageBytes(),
                                     accessToken)).thenReturn("/This/Is/An/URL/Of/AVATAR");
    demoProfile.setProperty(Profile.AVATAR, attachment);
    identityStorage.saveProfile(demoProfile);

    demoProfile = identityStorage.loadProfile(demoProfile);

    matrixService.updateUserAvatar(demoProfile, demoIdOnMatrix);
    verify(matrixHttpClient, times(1)).updateUserAvatar(anyString(), anyString(), eq(accessToken));
  }

  @Test
  void updateRoomAvatar() throws Exception {
    Space space = getSpaceInstance(1);
    String roomId = matrixService.createRoom(space);
    roomsToDelete.add(roomId);
    InputStream inputStream = getClass().getClassLoader().getResourceAsStream("meeds.png");

    AvatarAttachment attachment = new AvatarAttachment(null, "meeds.png", "image/png", inputStream, System.currentTimeMillis());
    space.setAvatarAttachment(attachment);
    spaceService.updateSpaceAvatar(space, "demo");
    when(matrixHttpClient.uploadFile("avatar-space-my_space_1.png",
                                     "image/png",
                                     attachment.getImageBytes(),
                                     accessToken)).thenReturn("/This/Is/An/URL/Of/AVATAR");

    matrixService.updateRoomAvatar(space, matrixRoomId);
    verify(matrixHttpClient, times(1)).updateRoomAvatar(eq(matrixRoomId), anyString(), eq(accessToken));
  }

  @Test
  void saveUserAccount() throws JsonException, IOException, InterruptedException {
    Identity demoIdentity = identityManager.getOrCreateUserIdentity("demo");
    String userIdOnMatrix = matrixService.saveUserAccount(demoIdentity, true);
    assertNotNull(userIdOnMatrix);
    assertEquals("@demo:matrix.meeds.tn", userIdOnMatrix);
  }

  @Test
  void createDirectMessagingRoom() throws ObjectAlreadyExistsException {
    Room directMessagingRoom = new Room();
    directMessagingRoom.setRoomId("!ThisIsARoomId:matrix.meeds.tn");
    directMessagingRoom.setFirstParticipant("demo");
    directMessagingRoom.setSecondParticipant("raul");
    Room createdRoom = matrixService.createDirectMessagingRoom(directMessagingRoom);
    roomsToDelete.add(createdRoom.getRoomId());
    assertNotNull(createdRoom);
    assertNotEquals(0, createdRoom.getId());
    assertEquals(directMessagingRoom.getRoomId(), createdRoom.getRoomId());
  }

  @Test
  void getById() throws Exception {
    Space space = getSpaceInstance(1);
    String roomId = matrixService.createRoom(space);
    roomsToDelete.add(roomId);
    assertNotNull(roomId);
    Room room = matrixService.getById(roomId);
    assertNotNull(room);
    String splitRoomId = roomId.substring(0, roomId.indexOf(":"));
    Room room1 = matrixService.getById(splitRoomId);
    assertNotNull(room1);
  }

  @Test
  void enableSpaceChat() throws Exception {
    Space space = getSpaceInstance(1);
    roomsToDelete.add(matrixRoomId);
    Room room = matrixService.getById(matrixRoomId);
    assertEquals(room.getStatus(), RoomStatus.ENABLED.name());
    matrixService.enableSpaceChat(space, false);
    verify(matrixHttpClient, times(1)).kickUserFromRoom(anyString(), anyString(), anyString(), anyString());
    matrixService.enableSpaceChat(space, true);
    verify(matrixHttpClient, times(2)).joinUserToRoom(anyString(), anyString(), anyString());
  }

  @Test
  void overrideAdminRateLimit() throws IOException, InterruptedException {
    String admin = "admin";
    when(matrixHttpClient.getOverriddenRateLimitForUser(admin, accessToken)).thenReturn("""
                                                                                                          {
                                                                                                            "messages_per_second": 0,
                                                                                                            "burst_count": 0
                                                                                                          }""");
    matrixService.overrideAdminRateLimit(admin);
    verify(matrixHttpClient, times(0)).overrideRateLimitForUser(admin, 0, 0, accessToken);

    when(matrixHttpClient.getOverriddenRateLimitForUser(admin, accessToken)).thenReturn("""
                                                                                                          {
                                                                                                            "messages_per_second": 10,
                                                                                                            "burst_count": 20
                                                                                                          }""");
    matrixService.overrideAdminRateLimit(admin);
    verify(matrixHttpClient, times(1)).overrideRateLimitForUser(admin, 0, 0, accessToken);
  }
}
