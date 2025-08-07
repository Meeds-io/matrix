package io.meeds.chat.service;

import io.meeds.chat.MatrixBaseTest;
import io.meeds.chat.entity.RoomStatus;
import io.meeds.chat.model.Room;
import io.meeds.chat.rest.model.LastMessage;
import io.meeds.chat.rest.model.RoomEntity;
import io.meeds.chat.rest.model.RoomList;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.model.AvatarAttachment;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(MatrixBaseTest.class)
class MatrixServiceTest extends MatrixBaseTest {

  @Autowired
  MatrixService   matrixService;

  @Autowired
  IdentityManager identityManager;

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

  @Test
  void createRoom() throws Exception {
    Space space = getSpaceInstance(1);
    Room spaceRoom = matrixService.getRoomBySpace(space);
    assertNotNull(spaceRoom);
    assertEquals(matrixRoomId, spaceRoom.getRoomId());
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
    directMessagingRoom.setSecondParticipant("ghost");
    Room createdRoom = matrixService.createDirectMessagingRoom(directMessagingRoom);
    assertNotNull(createdRoom);
    assertNotEquals(0, createdRoom.getId());
    assertEquals(directMessagingRoom.getRoomId(), createdRoom.getRoomId());
  }

  @Test
  void getById() throws Exception {
    Space space = getSpaceInstance(1);
    String roomId = matrixService.getRoomBySpace(space).getRoomId();
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

  @Test
  void findUserByMatrixId() throws IOException, InterruptedException {
    String useridOnMatrix = "@demo:matrix.meeds.tn";
    String jsonResponse = """
        {
          "name": "@test:matrix.meeds.tn",
          "admin": false,
          "deactivated": false,
          "displayname": "Test User",
          "avatar_url": null,
          "threepids": [
            {
              "medium": "email",
              "address": "test@meeds.com",
              "validated_at": 1745230543328,
              "added_at": 1745230543328
            }
          ],
        }""";

    when(matrixHttpClient.getUser("@test:matrix.meeds.tn", accessToken)).thenReturn(jsonResponse);
    String result = matrixService.findUserByMatrixId("@test:matrix.meeds.tn");// user not found in organization service, we will
                                                                              // return his Matrix ID
    assertNotNull(result);
    assertEquals("@test:matrix.meeds.tn", result);

    jsonResponse = """
        {
          "name": "@demo:matrix.meeds.tn",
          "admin": false,
          "deactivated": false,
          "displayname": "Demo User",
          "avatar_url": null,
          "threepids": [
            {
              "medium": "email",
              "address": "demo@localhost",
              "validated_at": 1745230543328,
              "added_at": 1745230543328
            }
          ],
        }""";
    when(matrixHttpClient.getUser(useridOnMatrix, accessToken)).thenReturn(jsonResponse);
    result = matrixService.findUserByMatrixId("@demo:matrix.meeds.tn");
    assertNotNull(result);
    assertEquals("demo", result);
  }

  @Test
  void invalidateAccessToken() throws IOException, InterruptedException {
    String accessToken = "sys_sampleAccessToken";
    when(matrixHttpClient.invalidateAccessToken(accessToken)).thenReturn(true);
    boolean result = matrixService.invalidateAccessToken("sys_sampleAccessToken");
    assertTrue(result);

    when(matrixHttpClient.invalidateAccessToken(accessToken)).thenThrow(new InterruptedException());
    result = matrixService.invalidateAccessToken("sys_sampleAccessToken");
    assertFalse(result);
  }
}
