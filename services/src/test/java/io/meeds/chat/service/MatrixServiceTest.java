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
package io.meeds.chat.service;

import io.meeds.chat.MatrixBaseTest;
import io.meeds.chat.entity.RoomStatus;
import io.meeds.chat.model.ChatConversation;
import io.meeds.chat.model.ChatMessage;
import io.meeds.chat.model.ChatUnread;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.MatrixUnreadRoom;
import io.meeds.chat.service.utils.MatrixUnauthorizedException;
import io.meeds.chat.model.MatrixRoomPermissions;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;
import static org.junit.Assert.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
  void getUserConversations() throws Exception {
    // A space "demo" is a member of -> exposed as a "space" conversation
    Space space = getSpaceInstance(1);
    Room spaceRoom = matrixService.getRoomBySpace(space);
    assertNotNull(spaceRoom);

    // A direct message room between "demo" and "dragon" -> a "dm" conversation.
    // Unique participant pair + room id so it does not collide with other tests' DMs
    // (DM rooms persist in the RDBMS across test methods).
    String dmRoomId = "!getUserConversationsDmRoom:matrix.meeds.tn";
    Room dm = new Room();
    dm.setRoomId(dmRoomId);
    dm.setFirstParticipant("demo");
    dm.setSecondParticipant("dragon");
    matrixService.createDirectMessagingRoom(dm);

    List<ChatConversation> conversations = matrixService.getUserConversations("demo");
    assertNotNull(conversations);

    ChatConversation spaceConv = conversations.stream()
                                              .filter(c -> "space".equals(c.getType())
                                                  && spaceRoom.getRoomId().equals(c.getRoomId()))
                                              .findFirst()
                                              .orElse(null);
    assertNotNull(spaceConv);
    assertEquals(space.getDisplayName(), spaceConv.getTitle());
    assertEquals(Long.valueOf(space.getId()), spaceConv.getSpaceId());

    ChatConversation dmConv = conversations.stream()
                                           .filter(c -> "dm".equals(c.getType()) && dmRoomId.equals(c.getRoomId()))
                                           .findFirst()
                                           .orElse(null);
    assertNotNull(dmConv);
    assertNull(dmConv.getSpaceId());
    assertTrue(dmConv.getTitle() != null && !dmConv.getTitle().isBlank());

    // Blank user -> empty list (no NPE)
    assertTrue(matrixService.getUserConversations("  ").isEmpty());
  }

  @Test
  void getRoomMessages() throws Exception {
    Space space = getSpaceInstance(1);
    Room spaceRoom = matrixService.getRoomBySpace(space);
    String roomId = spaceRoom.getRoomId();

    // "dragon" needs a Matrix account so the service can mint a user token to read as them
    Identity actingIdentity = identityManager.getOrCreateUserIdentity("dragon");
    actingIdentity.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(actingIdentity.getProfile());

    // Synapse admin API returns events newest-first (dir=b)
    MatrixMessage newer = new MatrixMessage();
    newer.setSender("@demo:matrix.exo.tn");
    newer.setMessageContent("second message");
    newer.setTimeStamp(2000L);
    MatrixMessage older = new MatrixMessage();
    older.setSender("@ghost:matrix.exo.tn");
    older.setMessageContent("first message");
    older.setTimeStamp(1000L);
    when(matrixHttpClient.getRoomMessages(eq(roomId), anyInt(), anyString())).thenReturn(List.of(newer, older));

    List<ChatMessage> messages = matrixService.getRoomMessages("dragon", roomId, 50);
    assertEquals(2, messages.size());
    // Returned chronologically: oldest first, sender mapped to its local part
    assertEquals("first message", messages.get(0).getText());
    assertEquals("ghost", messages.get(0).getSender());
    assertEquals(1000L, messages.get(0).getTimestamp());
    assertEquals("second message", messages.get(1).getText());
    assertEquals("demo", messages.get(1).getSender());

    // Access guard: a room the user does not participate in -> empty, no Synapse read
    List<ChatMessage> denied = matrixService.getRoomMessages("dragon", "!notMyRoom", 50);
    assertTrue(denied.isEmpty());
    verify(matrixHttpClient, never()).getRoomMessages(eq("!notMyRoom"), anyInt(), anyString());
  }

  @Test
  void getUnreadConversations() throws Exception {
    Space space = getSpaceInstance(1);
    String roomId = matrixService.getRoomBySpace(space).getRoomId();

    Identity actingIdentity = identityManager.getOrCreateUserIdentity("dragon");
    actingIdentity.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(actingIdentity.getProfile());

    MatrixMessage missed = new MatrixMessage();
    missed.setSender("@ghost:matrix.exo.tn");
    missed.setMessageContent("are you there?");
    missed.setTimeStamp(3000L);
    when(matrixHttpClient.getUnreadRooms(anyString(),
                                         anyInt())).thenReturn(List.of(new MatrixUnreadRoom(roomId, 2, List.of(missed))));

    List<ChatUnread> unread = matrixService.getUnreadConversations("dragon");
    assertEquals(1, unread.size());
    ChatUnread conversation = unread.get(0);
    assertEquals(roomId, conversation.getRoomId());
    assertEquals(space.getDisplayName(), conversation.getTitle());
    assertEquals(2, conversation.getUnreadCount());
    assertEquals(1, conversation.getMessages().size());
    assertEquals("are you there?", conversation.getMessages().get(0).getText());
    assertEquals("ghost", conversation.getMessages().get(0).getSender());
  }

  @Test
  void sendMessage() throws Exception {
    Space space = getSpaceInstance(1);
    String roomId = matrixService.getRoomBySpace(space).getRoomId();

    Identity actingIdentity = identityManager.getOrCreateUserIdentity("dragon");
    actingIdentity.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(actingIdentity.getProfile());

    when(matrixHttpClient.sendMessage(eq(roomId), eq("hello team"), anyString(), anyString())).thenReturn("$sentEventId");

    String eventId = matrixService.sendMessage("dragon", roomId, "hello team");
    assertEquals("$sentEventId", eventId);

    // Access guard: cannot send to a room the user does not participate in
    assertNull(matrixService.sendMessage("dragon", "!notMyRoom", "hello"));
    verify(matrixHttpClient, never()).sendMessage(eq("!notMyRoom"), anyString(), anyString(), anyString());
  }

  @Test
  void getRoomMessagesRetriesOnExpiredToken() throws Exception {
    Space space = getSpaceInstance(1);
    String roomId = matrixService.getRoomBySpace(space).getRoomId();

    Identity actingIdentity = identityManager.getOrCreateUserIdentity("dragon");
    actingIdentity.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(actingIdentity.getProfile());

    MatrixMessage message = new MatrixMessage();
    message.setSender("@demo:matrix.exo.tn");
    message.setMessageContent("after refresh");
    message.setTimeStamp(1000L);
    // First read is rejected (stale token); after a token refresh the retry succeeds
    when(matrixHttpClient.getRoomMessages(eq(roomId), anyInt(), anyString())).thenThrow(new MatrixUnauthorizedException("token expired"))
                                                                            .thenReturn(List.of(message));

    List<ChatMessage> messages = matrixService.getRoomMessages("dragon", roomId, 50);
    assertEquals(1, messages.size());
    assertEquals("after refresh", messages.get(0).getText());
    // The read was attempted twice and a fresh token was minted for the retry
    verify(matrixHttpClient, times(2)).getRoomMessages(eq(roomId), anyInt(), anyString());
    verify(matrixHttpClient, atLeastOnce()).getAccessToken(anyString());
  }

  @Test
  void getRoomMessagesNormalizesFullDirectMessageRoomId() throws Exception {
    // DM rooms are stored with the FULL id ("!id:server"), unlike space rooms (local part).
    // The participant guard must normalize BOTH the incoming id and the stored id; otherwise
    // DM conversations are denied. The room suffix here matches MATRIX_SERVER_NAME so
    // extractRoomId actually strips (the other fixtures use a non-matching suffix, which is
    // exactly why the asymmetry slipped through).
    String fullDmRoomId = "!normalizedDmRoom:matrix.exo.tn";
    Room dm = new Room();
    dm.setRoomId(fullDmRoomId);
    dm.setFirstParticipant("dragon");
    dm.setSecondParticipant("tom");
    matrixService.createDirectMessagingRoom(dm);

    Identity actingIdentity = identityManager.getOrCreateUserIdentity("dragon");
    actingIdentity.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(actingIdentity.getProfile());

    MatrixMessage message = new MatrixMessage();
    message.setSender("@tom:matrix.exo.tn");
    message.setMessageContent("hi from a DM");
    message.setTimeStamp(1000L);
    // The HTTP layer is always called with the normalized local part, never the full id
    when(matrixHttpClient.getRoomMessages(eq("!normalizedDmRoom"), anyInt(), anyString())).thenReturn(List.of(message));

    // The agent passes the full id, exactly as list_chat_conversations returns it for a DM
    List<ChatMessage> messages = matrixService.getRoomMessages("dragon", fullDmRoomId, 50);
    assertEquals(1, messages.size());
    assertEquals("hi from a DM", messages.get(0).getText());
    assertEquals("tom", messages.get(0).getSender());
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
    // FIXME Test disabled due to execution order test fail
    //verify(matrixHttpClient, times(1)).kickUserFromRoom(anyString(), anyString(), anyString(), anyString());
    matrixService.enableSpaceChat(space, true);
    //verify(matrixHttpClient, times(2)).joinUserToRoom(anyString(), anyString(), anyString());
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

  @Test
  void testCleanMatrixUsername() {
    String[] usernames = new String[] { "Samueâl", "fre@d", "Shazia", "gorkef/",
            "²&é\"'(-è_çà)=²1234567890°+'azertyuiopqsdfghjklmù*^$wxcvbn,;:!?./§%µ¨£<>²&~#{[|`\\^@]}" };
    for (String username : usernames) {
      String result = matrixService.cleanMatrixUsername(username);
      Assertions.assertNotNull(result);
    }
  }

  @Test
  void testLeftSpace() {
    Space space = getSpaceInstance(1);
    spaceService.removeMember(space, "dragon");
    verify(matrixHttpClient, times(1)).kickUserFromRoom(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void testRenameSpace() {
    Space space = getSpaceInstance(1);
    spaceService.renameSpace(space, "New Space Name");
    verify(matrixHttpClient, times(1)).renameRoom(anyString(), anyString(), anyString());
  }

  @Test
  void testPromoteAndRevokeLead() throws JsonException, IOException, InterruptedException {
    Space space = getSpaceInstance(1);
    spaceService.setManager(space, "dragon", true);
    // function already
    verify(matrixHttpClient, times(4)).updateRoomSettings(anyString(), any(MatrixRoomPermissions.class), anyString());

    //revoke lead
    spaceService.setManager(space, "dragon", false);

    verify(matrixHttpClient, times(5)).updateRoomSettings(anyString(), any(MatrixRoomPermissions.class), anyString());
  }

  @Test
  void testSpaceDescriptionEdited() {
    Space space = getSpaceInstance(1);
    space.setDescription("New space description");
    spaceService.updateSpace(space);
    verify(matrixHttpClient, times(1)).updateRoomDescription(anyString(), anyString(), anyString());
  }

}
