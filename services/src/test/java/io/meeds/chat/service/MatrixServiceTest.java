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
import io.meeds.chat.model.ChatSearchResult;
import io.meeds.chat.model.ChatUnread;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.MatrixUnreadRoom;
import io.meeds.chat.service.utils.MatrixUnauthorizedException;
import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.Room;
import io.meeds.chat.service.model.ChatSettingsEntity;
import io.meeds.chat.service.model.ChatSettings;
import io.meeds.chat.service.model.LastMessage;
import io.meeds.chat.service.model.RoomEntity;
import io.meeds.chat.service.model.RoomList;
import io.meeds.portal.navigation.model.NavigationConfiguration;
import io.meeds.portal.navigation.model.TopbarApplication;
import io.meeds.portal.navigation.model.TopbarConfiguration;
import io.meeds.portal.navigation.service.NavigationConfigurationService;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.model.AvatarAttachment;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_CONNECTION_RETRY_ATTEMPTS;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_CONNECTION_RETRY_DELAY;
import static io.meeds.chat.service.utils.MatrixConstants.SPACE_CHAT_AUTHORIZED;
import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;
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
  void initRetriesUntilMatrixServiceIsAvailable() throws Exception {
    PropertyManager.setProperty(MATRIX_CONNECTION_RETRY_ATTEMPTS, "5");
    PropertyManager.setProperty(MATRIX_CONNECTION_RETRY_DELAY, "0");
    ReflectionTestUtils.setField(matrixService, "matrixAccessToken", null);
    clearInvocations(matrixHttpClient);
    // Matrix is unreachable for the first two attempts, then becomes operational
    when(matrixHttpClient.getAccessToken(anyString())).thenThrow(new IOException("Connection refused"))
                                                      .thenThrow(new IOException("Connection refused"))
                                                      .thenReturn(accessToken);

    matrixService.init();

    assertTrue(matrixService.isServiceAvailable());
    verify(matrixHttpClient, times(3)).getAccessToken(anyString());
  }

  @Test
  void initFailsFastOnConfigurationError() throws Exception {
    PropertyManager.setProperty(MATRIX_CONNECTION_RETRY_ATTEMPTS, "5");
    PropertyManager.setProperty(MATRIX_CONNECTION_RETRY_DELAY, "0");
    ReflectionTestUtils.setField(matrixService, "matrixAccessToken", null);
    clearInvocations(matrixHttpClient);
    // a non-transient configuration error must not be retried
    when(matrixHttpClient.getAccessToken(anyString())).thenThrow(new IllegalArgumentException("The URL of the Matrix server is required"));

    matrixService.init();

    assertFalse(matrixService.isServiceAvailable());
    verify(matrixHttpClient, times(1)).getAccessToken(anyString());

    doReturn(accessToken).when(matrixHttpClient).getAccessToken(anyString());
    ReflectionTestUtils.setField(matrixService, "matrixAccessToken", null);
    matrixService.init();
    assertTrue(matrixService.isServiceAvailable());
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
  void searchChatMessages() throws Exception {
    Space space = getSpaceInstance(1);
    Room spaceRoom = matrixService.getRoomBySpace(space);
    String roomId = spaceRoom.getRoomId();

    Identity actingIdentity = identityManager.getOrCreateUserIdentity("dragon");
    actingIdentity.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(actingIdentity.getProfile());

    MatrixMessage hit = new MatrixMessage();
    hit.setRoomId(roomId);
    hit.setEventId("$hitEvent1");
    hit.setSender("@ghost:matrix.exo.tn");
    hit.setMessageContent("the release date is friday");
    hit.setTimeStamp(4000L);
    when(matrixHttpClient.searchMessages(eq("release"), any(), anyInt(), anyString())).thenReturn(List.of(hit));

    // Global search across the user's conversations: hit mapped + title resolved
    List<ChatSearchResult> results = matrixService.searchChatMessages("dragon", "release", null, 20);
    assertEquals(1, results.size());
    ChatSearchResult result = results.get(0);
    assertEquals("$hitEvent1", result.getEventId());
    assertEquals("the release date is friday", result.getText());
    assertEquals("ghost", result.getSender());
    assertEquals(4000L, result.getTimestamp());
    assertEquals(space.getDisplayName(), result.getConversationTitle());

    // Scoped to a conversation the user participates in still works
    assertEquals(1, matrixService.searchChatMessages("dragon", "release", roomId, 20).size());

    // Access guard: scoping to a room the user is not in -> empty, no Synapse search
    assertTrue(matrixService.searchChatMessages("dragon", "release", "!notMyRoom", 20).isEmpty());
    verify(matrixHttpClient, never()).searchMessages(anyString(), eq("!notMyRoom"), anyInt(), anyString());

    // Blank query -> empty list (no NPE, no search)
    assertTrue(matrixService.searchChatMessages("dragon", "  ", null, 20).isEmpty());
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
  void getRoomMessagesReInterruptsOnInterruptedException() throws Exception {
    Space space = getSpaceInstance(1);
    String roomId = matrixService.getRoomBySpace(space).getRoomId();

    Identity actingIdentity = identityManager.getOrCreateUserIdentity("dragon");
    actingIdentity.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(actingIdentity.getProfile());

    when(matrixHttpClient.getRoomMessages(eq(roomId), anyInt(), anyString())).thenThrow(new InterruptedException("interrupted"));

    // The thread must not stay flagged before we assert on the re-interrupt below
    assertFalse(Thread.currentThread().isInterrupted());
    List<ChatMessage> messages = matrixService.getRoomMessages("dragon", roomId, 50);
    // The failure is swallowed into the fallback (empty list), never propagated
    assertNotNull(messages);
    assertTrue(messages.isEmpty());
    // ...but the interrupt status is preserved so callers up the stack can react to it
    assertTrue(Thread.interrupted(), "The interrupt status must be restored (and is cleared here for the next test)");
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

    String raulMatrixId = "@raul:matrix.exo.tn";
    Profile raulProfile = identityManager.getOrCreateUserIdentity("raul").getProfile();
    raulProfile.setProperty(USER_MATRIX_ID, raulMatrixId);
    identityManager.updateProfile(raulProfile);

    clearInvocations(matrixHttpClient);
    matrixService.enableSpaceChat(space, false);
    verify(matrixHttpClient, times(1)).updateRoomSettings(eq(matrixRoomId), any(), eq(accessToken));
    verify(matrixHttpClient, never()).joinUserToRoom(anyString(), anyString(), anyString());
    assertEquals(RoomStatus.DISABLED.name(), matrixService.getById(matrixRoomId, true).getStatus());

    clearInvocations(matrixHttpClient);
    matrixService.enableSpaceChat(space, true);
    verify(matrixHttpClient, times(1)).joinUserToRoom(eq(matrixRoomId), eq(raulMatrixId), eq(accessToken));
    assertEquals(RoomStatus.ENABLED.name(), matrixService.getById(matrixRoomId).getStatus());
  }

  @Test
  void isUserMemberOfRoom() throws JsonException, IOException, InterruptedException {
    when(matrixHttpClient.isUserMemberOfRoom("roomId", "userId", accessToken)).thenReturn(true);
    assertTrue(matrixService.isUserMemberOfRoom("roomId", "userId"));

    when(matrixHttpClient.isUserMemberOfRoom("roomId", "userId", accessToken)).thenReturn(false);
    assertFalse(matrixService.isUserMemberOfRoom("roomId", "userId"));

    verify(matrixHttpClient, times(2)).isUserMemberOfRoom("roomId", "userId", accessToken);
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

    when(matrixHttpClient.getOverriddenRateLimitForUser(admin, accessToken)).thenReturn("");
    matrixService.overrideAdminRateLimit(admin);
    verify(matrixHttpClient, times(2)).overrideRateLimitForUser(admin, 0, 0, accessToken);
  }

  @Test
  void isChatAuthorizedByAdministration() {
    Space space = new Space();
    assertTrue(matrixService.isChatAuthorizedByAdministration(space));

    space.setExtendedProperties(Map.of());
    assertTrue(matrixService.isChatAuthorizedByAdministration(space));

    space.setExtendedProperties(Map.of(SPACE_CHAT_AUTHORIZED, "true"));
    assertTrue(matrixService.isChatAuthorizedByAdministration(space));

    space.setExtendedProperties(Map.of(SPACE_CHAT_AUTHORIZED, "false"));
    assertFalse(matrixService.isChatAuthorizedByAdministration(space));
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

  @Test
  void loadChatSettings() {
    NavigationConfigurationService navigationConfigurationService = mock(NavigationConfigurationService.class);
    ExoContainerContext.getCurrentContainer().registerComponentInstance(navigationConfigurationService);
    NavigationConfiguration navigationConfiguration = new NavigationConfiguration();
    List<TopbarApplication> applications = new ArrayList<>();
    TopbarApplication chatApp = new TopbarApplication();
    chatApp.setId("chat");
    applications.add(chatApp);
    navigationConfiguration.setTopbar(new TopbarConfiguration());
    navigationConfiguration.getTopbar().setApplications(applications);
    when(navigationConfigurationService.getConfiguration()).thenReturn(navigationConfiguration);
    matrixService.saveChatSettings(new ChatSettings(false, false, false));

    ChatSettingsEntity chatSettings = matrixService.loadChatSettings();
    assertFalse(chatSettings.isChatEnabled());
    assertFalse(chatSettings.isSpaceRoomsEnabled());
    assertFalse(chatSettings.isPrivateRoomsEnabled());

    matrixService.saveChatSettings(new ChatSettings(true, true, true));

    chatSettings = matrixService.loadChatSettings();
    assertTrue(chatSettings.isChatEnabled());
    assertTrue(chatSettings.isSpaceRoomsEnabled());
    assertTrue(chatSettings.isPrivateRoomsEnabled());
  }

  @Test
  void changeChatRoomReadability() throws Exception {
    Space space = getSpaceInstance(1);
    Room room = matrixService.getRoomBySpace(space);
    verify(matrixHttpClient, times(3)).updateRoomSettings(anyString(), any(MatrixRoomPermissions.class), anyString());
    matrixService.changeChatRoomReadability(room.getRoomId(), true);
    // 4 times including
    verify(matrixHttpClient, times(4)).updateRoomSettings(anyString(), any(MatrixRoomPermissions.class), anyString());
    matrixService.changeChatRoomReadability(room.getRoomId(), false);
    // 5 times including
    verify(matrixHttpClient, times(5)).updateRoomSettings(anyString(), any(MatrixRoomPermissions.class), anyString());
  }
}
