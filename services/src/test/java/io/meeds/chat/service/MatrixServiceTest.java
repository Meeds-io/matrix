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
import io.meeds.chat.service.utils.MatrixException;
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
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.model.AvatarAttachment;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.json.JSONObject;
import org.apache.commons.codec.digest.DigestUtils;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.meeds.chat.service.utils.MatrixConstants.CHAT_SETTINGS;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_CONNECTION_RETRY_ATTEMPTS;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_CONNECTION_RETRY_DELAY;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_RESTRICTED_USERS_GROUP;
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

  /** The unread read merges an edit into its original too: no "* " duplicate. */
  @Test
  void getUnreadConversationsMergesEdits() throws Exception {
    Space space = getSpaceInstance(1);
    String roomId = matrixService.getRoomBySpace(space).getRoomId();
    Identity actingIdentity = identityManager.getOrCreateUserIdentity("dragon");
    actingIdentity.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(actingIdentity.getProfile());

    MatrixMessage original = new MatrixMessage();
    original.setEventId("$original");
    original.setSender("@ghost:matrix.exo.tn");
    original.setMessageContent("typo");
    original.setTimeStamp(1000L);
    MatrixMessage plain = new MatrixMessage();
    plain.setEventId("$plain");
    plain.setSender("@demo:matrix.exo.tn");
    plain.setMessageContent("hi");
    plain.setTimeStamp(2000L);
    MatrixMessage edit = new MatrixMessage();
    edit.setEventId("$edit");
    edit.setSender("@ghost:matrix.exo.tn");
    edit.setMessageContent("* fixed");
    edit.setEditedContent("fixed");
    edit.setRelatesToType("m.replace");
    edit.setReplacedEventId("$original");
    edit.setTimeStamp(3000L);
    // the /sync timeline is oldest first
    when(matrixHttpClient.getUnreadRooms(anyString(), anyInt()))
        .thenReturn(List.of(new MatrixUnreadRoom(roomId, 3, List.of(original, plain, edit))));

    List<ChatMessage> messages = matrixService.getUnreadConversations("dragon").get(0).getMessages();

    assertEquals(List.of("fixed", "hi"), messages.stream().map(ChatMessage::getText).toList());
    assertEquals(List.of(1000L, 2000L), messages.stream().map(ChatMessage::getTimestamp).toList());
  }

  /**
   * The bounded event read completes a room ID stored as its local part with the
   * server name, and reads a full room ID as given: Synapse finds an event only
   * under its full room ID.
   */
  @Test
  void getRoomEventOrThrowReadsTheFullRoomId() throws Exception {
    Duration timeout = Duration.ofSeconds(10);
    when(matrixHttpClient.getAccessToken(anyString(), eq(timeout))).thenReturn(accessToken);
    MatrixMessage message = new MatrixMessage();
    message.setType("m.room.message");
    when(matrixHttpClient.getEventById(eq("$event"), anyString(), anyString(), eq(timeout))).thenReturn(message);

    assertSame(message, matrixService.getRoomEventOrThrow("$event", "!abc", timeout));
    verify(matrixHttpClient).getEventById(eq("$event"), eq("!abc:matrix.exo.tn"), anyString(), eq(timeout));
    assertSame(message, matrixService.getRoomEventOrThrow("$event", "!def:other.server", timeout));
    verify(matrixHttpClient).getEventById(eq("$event"), eq("!def:other.server"), anyString(), eq(timeout));
  }

  /**
   * The read anchor's event read, made with the user's own identity, completes a
   * room ID stored as its local part with the server name too.
   */
  @Test
  void getRoomEventAsUserReadsTheFullRoomId() throws Exception {
    Identity actingIdentity = identityManager.getOrCreateUserIdentity("dragon");
    actingIdentity.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(actingIdentity.getProfile());
    when(matrixHttpClient.getAccessToken(anyString())).thenReturn(accessToken);
    MatrixMessage message = new MatrixMessage();
    when(matrixHttpClient.getEventById(eq("$read"), eq("!abc:matrix.exo.tn"), anyString())).thenReturn(message);

    assertSame(message, matrixService.getRoomEventAsUser("dragon", "!abc", "$read"));
  }

  /**
   * The bounded lookup answers the platform login, null for a Matrix account
   * no platform user matches, and propagates a failure.
   */
  @Test
  void findUserByMatrixIdOrThrow() throws Exception {
    Duration timeout = Duration.ofSeconds(10);
    when(matrixHttpClient.getAccessToken(anyString(), eq(timeout))).thenReturn(accessToken);
    when(matrixHttpClient.getUser(eq("@demo:matrix.meeds.tn"), any(), eq(timeout))).thenReturn("""
        {"name":"@demo:matrix.meeds.tn","threepids":[{"medium":"email","address":"demo@localhost"}]}""");
    when(matrixHttpClient.getUser(eq("@stranger:matrix.meeds.tn"), any(), eq(timeout))).thenReturn("""
        {"name":"@stranger:matrix.meeds.tn","threepids":[{"medium":"email","address":"nobody@nowhere.tld"}]}""");
    when(matrixHttpClient.getUser(eq("@slow:matrix.meeds.tn"), any(), eq(timeout))).thenThrow(new IOException("request timed out"));

    assertEquals("demo", matrixService.findUserByMatrixIdOrThrow("@demo:matrix.meeds.tn", timeout));
    assertNull(matrixService.findUserByMatrixIdOrThrow("@stranger:matrix.meeds.tn", timeout));
    assertThrows(IOException.class, () -> matrixService.findUserByMatrixIdOrThrow("@slow:matrix.meeds.tn", timeout));
    verify(matrixHttpClient, never()).getUser(anyString(), anyString());
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
  void searchChatMessagesDropsHitsInRoomsThePlatformDoesNotTrack() throws Exception {
    Space space = getSpaceInstance(1);
    Room spaceRoom = matrixService.getRoomBySpace(space);

    Identity actingIdentity = identityManager.getOrCreateUserIdentity("dragon");
    actingIdentity.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(actingIdentity.getProfile());

    MatrixMessage mine = new MatrixMessage();
    mine.setRoomId(spaceRoom.getRoomId());
    mine.setEventId("$mine");
    mine.setSender("@ghost:matrix.exo.tn");
    mine.setMessageContent("release notes");
    mine.setTimeStamp(4000L);
    // Matrix also answers for a room the user belongs to on Matrix but the platform does not
    // track — created from another client, or left behind by a failed space kick.
    MatrixMessage untracked = new MatrixMessage();
    untracked.setRoomId("!roomThePlatformIgnores");
    untracked.setEventId("$untracked");
    untracked.setSender("@ghost:matrix.exo.tn");
    untracked.setMessageContent("release notes elsewhere");
    untracked.setTimeStamp(5000L);
    when(matrixHttpClient.searchMessages(eq("release"), any(), anyInt(), anyString())).thenReturn(List.of(untracked,
                                                                                                         mine));

    List<ChatSearchResult> results = matrixService.searchChatMessages("dragon", "release", null, 20);

    assertEquals(1, results.size());
    assertEquals("$mine", results.get(0).getEventId());
    assertEquals(space.getDisplayName(), results.get(0).getConversationTitle());
  }

  @Test
  void searchChatMessagesResolvesAConversationTitleOnlyOnce() throws Exception {
    Space space = getSpaceInstance(1);
    Room spaceRoom = matrixService.getRoomBySpace(space);

    Identity actingIdentity = identityManager.getOrCreateUserIdentity("dragon");
    actingIdentity.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(actingIdentity.getProfile());

    MatrixMessage first = new MatrixMessage();
    first.setRoomId(spaceRoom.getRoomId());
    first.setEventId("$first");
    first.setSender("@ghost:matrix.exo.tn");
    first.setMessageContent("release one");
    first.setTimeStamp(4000L);
    MatrixMessage second = new MatrixMessage();
    second.setRoomId(spaceRoom.getRoomId());
    second.setEventId("$second");
    second.setSender("@ghost:matrix.exo.tn");
    second.setMessageContent("release two");
    second.setTimeStamp(3000L);
    when(matrixHttpClient.searchMessages(eq("release"), any(), anyInt(), anyString())).thenReturn(List.of(first,
                                                                                                         second));

    List<ChatSearchResult> results = matrixService.searchChatMessages("dragon", "release", null, 20);

    // Both hits of the same conversation come back carrying the same title, and the conversation
    // is never resolved against Matrix more than once for a single search.
    assertEquals(2, results.size());
    assertEquals(results.get(0).getConversationTitle(), results.get(1).getConversationTitle());
    verify(matrixHttpClient, atMost(1)).getRoomDisplayName(anyString(), anyString(), anyString());
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

  /**
   * The propagating variant mints its token with the bounded login, refreshes
   * a rejected token once, and propagates what the plain variant swallows.
   */
  @Test
  void callAsUserOrThrow() throws Exception {
    Identity dragon = identityManager.getOrCreateUserIdentity("dragon");
    dragon.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(dragon.getProfile());
    Duration timeout = Duration.ofSeconds(10);
    when(matrixHttpClient.getAccessToken(anyString(), eq(timeout))).thenReturn("cachedToken");
    // leaves "cachedToken" in the user's token cache, whatever an earlier test left there
    matrixService.callAsUserOrThrow("dragon", timeout, token -> {
      if (!"cachedToken".equals(token)) {
        throw new MatrixUnauthorizedException("stale");
      }
      return token;
    });
    clearInvocations(matrixHttpClient);
    when(matrixHttpClient.getAccessToken(anyString(), eq(timeout))).thenReturn("freshToken");

    String result = matrixService.callAsUserOrThrow("dragon", timeout, token -> {
      if (!"freshToken".equals(token)) {
        throw new MatrixUnauthorizedException("rejected");
      }
      return token;
    });

    assertEquals("freshToken", result);
    verify(matrixHttpClient, times(1)).getAccessToken(anyString(), eq(timeout));
    verify(matrixHttpClient, never()).getAccessToken(anyString());

    IOException timedOut = new IOException("request timed out");
    assertSame(timedOut, assertThrows(IOException.class, () -> matrixService.callAsUserOrThrow("dragon", timeout, token -> {
      throw timedOut;
    })));
    assertThrows(MatrixUnauthorizedException.class, () -> matrixService.callAsUserOrThrow("dragon", timeout, token -> {
      throw new MatrixUnauthorizedException("rejected again");
    }));

    Identity ghost = identityManager.getOrCreateUserIdentity("ghost");
    ghost.getProfile().setProperty(USER_MATRIX_ID, "");
    identityManager.updateProfile(ghost.getProfile());
    assertThrows(MatrixException.class, () -> matrixService.callAsUserOrThrow("ghost", timeout, token -> token));
  }

  /**
   * Chat is open to everyone without a restriction, to the restricted groups'
   * members, and to the accounts of a declared kind whatever the restriction.
   */
  @Test
  void isChatUser() throws Exception {
    String restriction = PropertyManager.getProperty(MATRIX_RESTRICTED_USERS_GROUP);
    org.exoplatform.services.organization.OrganizationService organizationService =
                                                                               ExoContainerContext.getService(org.exoplatform.services.organization.OrganizationService.class);
    org.exoplatform.services.organization.Group workers = organizationService.getGroupHandler()
                                                                             .findGroupById(io.meeds.chat.listeners.TestWorkerKindPlugin.GROUP_ID);
    if (workers == null) {
      workers = organizationService.getGroupHandler().createGroupInstance();
      workers.setGroupName(io.meeds.chat.listeners.TestWorkerKindPlugin.GROUP_ID.substring(1));
      workers.setLabel("Matrix test workers");
      organizationService.getGroupHandler().addChild(null, workers, true);
      workers = organizationService.getGroupHandler().findGroupById(io.meeds.chat.listeners.TestWorkerKindPlugin.GROUP_ID);
    }
    organizationService.getMembershipHandler()
                       .linkMembership(organizationService.getUserHandler().findUserByName("ghost"),
                                       workers,
                                       organizationService.getMembershipTypeHandler().findMembershipType("member"),
                                       false);
    try {
      PropertyManager.setProperty(MATRIX_RESTRICTED_USERS_GROUP, "");
      assertTrue(matrixService.isChatUser("raul"));

      PropertyManager.setProperty(MATRIX_RESTRICTED_USERS_GROUP, "/platform/administrators");
      assertTrue(matrixService.isChatUser("root"));
      assertFalse(matrixService.isChatUser("raul"));
      assertTrue(matrixService.isChatUser("ghost"));
      assertTrue(matrixService.getAccountKindGroups().contains(io.meeds.chat.listeners.TestWorkerKindPlugin.GROUP_ID));
    } finally {
      PropertyManager.setProperty(MATRIX_RESTRICTED_USERS_GROUP, restriction == null ? "" : restriction);
      org.exoplatform.services.organization.Membership membership =
                                                                organizationService.getMembershipHandler()
                                                                                   .findMembershipByUserGroupAndType("ghost",
                                                                                                                     io.meeds.chat.listeners.TestWorkerKindPlugin.GROUP_ID,
                                                                                                                     "member");
      organizationService.getMembershipHandler().removeMembership(membership.getId(), false);
    }
  }

  // ---- Room read: edits merged -------------------------------------------------------------

  @Test
  void getRoomMessagesMergesEditsIntoTheirOriginal() throws Exception {
    String roomId = actingInSpaceRoom();
    MatrixMessage plain = message("$plain", "@demo:matrix.exo.tn", "untouched", 1000L);
    MatrixMessage edited = message("$edited", "@demo:matrix.exo.tn", "typo", 2000L);
    MatrixMessage withNewContent = edit("$e1", "$edited", "@demo:matrix.exo.tn", "* fixed", "fixed", 3000L);
    MatrixMessage original = message("$legacy", "@ghost:matrix.exo.tn", "old", 4000L);
    MatrixMessage withoutNewContent = edit("$e2", "$legacy", "@ghost:matrix.exo.tn", "* legacy fixed", null, 5000L);
    // newest first, as the client API returns them
    when(matrixHttpClient.getRoomMessages(eq(roomId), anyInt(), anyString())).thenReturn(List.of(withoutNewContent,
                                                                                                withNewContent,
                                                                                                original,
                                                                                                edited,
                                                                                                plain));

    List<ChatMessage> messages = matrixService.getRoomMessages("dragon", roomId, 50);

    assertEquals(List.of("untouched", "fixed", "legacy fixed"), messages.stream().map(ChatMessage::getText).toList());
    assertEquals(List.of(1000L, 2000L, 4000L), messages.stream().map(ChatMessage::getTimestamp).toList());
    assertEquals(List.of("demo", "demo", "ghost"), messages.stream().map(ChatMessage::getSender).toList());
  }

  @Test
  void getRoomMessagesIgnoresAnEditFromAnotherSender() throws Exception {
    String roomId = actingInSpaceRoom();
    MatrixMessage original = message("$orig", "@demo:matrix.exo.tn", "mine", 1000L);
    MatrixMessage forged = edit("$e1", "$orig", "@ghost:matrix.exo.tn", "* forged", "forged", 2000L);
    when(matrixHttpClient.getRoomMessages(eq(roomId), anyInt(), anyString())).thenReturn(List.of(forged, original));

    List<ChatMessage> messages = matrixService.getRoomMessages("dragon", roomId, 50);

    assertEquals(1, messages.size());
    assertEquals("mine", messages.get(0).getText());
    assertEquals("demo", messages.get(0).getSender());
  }

  @Test
  void getRoomMessagesKeepsTheLatestOfSeveralEdits() throws Exception {
    String roomId = actingInSpaceRoom();
    MatrixMessage original = message("$orig", "@demo:matrix.exo.tn", "v0", 1000L);
    MatrixMessage first = edit("$e1", "$orig", "@demo:matrix.exo.tn", "* v1", "v1", 2000L);
    MatrixMessage second = edit("$e2", "$orig", "@demo:matrix.exo.tn", "* v2", "v2", 3000L);
    // the later edit arrives before the earlier one in the page, and again after it
    when(matrixHttpClient.getRoomMessages(eq(roomId), anyInt(), anyString())).thenReturn(List.of(second, first, original));
    assertEquals("v2", matrixService.getRoomMessages("dragon", roomId, 50).get(0).getText());
    when(matrixHttpClient.getRoomMessages(eq(roomId), anyInt(), anyString())).thenReturn(List.of(first, second, original));
    List<ChatMessage> messages = matrixService.getRoomMessages("dragon", roomId, 50);
    assertEquals(1, messages.size());
    assertEquals("v2", messages.get(0).getText());
  }

  @Test
  void getRoomMessagesDropsAnEditWhoseOriginalIsNotInThePage() throws Exception {
    String roomId = actingInSpaceRoom();
    MatrixMessage kept = message("$kept", "@demo:matrix.exo.tn", "kept", 3000L);
    // the original is older than the page, or redacted (the read drops an event without a body)
    MatrixMessage orphan = edit("$e1", "$outOfPageOrRedacted", "@demo:matrix.exo.tn", "* orphan", "orphan", 4000L);
    when(matrixHttpClient.getRoomMessages(eq(roomId), anyInt(), anyString())).thenReturn(List.of(orphan, kept));

    List<ChatMessage> messages = matrixService.getRoomMessages("dragon", roomId, 50);

    assertEquals(1, messages.size());
    assertEquals("kept", messages.get(0).getText());
  }

  @Test
  void getRoomMessagesWithTimeoutMergesEditsAndPropagatesATimeout() throws Exception {
    String roomId = actingInSpaceRoom();
    Duration timeout = Duration.ofSeconds(5);
    MatrixMessage original = message("$orig", "@demo:matrix.exo.tn", "v0", 1000L);
    MatrixMessage edit = edit("$e1", "$orig", "@demo:matrix.exo.tn", "* v1", "v1", 2000L);
    when(matrixHttpClient.getRoomMessages(eq(roomId), anyInt(), anyString(), eq(timeout))).thenReturn(List.of(edit, original));

    List<ChatMessage> messages = matrixService.getRoomMessages("dragon", roomId, 50, timeout);
    assertEquals(1, messages.size());
    assertEquals("v1", messages.get(0).getText());

    when(matrixHttpClient.getRoomMessages(eq(roomId), anyInt(), anyString(), eq(timeout))).thenThrow(new IOException("timed out"));
    assertThrows(IOException.class, () -> matrixService.getRoomMessages("dragon", roomId, 50, timeout));
    verify(matrixHttpClient, never()).getRoomMessages(eq(roomId), anyInt(), anyString());

    // a conversation the user does not participate in is not read
    assertTrue(matrixService.getRoomMessages("dragon", "!notMyRoom", 50, timeout).isEmpty());
    verify(matrixHttpClient, never()).getRoomMessages(eq("!notMyRoom"), anyInt(), anyString(), any(Duration.class));
  }

  @Test
  void getRoomMessagesWithTimeoutAcceptsBothRoomIdShapes() throws Exception {
    // a space room stored as its local part (what createRoom answers after cutting the server name)
    when(matrixHttpClient.createRoom(anyString(), anyString(), anyString())).thenReturn("!localSpaceRoom");
    Room spaceRoom = matrixService.getRoomBySpace(getSpaceInstance(1));
    assertEquals("!localSpaceRoom", spaceRoom.getRoomId());
    // a DM stored with the full id, the server name being the test's
    Room dm = new Room();
    dm.setRoomId("!fullDmRoom:matrix.exo.tn");
    dm.setFirstParticipant("dragon");
    dm.setSecondParticipant("raul");
    matrixService.createDirectMessagingRoom(dm);
    actingAsDragon();
    Duration timeout = Duration.ofSeconds(5);
    MatrixMessage message = message("$m", "@demo:matrix.exo.tn", "hello", 1000L);
    when(matrixHttpClient.getRoomMessages(eq("!localSpaceRoom"), anyInt(), anyString(), eq(timeout))).thenReturn(List.of(message));
    when(matrixHttpClient.getRoomMessages(eq("!fullDmRoom"), anyInt(), anyString(), eq(timeout))).thenReturn(List.of(message));

    assertEquals(1, matrixService.getRoomMessages("dragon", "!localSpaceRoom", 50, timeout).size());
    assertEquals(1, matrixService.getRoomMessages("dragon", "!localSpaceRoom:matrix.exo.tn", 50, timeout).size());
    assertEquals(1, matrixService.getRoomMessages("dragon", "!fullDmRoom", 50, timeout).size());
    assertEquals(1, matrixService.getRoomMessages("dragon", "!fullDmRoom:matrix.exo.tn", 50, timeout).size());
  }

  // ---- sendReply -----------------------------------------------------------------------------

  @Test
  void sendReplyPostsTheComposerContentWithADeterministicTransactionId() throws Exception {
    String roomId = actingInSpaceRoom();
    Duration timeout = Duration.ofSeconds(5);
    ArgumentCaptor<JSONObject> content = ArgumentCaptor.forClass(JSONObject.class);
    ArgumentCaptor<String> transactionId = ArgumentCaptor.forClass(String.class);
    when(matrixHttpClient.sendMessage(eq(roomId), any(JSONObject.class), anyString(), anyString(), eq(timeout))).thenReturn("$reply");

    String eventId = matrixService.sendReply("dragon",
                                             roomId,
                                             "@asker answer",
                                             "<a href=\"https://matrix.to/#/@asker:matrix.exo.tn\">Asker</a> answer",
                                             "$question",
                                             List.of("@asker:matrix.exo.tn"),
                                             timeout);

    assertEquals("$reply", eventId);
    verify(matrixHttpClient).sendMessage(eq(roomId), content.capture(), transactionId.capture(), anyString(), eq(timeout));
    JSONObject sent = content.getValue();
    assertEquals("m.text", sent.getString("msgtype"));
    assertEquals("@asker answer", sent.getString("body"));
    assertEquals("org.matrix.custom.html", sent.getString("format"));
    assertEquals("<a href=\"https://matrix.to/#/@asker:matrix.exo.tn\">Asker</a> answer", sent.getString("formatted_body"));
    assertEquals("$question", sent.getJSONObject("m.relates_to").getJSONObject("m.in_reply_to").getString("event_id"));
    assertEquals(List.of("@asker:matrix.exo.tn"), sent.getJSONObject("m.mentions").getJSONArray("user_ids").toList());
    assertEquals(6, sent.length());
    assertEquals(DigestUtils.sha256Hex("$question:dragon"), transactionId.getValue());
  }

  @Test
  void sendReplyMentionsOnlyTheGivenIdsNotTheOnesInTheText() throws Exception {
    String roomId = actingInSpaceRoom();
    Duration timeout = Duration.ofSeconds(5);
    ArgumentCaptor<JSONObject> content = ArgumentCaptor.forClass(JSONObject.class);
    when(matrixHttpClient.sendMessage(eq(roomId), any(JSONObject.class), anyString(), anyString(), eq(timeout))).thenReturn("$reply");

    matrixService.sendReply("dragon",
                            roomId,
                            "ping @someone:matrix.exo.tn and @room",
                            "<b>ping @someone:matrix.exo.tn</b>",
                            "$question",
                            List.of("@asker:matrix.exo.tn"),
                            timeout);

    verify(matrixHttpClient).sendMessage(eq(roomId), content.capture(), anyString(), anyString(), eq(timeout));
    assertEquals(List.of("@asker:matrix.exo.tn"),
                 content.getValue().getJSONObject("m.mentions").getJSONArray("user_ids").toList());
  }

  @Test
  void sendReplyTransactionIdDependsOnTheRepliedEventOnly() throws Exception {
    String roomId = actingInSpaceRoom();
    Duration timeout = Duration.ofSeconds(5);
    ArgumentCaptor<String> transactionId = ArgumentCaptor.forClass(String.class);
    when(matrixHttpClient.sendMessage(eq(roomId), any(JSONObject.class), anyString(), anyString(), eq(timeout))).thenReturn("$reply");

    matrixService.sendReply("dragon", roomId, "first try", null, "$question", List.of(), timeout);
    matrixService.sendReply("dragon", roomId, "retried with other words", null, "$question", List.of(), timeout);
    matrixService.sendReply("dragon", roomId, "first try", null, "$otherQuestion", List.of(), timeout);

    verify(matrixHttpClient, times(3)).sendMessage(eq(roomId), any(JSONObject.class), transactionId.capture(), anyString(), eq(timeout));
    List<String> ids = transactionId.getAllValues();
    assertEquals(ids.get(0), ids.get(1));
    assertNotEquals(ids.get(0), ids.get(2));
  }

  @Test
  void sendReplyByANonMemberSendsNothing() throws Exception {
    actingInSpaceRoom();
    Duration timeout = Duration.ofSeconds(5);

    assertNull(matrixService.sendReply("dragon", "!notMyRoom", "answer", null, "$question", List.of("@asker:matrix.exo.tn"), timeout));

    verify(matrixHttpClient, never()).sendMessage(anyString(), any(JSONObject.class), anyString(), anyString(), any(Duration.class));
  }

  @Test
  void sendReplyPropagatesATimeoutAndAcceptsBothRoomIdShapes() throws Exception {
    when(matrixHttpClient.createRoom(anyString(), anyString(), anyString())).thenReturn("!localSpaceRoom");
    matrixService.getRoomBySpace(getSpaceInstance(1));
    actingAsDragon();
    Duration timeout = Duration.ofSeconds(5);
    when(matrixHttpClient.sendMessage(eq("!localSpaceRoom"), any(JSONObject.class), anyString(), anyString(), eq(timeout))).thenReturn("$reply");

    assertEquals("$reply", matrixService.sendReply("dragon", "!localSpaceRoom:matrix.exo.tn", "a", null, "$q", List.of(), timeout));
    assertEquals("$reply", matrixService.sendReply("dragon", "!localSpaceRoom", "a", null, "$q", List.of(), timeout));

    when(matrixHttpClient.sendMessage(eq("!localSpaceRoom"), any(JSONObject.class), anyString(), anyString(), eq(timeout))).thenThrow(new IOException("timed out"));
    assertThrows(IOException.class, () -> matrixService.sendReply("dragon", "!localSpaceRoom", "a", null, "$q", List.of(), timeout));
  }

  /**
   * Gives "dragon" a Matrix account and a space room he participates in.
   *
   * @return the room id as stored
   */
  private String actingInSpaceRoom() {
    String roomId = matrixService.getRoomBySpace(getSpaceInstance(1)).getRoomId();
    actingAsDragon();
    return roomId;
  }

  /**
   * Gives "dragon" a Matrix account so the service can mint a user token.
   */
  private void actingAsDragon() {
    Identity actingIdentity = identityManager.getOrCreateUserIdentity("dragon");
    actingIdentity.getProfile().setProperty(USER_MATRIX_ID, "@dragon:matrix.exo.tn");
    identityManager.updateProfile(actingIdentity.getProfile());
  }

  /**
   * Builds a textual message event as the room read returns it.
   *
   * @param eventId the event id
   * @param sender the sender's Matrix id
   * @param text the body
   * @param timestamp the origin server timestamp
   * @return the message
   */
  private MatrixMessage message(String eventId, String sender, String text, long timestamp) {
    MatrixMessage message = new MatrixMessage();
    message.setEventId(eventId);
    message.setSender(sender);
    message.setMessageContent(text);
    message.setTimeStamp(timestamp);
    return message;
  }

  /**
   * Builds an {@code m.replace} event as the room read returns it.
   *
   * @param eventId the edit's own event id
   * @param replacedEventId the event it replaces
   * @param sender the sender's Matrix id
   * @param body the fallback body, with its "* " marker
   * @param newBody the body of m.new_content, null when the client sent none
   * @param timestamp the origin server timestamp
   * @return the edit event
   */
  private MatrixMessage edit(String eventId, String replacedEventId, String sender, String body, String newBody, long timestamp) {
    MatrixMessage edit = message(eventId, sender, body, timestamp);
    edit.setRelatesToType("m.replace");
    edit.setReplacedEventId(replacedEventId);
    edit.setEditedContent(newBody);
    return edit;
  }

  /**
   * A user with no direct message yet: the room is created on Matrix as the
   * sender with the recipient invited, recorded on the platform, the sender's
   * m.direct account data updated, and the message lands in that room. A second
   * message reuses the room without creating another.
   */
  @Test
  void sendDirectMessageOpensTheConversationWhenThereIsNone() throws Exception {
    setMatrixId("ghost", "ghost");
    setMatrixId("mary", "mary");
    String createdRoomId = "!createdDmRoom:matrix.exo.tn";
    when(matrixHttpClient.createDirectMessagingRoom(eq("@mary:matrix.exo.tn"), anyString())).thenReturn(createdRoomId);
    when(matrixHttpClient.sendMessage(eq("!createdDmRoom"), eq("hello Mary"), anyString(), anyString())).thenReturn("$dm1");
    when(matrixHttpClient.sendMessage(eq("!createdDmRoom"), eq("again"), anyString(), anyString())).thenReturn("$dm2");
    try {
      assertEquals("$dm1", matrixService.sendDirectMessage("ghost", "mary", "hello Mary"));

      Room room = matrixService.getDirectMessagingRoom("mary", "ghost");
      assertNotNull(room);
      assertEquals(createdRoomId, room.getRoomId());
      @SuppressWarnings("unchecked")
      ArgumentCaptor<Map<String, String[]>> accountData = ArgumentCaptor.forClass(Map.class);
      verify(matrixHttpClient).setDirectRoomsAccountData(eq("@ghost:matrix.exo.tn"), accountData.capture(), anyString());
      assertArrayEquals(new String[] { createdRoomId }, accountData.getValue().get("@mary:matrix.exo.tn"));

      assertEquals("$dm2", matrixService.sendDirectMessage("ghost", "mary", "again"));
      verify(matrixHttpClient, times(1)).createDirectMessagingRoom(anyString(), anyString());
    } finally {
      matrixService.deleteRoom(createdRoomId);
    }
  }

  /**
   * A user with a direct message already, whoever opened it: the message lands
   * in it and nothing is created.
   */
  @Test
  void sendDirectMessageUsesTheExistingConversation() throws Exception {
    setMatrixId("raul", "raul");
    setMatrixId("paul", "paul");
    String existingRoomId = "!existingDmRoom:matrix.exo.tn";
    Room dm = new Room();
    dm.setRoomId(existingRoomId);
    dm.setFirstParticipant("paul");
    dm.setSecondParticipant("raul");
    matrixService.createDirectMessagingRoom(dm);
    when(matrixHttpClient.sendMessage(eq("!existingDmRoom"), eq("hi Paul"), anyString(), anyString())).thenReturn("$dm3");
    try {
      assertEquals("$dm3", matrixService.sendDirectMessage("raul", "paul", "hi Paul"));
      verify(matrixHttpClient, never()).createDirectMessagingRoom(anyString(), anyString());
      verify(matrixHttpClient, never()).setDirectRoomsAccountData(anyString(), any(), anyString());
    } finally {
      matrixService.deleteRoom(existingRoomId);
    }
  }

  /**
   * The refusals, each before anything is created on Matrix: a blank or the
   * user's own username, an unknown user, a recipient without a chat account,
   * direct messages disabled, and a recipient outside the chat's restricted
   * groups. A blank text sends nothing.
   */
  @Test
  void sendDirectMessageRefusesWhereTheChatDoes() throws Exception {
    setMatrixId("ghost", "ghost");
    assertThrows(IllegalArgumentException.class, () -> matrixService.sendDirectMessage("ghost", " ", "hi"));
    assertThrows(IllegalArgumentException.class, () -> matrixService.sendDirectMessage("ghost", "ghost", "hi"));
    assertThrows(ObjectNotFoundException.class, () -> matrixService.sendDirectMessage("ghost", "nobodyHere", "hi"));

    Identity jame = identityManager.getOrCreateUserIdentity("jame");
    jame.getProfile().getProperties().remove(USER_MATRIX_ID);
    identityManager.updateProfile(jame.getProfile());
    assertThrows(IllegalAccessException.class, () -> matrixService.sendDirectMessage("ghost", "jame", "hi"));

    assertNull(matrixService.sendDirectMessage("ghost", "jame", " "));

    SettingService settingService = (SettingService) ReflectionTestUtils.getField(matrixService, "settingService");
    settingService.set(Context.GLOBAL, Scope.APPLICATION, CHAT_SETTINGS, new SettingValue<>(new ChatSettings(true, false, true).toString()));
    try {
      setMatrixId("jame", "jame");
      assertFalse(matrixService.loadChatSettings().isPrivateRoomsEnabled());
      assertThrows(IllegalAccessException.class, () -> matrixService.sendDirectMessage("ghost", "jame", "hi"));
    } finally {
      settingService.remove(Context.GLOBAL, Scope.APPLICATION, CHAT_SETTINGS);
    }

    String restriction = PropertyManager.getProperty(MATRIX_RESTRICTED_USERS_GROUP);
    try {
      PropertyManager.setProperty(MATRIX_RESTRICTED_USERS_GROUP, "/platform/administrators");
      assertThrows(IllegalAccessException.class, () -> matrixService.sendDirectMessage("root", "jame", "hi"));
      assertThrows(IllegalAccessException.class, () -> matrixService.sendDirectMessage("jame", "root", "hi"));
    } finally {
      PropertyManager.setProperty(MATRIX_RESTRICTED_USERS_GROUP, restriction == null ? "" : restriction);
    }
    verify(matrixHttpClient, never()).createDirectMessagingRoom(anyString(), anyString());
  }

  /**
   * The conversations of a type, and those whose title or other participant's
   * username contains the query, case and accents ignored, up to the limit.
   */
  @Test
  void getUserConversationsFiltersByTypeQueryAndLimit() throws Exception {
    Space space = getSpaceInstance(1);
    String spaceRoomId = matrixService.getRoomBySpace(space).getRoomId();
    String dmRoomId = "!filteredDmRoom:matrix.exo.tn";
    Room dm = new Room();
    dm.setRoomId(dmRoomId);
    dm.setFirstParticipant("dragon");
    dm.setSecondParticipant("john");
    matrixService.createDirectMessagingRoom(dm);
    try {
      List<ChatConversation> dms = matrixService.getUserConversations("dragon", "dm", "JOHN", null);
      assertEquals(List.of(dmRoomId), dms.stream().map(ChatConversation::getRoomId).toList());
      assertEquals("john", dms.get(0).getUserName());

      assertTrue(matrixService.getUserConversations("dragon", "space", "john", null).isEmpty());
      assertTrue(matrixService.getUserConversations("dragon", "dm", "nobody", null).isEmpty());

      List<ChatConversation> spaces = matrixService.getUserConversations("dragon", "space", "MY SPÂCE 1", null);
      assertEquals(List.of(spaceRoomId), spaces.stream().map(ChatConversation::getRoomId).toList());
      assertNull(spaces.get(0).getUserName());

      List<ChatConversation> all = matrixService.getUserConversations("dragon", null, null, null);
      assertTrue(all.stream().anyMatch(conversation -> dmRoomId.equals(conversation.getRoomId())));
      assertTrue(all.stream().anyMatch(conversation -> spaceRoomId.equals(conversation.getRoomId())));
      assertEquals(1, matrixService.getUserConversations("dragon", null, null, 1).size());
    } finally {
      matrixService.deleteRoom(dmRoomId);
    }
  }

  /**
   * Sets the Matrix id a user's profile carries.
   *
   * @param userName the user
   * @param matrixId the Matrix id to store
   */
  private void setMatrixId(String userName, String matrixId) {
    Identity identity = identityManager.getOrCreateUserIdentity(userName);
    identity.getProfile().setProperty(USER_MATRIX_ID, matrixId);
    identityManager.updateProfile(identity.getProfile());
  }
}
