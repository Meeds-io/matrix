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
package io.meeds.chat.service.utils;

import io.meeds.chat.model.Events;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.MatrixUnreadRoom;
import io.meeds.chat.model.MatrixUserPermission;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.impl.UserImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MatrixHttpClientTest {

  MockedStatic<HTTPHelper> MATRIX_HTTP_HELPER;

  String                   jwtToken         = "ThisIsAJWTToken";

  String                   accessToken      = "ThisIsAnAccessTokenForUser";

  HttpResponse<String>     responseOK;

  HttpResponse<String>     responseTooManyRequests;

  MatrixHttpClient         matrixHttpClient = new MatrixHttpClient();

  private HttpResponse     responseNotOK;

  @Test
  void getAccessToken() throws JsonException, IOException, InterruptedException {
    // response OK
    when(responseOK.statusCode()).thenReturn(200);
    when(responseOK.body()).thenReturn("{\"access_token\":\"thisIsAnAccessToken\"}");

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString())).thenReturn(responseOK);
    String result = "";
    try {
      result = matrixHttpClient.getAccessToken(jwtToken);
    } catch (Exception e) {
      fail();
      throw e;
    }
    assertNotNull(result);
    assertEquals("thisIsAnAccessToken", result);

    // response 429
    HttpResponse response1 = mock(HttpResponse.class);
    when(response1.statusCode()).thenReturn(429);
    when(response1.body()).thenReturn("{\"retry_after_ms\":\"120\"}");

    HttpResponse response2 = mock(HttpResponse.class);
    when(response2.statusCode()).thenReturn(200);
    when(response2.body()).thenReturn("{\"access_token\":\"thisIsAnAccessToken\"}");

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString()))
                      .thenReturn(response1, response2);
    try {
      result = matrixHttpClient.getAccessToken(jwtToken);
    } catch (Exception e) {
      fail();
      throw e;
    }
    assertNotNull(result);
    assertEquals("thisIsAnAccessToken", result);
    verify(response1, times(1)).body();
    verify(response2, times(1)).body();
  }

  @BeforeEach
  void setUp() {
    PropertyManager.setProperty(MATRIX_SERVER_URL, "http://matrix:8008");
    PropertyManager.setProperty(MATRIX_SERVER_NAME, "matrix.exo.com");
    PropertyManager.setProperty(SHARED_SECRET_REGISTRATION, "sharedSecretRegistration");
    MATRIX_HTTP_HELPER = mockStatic(HTTPHelper.class);
    responseOK = mock(HttpResponse.class);
    when(responseOK.statusCode()).thenReturn(200);

    responseTooManyRequests = mock(HttpResponse.class);
    when(responseTooManyRequests.statusCode()).thenReturn(429);
    when(responseTooManyRequests.body()).thenReturn("{\"retry_after_ms\":\"120\"}");

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenReturn(responseOK);
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString())).thenReturn(responseOK);
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString(), any()))
                      .thenReturn(responseOK);
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPutRequest(anyString(), anyString(), anyString())).thenReturn(responseOK);

    responseNotOK = mock(HttpResponse.class);
    when(responseNotOK.statusCode()).thenReturn(500);

  }

  @AfterEach
  void tearDown() {
    MATRIX_HTTP_HELPER.close();
  }

  @Test
  void updateUserDisplayName() {
    // response OK
    try {
      matrixHttpClient.updateUserDisplayName("@user:matrix.server.com", "Chat Bot", accessToken);
    } catch (Exception e) {
      fail();
    }

    // response 429
    HttpResponse response1 = mock(HttpResponse.class);
    when(response1.statusCode()).thenReturn(429);
    when(response1.body()).thenReturn("{\"retry_after_ms\":\"120\"}");

    HttpResponse response2 = mock(HttpResponse.class);
    when(response2.statusCode()).thenReturn(200);

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPutRequest(anyString(), anyString(), anyString()))
                      .thenReturn(response1, response2);
    try {
      matrixHttpClient.updateUserDisplayName("@user:matrix.server.com", "Chat Bot", accessToken);
    } catch (Exception e) {
      fail();
    }
    verify(response1, times(1)).body();
    verify(response2, times(0)).body();

  }

  @Test
  void getUserDisplayName() throws JsonException, IOException, InterruptedException {
    // response OK
    when(responseOK.body()).thenReturn("{\"displayname\":\"Chat Bot\"}");

    String displayName = matrixHttpClient.getUserDisplayName("@user:matrix.server.com", accessToken);
    assertNotNull(displayName);
    assertEquals("Chat Bot", displayName);
  }

  @Test
  void authenticateUser() {
    when(responseOK.body()).thenReturn("{\"access_token\":\"Access token for ali\"}");

    String result = null;
    try {
      result = matrixHttpClient.authenticateUser("ali", "password");
      assertNotNull(result);
      assertEquals("Access token for ali", result);
      verify(responseOK, times(1)).body();
    } catch (Exception e) {
      fail();
    }
    // Error HTTP 429 : too many requests

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString()))
                      .thenReturn(responseTooManyRequests, responseOK);
    try {
      result = matrixHttpClient.authenticateUser("ali", "password");

      assertNotNull(result);
      assertEquals("Access token for ali", result);
      verify(responseTooManyRequests, times(1)).body();
      verify(responseOK, times(2)).body();

    } catch (Exception e) {
      fail();
    }

  }

  @Test
  void createRoom() {
    when(responseOK.body()).thenReturn("{\"room_id\":\"!RoomIdentifier:matrix.exo.com\"}");

    String result = null;
    try {
      result = matrixHttpClient.createRoom("Internal Communication",
                                           "Discussion room for planning internal news and announcements communication",
                                           accessToken);
      assertNotNull(result);
      assertEquals("!RoomIdentifier", result);
      verify(responseOK, times(1)).body();
    } catch (Exception e) {
      fail();
    }
    // Error HTTP 429 : too many requests

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString()))
                      .thenReturn(responseTooManyRequests, responseOK);
    try {
      result = matrixHttpClient.createRoom("Internal Communication",
                                           "Discussion room for planning internal news and announcements communication",
                                           accessToken);

      assertNotNull(result);
      assertEquals("!RoomIdentifier", result);
      verify(responseTooManyRequests, times(1)).body();
      verify(responseOK, times(2)).body();

    } catch (Exception e) {
      fail();
    }

  }

  @Test
  void createUserAccount() {
    when(responseOK.body()).thenReturn("{\"user_id\":\"@harun:matrix.exo.com\"}");

    HttpResponse nonceResponse = mock(HttpResponse.class);
    when(nonceResponse.statusCode()).thenReturn(200);
    when(nonceResponse.body()).thenReturn("{\"nonce\":\"ThisIsANonce\"}");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenReturn(nonceResponse);

    User user = new UserImpl("harun");
    String result = null;

    result = matrixHttpClient.createUserAccount(user, accessToken);
    assertNotNull(result);
    assertEquals("@harun:matrix.exo.com", result);
    verify(responseOK, times(1)).body();

  }

  @Test
  void testUpdateUserDisplayName() {
  }

  @Test
  void saveUserAccount() {
    Identity identity = new Identity();
    identity.setRemoteId("userOne");
    Profile profile = new Profile();
    profile.setProperty(Profile.FULL_NAME, "User One");
    profile.setProperty(Profile.EMAIL, "user@email.com");
    identity.setProfile(profile);
    String userId = "userOneOnMatrix";

    when(responseOK.body()).thenReturn("{\"name\": \"@userOneOnMatrix:matrix.meeds.tn\",\"access_token\":\"accessTokenForUserOne\"}");
    String returnedUserId = matrixHttpClient.saveUserAccount(identity, userId, true, accessToken);
    assertNotNull(returnedUserId);
    assertEquals(userId, returnedUserId);

    returnedUserId = matrixHttpClient.saveUserAccount(identity, userId, false, accessToken, true, true);
    assertNotNull(returnedUserId);
    assertEquals(userId, returnedUserId);

    returnedUserId = matrixHttpClient.saveUserAccount(identity, userId, false, accessToken, true, false);
    assertNotNull(returnedUserId);
    assertEquals(userId, returnedUserId);

    // username with only digits
    // Default prefix : u
    userId = "12";
    when(responseOK.body()).thenReturn("{\"name\": \"@u12:matrix.meeds.tn\",\"access_token\":\"accessTokenForU12\"}");
    returnedUserId = matrixHttpClient.saveUserAccount(identity, userId, false, accessToken, true, false);
    assertNotNull(returnedUserId);
    assertEquals("u" + userId, returnedUserId);

    // Customized prefix : user
    userId = "12";
    when(responseOK.body()).thenReturn("{\"name\": \"@user12:matrix.meeds.tn\",\"access_token\":\"accessTokenForUser12\"}");
    PropertyManager.setProperty(MATRIX_USERNAME_PREFIX, "user");
    returnedUserId = matrixHttpClient.saveUserAccount(identity, userId, false, accessToken, true, false);
    assertNotNull(returnedUserId);
    assertEquals("user" + userId, returnedUserId);
    // Customized prefix : user
    userId = "12";
    when(responseOK.body()).thenReturn("{\"name\": \"@user12:matrix.meeds.tn\",\"access_token\":\"accessTokenForUser12\"}");
    PropertyManager.setProperty(MATRIX_USERNAME_PREFIX, "user");
    returnedUserId = matrixHttpClient.saveUserAccount(identity, userId, false, accessToken, true, false);
    assertNotNull(returnedUserId);
    assertEquals("user" + userId, returnedUserId);

    // Customized prefix : user
    userId = "12";
    when(responseOK.body()).thenReturn("{\"name\": \"@user12:matrix.meeds.tn\",\"access_token\":\"accessTokenForUser12\"}");
    PropertyManager.setProperty(MATRIX_USERNAME_PREFIX, "user");
    returnedUserId = matrixHttpClient.saveUserAccount(identity, userId, false, accessToken, true, false);
    assertNotNull(returnedUserId);
    assertEquals("user" + userId, returnedUserId);

  }

  @Test
  void disableAccount() {
  }

  @Test
  void renameRoom() {
  }

  @Test
  void inviteUserToRoom() {
  }

  @Test
  void kickUserFromRoom() {
  }

  @Test
  void joinUserToRoom() {
    String matrixUserId = "userIdOnMatrix";
    String roomId = "matrixRoomId";
    boolean result = matrixHttpClient.joinUserToRoom(roomId, matrixUserId, accessToken);
    assertTrue(result);

    // Error HTTP 429 : too many requests

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString()))
                      .thenReturn(responseTooManyRequests, responseOK);
    try {
      result = matrixHttpClient.joinUserToRoom(roomId, matrixUserId, accessToken);

      assertTrue(result);
      verify(responseTooManyRequests, times(1)).body();

    } catch (Exception e) {
      fail();
    }
    // Error HTTP 500

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString()))
                      .thenReturn(responseNotOK);
    try {
      result = matrixHttpClient.joinUserToRoom(roomId, matrixUserId, accessToken);

      assertFalse(result);
      verify(responseNotOK, times(1)).body();

    } catch (Exception e) {
      fail();
    }
  }

  @Test
  void isUserMemberOfRoom() throws IOException, InterruptedException {
    String matrixUserId = "user";
    String roomId = "matrixRoomId";

    when(responseOK.body()).thenReturn("""
        {
          "joined": {
            "@user:matrix.exo.com": {
              "display_name": "User",
              "avatar_url": null
            }
          }
        }""");
    boolean result = matrixHttpClient.isUserMemberOfRoom(roomId, matrixUserId, accessToken);
    assertTrue(result);

    result = matrixHttpClient.isUserMemberOfRoom(roomId, "otherUser", accessToken);
    assertFalse(result);

    // Error HTTP 500
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenReturn(responseNotOK);
    result = matrixHttpClient.isUserMemberOfRoom(roomId, matrixUserId, accessToken);
    assertFalse(result);
  }

  @Test
  void makeUserAdminInRoom() {
  }

  @Test
  void getRoomSettings() {
  }

  @Test
  void updateRoomSettings() throws JsonException, IOException, InterruptedException {
    String roomId = "matrixRoomId";
    MatrixRoomPermissions matrixRoomPermissions = new MatrixRoomPermissions();
    MatrixUserPermission userPermission = new MatrixUserPermission();
    userPermission.setUserName("userOne");
    userPermission.setUserRole(SIMPLE_USER_ROLE);
    matrixRoomPermissions.setUsers(List.of(userPermission));
    Events events = new Events("roomName", "50", "50", "50", "50", "50", "50", "50");
    matrixRoomPermissions.setEvents(events);

    when(responseOK.body()).thenReturn("{\"event_id\": \"thisIsAnEventId\"}");
    String result = matrixHttpClient.updateRoomSettings(roomId, matrixRoomPermissions, accessToken);
    assertNotNull(result);
    assertEquals("thisIsAnEventId", result);

    // Error HTTP 429 : too many requests

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPutRequest(anyString(), anyString(), anyString()))
                      .thenReturn(responseTooManyRequests, responseOK);
    try {
      result = matrixHttpClient.updateRoomSettings(roomId, matrixRoomPermissions, accessToken);

      assertNotNull(result);
      verify(responseTooManyRequests, times(1)).body();
      verify(responseOK, times(2)).body();

    } catch (Exception e) {
      fail();
    }

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPutRequest(anyString(), anyString(), anyString())).thenReturn(responseNotOK);
    try {
      matrixHttpClient.updateRoomSettings(roomId, matrixRoomPermissions, accessToken);
      fail();
    } catch (Exception e) {
      // Expected
    }
  }

  @Test
  void uploadFile() {
  }

  @Test
  void updateRoomAvatar() {
    String roomId = "matrixRoomId";
    String avatarUrl = "/path/to/avatar/url";
    boolean result = matrixHttpClient.updateRoomAvatar(roomId, avatarUrl, accessToken);
    assertTrue(result);

    // Error HTTP 429 : too many requests

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPutRequest(anyString(), anyString(), anyString()))
                      .thenReturn(responseTooManyRequests, responseOK);
    try {
      result = matrixHttpClient.updateRoomAvatar(roomId, avatarUrl, accessToken);

      assertTrue(result);
      verify(responseTooManyRequests, times(1)).body();

    } catch (Exception e) {
      fail();
    }

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPutRequest(anyString(), anyString(), anyString())).thenReturn(responseNotOK);
    try {
      matrixHttpClient.updateRoomAvatar(roomId, avatarUrl, accessToken);
      fail();
    } catch (Exception e) {
      // Expected
    }

  }

  @Test
  void updateUserAvatar() {
    String userId = "matrixIdOfUserOne";
    String avatarUrl = "/path/to/avatar/url";
    boolean result = matrixHttpClient.updateUserAvatar(userId, avatarUrl, accessToken);
    assertTrue(result);

    // Error HTTP 429 : too many requests

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPutRequest(anyString(), anyString(), anyString()))
                      .thenReturn(responseTooManyRequests, responseOK);
    try {
      result = matrixHttpClient.updateUserAvatar(userId, avatarUrl, accessToken);
      assertTrue(result);
      verify(responseTooManyRequests, times(1)).body();

    } catch (Exception e) {
      fail();
    }

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPutRequest(anyString(), anyString(), anyString())).thenReturn(responseNotOK);
    result = matrixHttpClient.updateUserAvatar(userId, avatarUrl, accessToken);
    assertFalse(result);
  }

  @Test
  void deleteRoom() {
  }

  @Test
  void getUserPresence() {
    when(responseOK.body()).thenReturn("{\"last_active_ago\": 420845,\"presence\": \"online\"}");

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenReturn(responseOK);
    String result = null;
    try {
      result = matrixHttpClient.getUserPresence("user", accessToken);
    } catch (Exception e) {
      fail();
    }
    assertNotNull(result);
    assertEquals("online", result);
    verify(responseOK, times(1)).body();
  }

  @Test
  void setUserPresence() {
    when(responseOK.body()).thenReturn("{}");

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPutRequest(anyString(), anyString(), anyString())).thenReturn(responseOK);
    String result = null;
    try {
      result = matrixHttpClient.setUserPresence("user", "online", "I am online", accessToken);
    } catch (Exception e) {
      fail();
    }
    assertNotNull(result);
    verify(responseOK, times(1)).body();

    // Error HTTP 429 : too many requests

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPutRequest(anyString(), anyString(), anyString()))
                      .thenReturn(responseTooManyRequests, responseOK);
    try {
      result = matrixHttpClient.setUserPresence("user", "online", "I am online", accessToken);

      assertNotNull(result);
      verify(responseTooManyRequests, times(1)).body();
      verify(responseOK, times(2)).body();

    } catch (Exception e) {
      fail();
    }
  }

  @Test
  void overrideRateLimitForUser() {
    try {
      matrixHttpClient.overrideRateLimitForUser("user", 0, 0, accessToken);
    } catch (Exception e) {
      fail();
    }
    verify(responseOK, times(1)).body();

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString()))
                      .thenReturn(responseNotOK);
    try {
      matrixHttpClient.overrideRateLimitForUser("user", 0, 0, accessToken);
      fail();
    } catch (Exception e) {
      // Expected
    }
  }

  @Test
  void getOverriddenRateLimitForUser() {
    try {
      matrixHttpClient.getOverriddenRateLimitForUser("user", accessToken);
    } catch (Exception e) {
      fail();
    }
    verify(responseOK, times(1)).body();

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenReturn(responseNotOK);
    try {
      matrixHttpClient.getOverriddenRateLimitForUser("user", accessToken);
      fail();
    } catch (Exception e) {
      // Expected
    }
  }

  @Test
  void getUser() {
    when(responseOK.body()).thenReturn("""
        {
          "name": "@ali:matrix.exo.tn",
          "admin": false,
          "deactivated": false,
          "locked": false,
          "shadow_banned": false,
          "creation_ts": 1742995890,
          "appservice_id": null,
          "consent_server_notice_sent": null,
          "consent_version": null,
          "consent_ts": null,
          "user_type": null,
          "is_guest": false,
          "displayname": "Ali Hamdi",
          "avatar_url": null,
          "threepids": [
            {
              "medium": "email",
              "address": "ali@exo.com",
              "validated_at": 1745230543328,
              "added_at": 1745230543328
            }
          ],
          "external_ids": [],
          "erased": false,
          "last_seen_ts": 1754208683122
        }""");
    try {
      String response = matrixHttpClient.getUser("user", accessToken);
      assertNotNull(response);
    } catch (IOException | InterruptedException e) {
      fail();
    }

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenReturn(responseNotOK);
    try {
      matrixHttpClient.getUser("user", accessToken);
      fail();
    } catch (Exception e) {
      // sucess
    }
  }

  @Test
  void getEventById() {
    when(responseOK.body()).thenReturn("""
        {
          "content": {
            "body": "This is an example text message",
            "format": "org.matrix.custom.html",
            "formatted_body": "<b>This is an example text message</b>",
            "msgtype": "m.text"
          },
          "event_id": "$143273582443PhrSn:example.org",
          "origin_server_ts": 1432735824653,
          "room_id": "!636q39766251:matrix.org",
          "sender": "@example:example.org",
          "type": "m.room.message",
          "unsigned": {
            "age": 1234,
            "membership": "join"
          }
        }
        """);
    MatrixMessage response;
    try {
      response = matrixHttpClient.getEventById("eventId", "roomId", accessToken);
      assertNotNull(response);
    } catch (Exception e) {
      fail();
    }

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenReturn(responseNotOK);
    try {
      matrixHttpClient.getEventById("eventId", "roomId", accessToken);
      fail();
    } catch (Exception e) {
      // sucess
    }

  }

  @Test
  void invalidateAccessToken() {
    try {
      boolean result = matrixHttpClient.invalidateAccessToken("accessTokenString");
      assertTrue(result);
    } catch (Exception e) {
      fail();
    }

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString())).thenReturn(responseNotOK);

    try {
      matrixHttpClient.invalidateAccessToken("accessTokenString");
      fail();
    } catch (Exception e) {
      // Expected
    }
  }


  @Test
  void testInviteUserToRoom() {
    boolean result = matrixHttpClient.inviteUserToRoom("roomIdentifier", "@user:matrix.meeds.tn", "Welcome to the room", accessToken);
    assertTrue(result);

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString())).thenReturn(responseNotOK);

    result = matrixHttpClient.inviteUserToRoom("roomIdentifier", "@user:matrix.meeds.tn", "Welcome to the room", accessToken);
    assertFalse(result);
  }

  @Test
  void testKickUserFromRoom() {
    boolean result = matrixHttpClient.kickUserFromRoom("roomIdentifier", "@user:matrix.meeds.tn", "Welcome to the room", accessToken);
    assertTrue(result);

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString())).thenReturn(responseNotOK);

    result = matrixHttpClient.kickUserFromRoom("roomIdentifier", "@user:matrix.meeds.tn", "Welcome to the room", accessToken);
    assertFalse(result);
  }

  @Test
  void getRoomMessages() throws Exception {
    when(responseOK.body()).thenReturn("""
        {
          "chunk": [
            {
              "type": "m.room.message",
              "event_id": "$evt1:matrix.exo.com",
              "sender": "@demo:matrix.exo.com",
              "origin_server_ts": 1600000000000,
              "content": { "body": "Hello world", "msgtype": "m.text" }
            },
            {
              "type": "m.room.member",
              "content": { "membership": "join" }
            },
            {
              "type": "m.room.message",
              "content": { "msgtype": "m.text" }
            },
            {
              "type": "m.room.message",
              "content": { "body": "Minimal" }
            }
          ]
        }""");
    List<MatrixMessage> messages = matrixHttpClient.getRoomMessages("!room1", 50, accessToken);
    // Only the two textual m.room.message events with a body are kept
    assertEquals(2, messages.size());
    assertEquals("Hello world", messages.get(0).getMessageContent());
    assertEquals("@demo:matrix.exo.com", messages.get(0).getSender());
    assertEquals(1600000000000L, messages.get(0).getTimeStamp());
    assertEquals("Minimal", messages.get(1).getMessageContent());

    // Empty / missing chunk -> empty list (no NPE)
    when(responseOK.body()).thenReturn("{}");
    assertTrue(matrixHttpClient.getRoomMessages("!room1", 50, accessToken).isEmpty());
  }

  @Test
  void getRoomMessagesUnauthorized() {
    HttpResponse unauthorized = mock(HttpResponse.class);
    when(unauthorized.statusCode()).thenReturn(401);
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenReturn(unauthorized);
    assertThrows(MatrixUnauthorizedException.class, () -> matrixHttpClient.getRoomMessages("!room1", 50, accessToken));
  }

  @Test
  void getRoomMessagesForbiddenReturnsEmpty() throws Exception {
    HttpResponse forbidden = mock(HttpResponse.class);
    when(forbidden.statusCode()).thenReturn(403);
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenReturn(forbidden);
    assertTrue(matrixHttpClient.getRoomMessages("!room1", 50, accessToken).isEmpty());
  }

  @Test
  void getRoomMessagesServerError() {
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenReturn(responseNotOK);
    assertThrows(MatrixException.class, () -> matrixHttpClient.getRoomMessages("!room1", 50, accessToken));
  }

  @Test
  void getUnreadRooms() throws Exception {
    when(responseOK.body()).thenReturn("""
        {
          "rooms": {
            "join": {
              "!room1:matrix.exo.com": {
                "unread_notifications": { "notification_count": 3 },
                "timeline": {
                  "events": [
                    {
                      "type": "m.room.message",
                      "sender": "@a:matrix.exo.com",
                      "origin_server_ts": 1600000000000,
                      "content": { "body": "Unread hello", "msgtype": "m.text" }
                    }
                  ]
                }
              },
              "!room2:matrix.exo.com": {
                "unread_notifications": { "notification_count": 0 },
                "timeline": { "events": [] }
              },
              "!room4:matrix.exo.com": {
                "unread_notifications": { "notification_count": 2 }
              }
            }
          }
        }""");
    List<MatrixUnreadRoom> unreadRooms = matrixHttpClient.getUnreadRooms(accessToken, 20);
    // room2 has 0 unread and is skipped; room1 and room4 remain
    assertEquals(2, unreadRooms.size());
    MatrixUnreadRoom room1 = unreadRooms.stream().filter(r -> "!room1".equals(r.getRoomId())).findFirst().orElseThrow();
    assertEquals(3, room1.getUnreadCount());
    assertEquals(1, room1.getMessages().size());
    assertEquals("Unread hello", room1.getMessages().get(0).getMessageContent());
    MatrixUnreadRoom room4 = unreadRooms.stream().filter(r -> "!room4".equals(r.getRoomId())).findFirst().orElseThrow();
    assertEquals(2, room4.getUnreadCount());
    assertTrue(room4.getMessages().isEmpty());

    // No joined rooms -> empty list
    when(responseOK.body()).thenReturn("{\"rooms\":{}}");
    assertTrue(matrixHttpClient.getUnreadRooms(accessToken, 20).isEmpty());
  }

  @Test
  void getUnreadRoomsUnauthorized() {
    HttpResponse unauthorized = mock(HttpResponse.class);
    when(unauthorized.statusCode()).thenReturn(401);
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenReturn(unauthorized);
    assertThrows(MatrixUnauthorizedException.class, () -> matrixHttpClient.getUnreadRooms(accessToken, 20));
  }

  @Test
  void getUnreadRoomsServerError() {
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenReturn(responseNotOK);
    assertThrows(MatrixException.class, () -> matrixHttpClient.getUnreadRooms(accessToken, 20));
  }

  @Test
  void sendMessage() throws Exception {
    when(responseOK.body()).thenReturn("{\"event_id\":\"$sentEvent1:matrix.exo.com\"}");
    String eventId = matrixHttpClient.sendMessage("!room1", "Hello there", "txn-1", accessToken);
    assertEquals("$sentEvent1:matrix.exo.com", eventId);
  }

  @Test
  void sendMessageUnauthorized() {
    HttpResponse unauthorized = mock(HttpResponse.class);
    when(unauthorized.statusCode()).thenReturn(401);
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPutRequest(anyString(), anyString(), anyString())).thenReturn(unauthorized);
    assertThrows(MatrixUnauthorizedException.class, () -> matrixHttpClient.sendMessage("!room1", "Hi", "txn-2", accessToken));
  }

  @Test
  void sendMessageServerError() {
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPutRequest(anyString(), anyString(), anyString())).thenReturn(responseNotOK);
    assertThrows(MatrixException.class, () -> matrixHttpClient.sendMessage("!room1", "Hi", "txn-3", accessToken));
  }

  @Test
  void searchMessages() throws Exception {
    when(responseOK.body()).thenReturn("""
        {
          "search_categories": {
            "room_events": {
              "results": [
                {
                  "result": {
                    "type": "m.room.message",
                    "event_id": "$hit1:matrix.exo.com",
                    "room_id": "!room1:matrix.exo.com",
                    "sender": "@a:matrix.exo.com",
                    "origin_server_ts": 1600000000000,
                    "content": { "body": "found it here", "msgtype": "m.text" }
                  }
                },
                {
                  "rank": 0.42
                }
              ]
            }
          }
        }""");
    // Unscoped search across all the user's rooms
    List<MatrixMessage> results = matrixHttpClient.searchMessages("found", null, 20, accessToken);
    assertEquals(1, results.size());
    assertEquals("!room1", results.get(0).getRoomId());
    assertEquals("found it here", results.get(0).getMessageContent());

    // Scoped search to a single room exercises the room-filter branch
    List<MatrixMessage> scoped = matrixHttpClient.searchMessages("found", "!room1", 20, accessToken);
    assertEquals(1, scoped.size());

    // No results / missing categories -> empty list (no NPE)
    when(responseOK.body()).thenReturn("{\"search_categories\":{}}");
    assertTrue(matrixHttpClient.searchMessages("found", null, 20, accessToken).isEmpty());
  }

  @Test
  void searchMessagesFoldsEditsOntoTheEditedMessage() throws Exception {
    // Matrix returns the edit and the message it replaces as two separate hits, most recent
    // first: they must count as one, pointing at the event the client renders.
    when(responseOK.body()).thenReturn("""
        {
          "search_categories": {
            "room_events": {
              "results": [
                {
                  "result": {
                    "type": "m.room.message",
                    "event_id": "$edit:matrix.exo.com",
                    "room_id": "!room1:matrix.exo.com",
                    "sender": "@a:matrix.exo.com",
                    "origin_server_ts": 1600000000100,
                    "content": {
                      "body": "* budget reviewed twice",
                      "msgtype": "m.text",
                      "m.new_content": { "body": "budget reviewed twice", "msgtype": "m.text" },
                      "m.relates_to": { "rel_type": "m.replace", "event_id": "$original:matrix.exo.com" }
                    }
                  }
                },
                {
                  "result": {
                    "type": "m.room.message",
                    "event_id": "$original:matrix.exo.com",
                    "room_id": "!room1:matrix.exo.com",
                    "sender": "@a:matrix.exo.com",
                    "origin_server_ts": 1600000000000,
                    "content": { "body": "budget reviewed", "msgtype": "m.text" }
                  }
                }
              ]
            }
          }
        }""");
    List<MatrixMessage> results = matrixHttpClient.searchMessages("budget", null, 20, accessToken);
    assertEquals(1, results.size());
    assertEquals("$original:matrix.exo.com", results.get(0).getEventId());
    assertEquals("budget reviewed twice", results.get(0).getMessageContent());
  }

  @Test
  void searchMessagesDropsMessagesEditedToRemoveTheTerm() throws Exception {
    // The original event keeps matching on its outdated body: neither it nor the edit
    // may be reported once the term is gone from the current text.
    when(responseOK.body()).thenReturn("""
        {
          "search_categories": {
            "room_events": {
              "results": [
                {
                  "result": {
                    "type": "m.room.message",
                    "event_id": "$edit:matrix.exo.com",
                    "room_id": "!room1:matrix.exo.com",
                    "sender": "@a:matrix.exo.com",
                    "origin_server_ts": 1600000000100,
                    "content": {
                      "body": "* nothing to see",
                      "msgtype": "m.text",
                      "m.new_content": { "body": "nothing to see", "msgtype": "m.text" },
                      "m.relates_to": { "rel_type": "m.replace", "event_id": "$original:matrix.exo.com" }
                    }
                  }
                },
                {
                  "result": {
                    "type": "m.room.message",
                    "event_id": "$original:matrix.exo.com",
                    "room_id": "!room1:matrix.exo.com",
                    "sender": "@a:matrix.exo.com",
                    "origin_server_ts": 1600000000000,
                    "content": { "body": "budget reviewed", "msgtype": "m.text" }
                  }
                }
              ]
            }
          }
        }""");
    assertTrue(matrixHttpClient.searchMessages("budget", null, 20, accessToken).isEmpty());
  }

  @Test
  void searchMessagesSkipsRedactedMessages() throws Exception {
    // Synapse keeps redacted events in its index: a deleted message is not a hit.
    when(responseOK.body()).thenReturn("""
        {
          "search_categories": {
            "room_events": {
              "results": [
                {
                  "result": {
                    "type": "m.room.message",
                    "event_id": "$deleted:matrix.exo.com",
                    "room_id": "!room1:matrix.exo.com",
                    "sender": "@a:matrix.exo.com",
                    "origin_server_ts": 1600000000000,
                    "content": { "body": "budget deleted", "msgtype": "m.text" },
                    "unsigned": { "redacted_because": { "type": "m.room.redaction" } }
                  }
                },
                {
                  "result": {
                    "type": "m.room.message",
                    "event_id": "$kept:matrix.exo.com",
                    "room_id": "!room1:matrix.exo.com",
                    "sender": "@a:matrix.exo.com",
                    "origin_server_ts": 1600000000001,
                    "content": { "body": "budget kept", "msgtype": "m.text" }
                  }
                }
              ]
            }
          }
        }""");
    List<MatrixMessage> results = matrixHttpClient.searchMessages("budget", null, 20, accessToken);
    assertEquals(1, results.size());
    assertEquals("$kept:matrix.exo.com", results.get(0).getEventId());
  }

  @Test
  void searchMessagesDeduplicatesRepeatedHitsOfTheSameEvent() throws Exception {
    when(responseOK.body()).thenReturn("""
        {
          "search_categories": {
            "room_events": {
              "results": [
                {
                  "result": {
                    "type": "m.room.message",
                    "event_id": "$twice:matrix.exo.com",
                    "room_id": "!room1:matrix.exo.com",
                    "sender": "@a:matrix.exo.com",
                    "origin_server_ts": 1600000000000,
                    "content": { "body": "budget once", "msgtype": "m.text" }
                  }
                },
                {
                  "result": {
                    "type": "m.room.message",
                    "event_id": "$twice:matrix.exo.com",
                    "room_id": "!room1:matrix.exo.com",
                    "sender": "@a:matrix.exo.com",
                    "origin_server_ts": 1600000000000,
                    "content": { "body": "budget once", "msgtype": "m.text" }
                  }
                }
              ]
            }
          }
        }""");
    assertEquals(1, matrixHttpClient.searchMessages("budget", null, 20, accessToken).size());
  }

  @Test
  void searchMessagesUnauthorized() {
    HttpResponse unauthorized = mock(HttpResponse.class);
    when(unauthorized.statusCode()).thenReturn(401);
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString())).thenReturn(unauthorized);
    assertThrows(MatrixUnauthorizedException.class, () -> matrixHttpClient.searchMessages("q", null, 20, accessToken));
  }

  @Test
  void searchMessagesServerError() {
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString())).thenReturn(responseNotOK);
    assertThrows(RuntimeException.class, () -> matrixHttpClient.searchMessages("q", null, 20, accessToken));
  }
}
