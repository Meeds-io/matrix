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
import io.meeds.chat.model.MatrixPushRule;
import io.meeds.chat.model.MatrixPusher;
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
import org.json.JSONObject;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MatrixHttpClientTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);

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

  /**
   * A mention message carries an HTML {@code formatted_body} beside its plain
   * {@code body}: the message content stays the plain text, which the mention
   * notification and the push popup show, and the HTML goes to the formatted
   * content only.
   */
  @Test
  void getEventByIdKeepsThePlainBodyAndTheHtmlApart() throws Exception {
    when(responseOK.body()).thenReturn("""
        {
          "content": {
            "body": "Lea can you check?",
            "format": "org.matrix.custom.html",
            "formatted_body": "<a href=\\"https://matrix.to/#/@lea:matrix.exo.com\\">Lea</a> can you check?",
            "msgtype": "m.text",
            "m.mentions": {"user_ids": ["@lea:matrix.exo.com"]}
          },
          "event_id": "$mention",
          "origin_server_ts": 1432735824653,
          "room_id": "!room:matrix.exo.com",
          "sender": "@john:matrix.exo.com",
          "type": "m.room.message"
        }
        """);

    MatrixMessage message = matrixHttpClient.getEventById("$mention", "!room:matrix.exo.com", accessToken);

    assertEquals("Lea can you check?", message.getMessageContent());
    assertEquals("<a href=\"https://matrix.to/#/@lea:matrix.exo.com\">Lea</a> can you check?", message.getFormattedContent());
    assertEquals("m.text", message.getMessageType());
    assertEquals(List.of("@lea:matrix.exo.com"), message.getMentionedUsers());
  }

  /**
   * A {@code formatted_body} counts only when the content declares the
   * {@code org.matrix.custom.html} format: without it, or with another format,
   * there is no formatted content and the message content is the body.
   */
  @Test
  void getEventByIdIgnoresAFormattedBodyWithoutTheHtmlFormat() throws Exception {
    when(responseOK.body()).thenReturn("""
        {
          "content": {"body": "plain", "formatted_body": "<b>plain</b>", "msgtype": "m.text"},
          "event_id": "$noformat", "origin_server_ts": 1, "room_id": "!room:matrix.exo.com",
          "sender": "@john:matrix.exo.com", "type": "m.room.message"
        }
        """);
    MatrixMessage message = matrixHttpClient.getEventById("$noformat", "!room:matrix.exo.com", accessToken);
    assertEquals("plain", message.getMessageContent());
    assertNull(message.getFormattedContent());

    when(responseOK.body()).thenReturn("""
        {
          "content": {"body": "plain", "format": "org.example.other", "formatted_body": "<b>plain</b>", "msgtype": "m.text"},
          "event_id": "$otherformat", "origin_server_ts": 1, "room_id": "!room:matrix.exo.com",
          "sender": "@john:matrix.exo.com", "type": "m.room.message"
        }
        """);
    message = matrixHttpClient.getEventById("$otherformat", "!room:matrix.exo.com", accessToken);
    assertEquals("plain", message.getMessageContent());
    assertNull(message.getFormattedContent());
  }

  /**
   * An event id of a room version 3 room can contain {@code /} and {@code +}:
   * the room id and the event id reach Synapse percent-encoded, so Synapse
   * routes the request to the event endpoint instead of answering 404.
   */
  @Test
  void getEventByIdEncodesTheRoomIdAndTheEventIdInThePath() throws Exception {
    when(responseOK.body()).thenReturn("""
        {
          "content": {"body": "hello", "msgtype": "m.text"},
          "event_id": "$a/b+c", "origin_server_ts": 1, "room_id": "!room:matrix.exo.com",
          "sender": "@john:matrix.exo.com", "type": "m.room.message"
        }
        """);

    List<String> requestedUrls = new java.util.ArrayList<>();
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(anyString(), anyString())).thenAnswer(invocation -> {
      requestedUrls.add(invocation.getArgument(0));
      return responseOK;
    });

    matrixHttpClient.getEventById("$a/b+c", "!room:matrix.exo.com", accessToken);

    assertEquals(List.of("http://matrix:8008/_matrix/client/v3/rooms/%21room%3Amatrix.exo.com/event/%24a%2Fb%2Bc"), requestedUrls);
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
  void getRoomDisplayNameUsesTheRoomName() {
    HttpResponse nameResponse = mock(HttpResponse.class);
    when(nameResponse.statusCode()).thenReturn(200);
    when(nameResponse.body()).thenReturn("{\"name\":\"Support Team\"}");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(contains("/state/m.room.name"), anyString()))
                      .thenReturn(nameResponse);

    assertEquals("Support Team", matrixHttpClient.getRoomDisplayName("!room1", "@me:matrix.exo.com", accessToken));
  }

  @Test
  void getRoomDisplayNameFallsBackToTheOtherMemberOfADirectMessage() {
    // No m.room.name on the room: a two-member room is named after the other member.
    HttpResponse noName = mock(HttpResponse.class);
    when(noName.statusCode()).thenReturn(404);
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(contains("/state/m.room.name"), anyString()))
                      .thenReturn(noName);
    HttpResponse members = mock(HttpResponse.class);
    when(members.statusCode()).thenReturn(200);
    when(members.body()).thenReturn("""
        {
          "joined": {
            "@me:matrix.exo.com": { "display_name": "Me Myself" },
            "@veronika:matrix.exo.com": { "display_name": "Veronika V" }
          }
        }""");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(contains("/joined_members"), anyString()))
                      .thenReturn(members);

    assertEquals("Veronika V", matrixHttpClient.getRoomDisplayName("!room1", "@me:matrix.exo.com", accessToken));
  }

  @Test
  void getRoomDisplayNameReturnsNullWhenMatrixCannotNameTheRoom() {
    HttpResponse noName = mock(HttpResponse.class);
    when(noName.statusCode()).thenReturn(404);
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(contains("/state/m.room.name"), anyString()))
                      .thenReturn(noName);
    // More than two members and no room name -> nothing sensible to display.
    HttpResponse members = mock(HttpResponse.class);
    when(members.statusCode()).thenReturn(200);
    when(members.body()).thenReturn("""
        {
          "joined": {
            "@me:matrix.exo.com": { "display_name": "Me Myself" },
            "@a:matrix.exo.com": { "display_name": "A A" },
            "@b:matrix.exo.com": { "display_name": "B B" }
          }
        }""");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpGetRequest(contains("/joined_members"), anyString()))
                      .thenReturn(members);

    assertNull(matrixHttpClient.getRoomDisplayName("!room1", "@me:matrix.exo.com", accessToken));
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

  /** The bounded login retries once after a 429, waiting what Synapse asked for. */
  @Test
  void boundedLoginRetriesOnceAfterRateLimit() throws Exception {
    HttpResponse limited = mock(HttpResponse.class);
    when(limited.statusCode()).thenReturn(429);
    when(limited.body()).thenReturn("{\"retry_after_ms\":50}");
    HttpResponse granted = mock(HttpResponse.class);
    when(granted.statusCode()).thenReturn(200);
    when(granted.body()).thenReturn("{\"access_token\":\"boundedToken\"}");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedPostRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)))
                      .thenReturn(limited, granted);

    assertEquals("boundedToken", matrixHttpClient.getAccessToken(jwtToken, TIMEOUT));
    MATRIX_HTTP_HELPER.verify(() -> HTTPHelper.sendBoundedPostRequest(contains("/login"), eq(""), contains(jwtToken), eq(TIMEOUT)),
                              times(2));
  }

  /** A second 429 fails the login instead of waiting again. */
  @Test
  void boundedLoginFailsOnSecondRateLimit() {
    HttpResponse limited = mock(HttpResponse.class);
    when(limited.statusCode()).thenReturn(429);
    when(limited.body()).thenReturn("{\"retry_after_ms\":10}");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedPostRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)))
                      .thenReturn(limited);

    assertThrows(MatrixException.class, () -> matrixHttpClient.getAccessToken(jwtToken, TIMEOUT));
    MATRIX_HTTP_HELPER.verify(() -> HTTPHelper.sendBoundedPostRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)),
                              times(2));
  }

  /** A 429 asking for a minute is waited for five seconds at most. */
  @Test
  void boundedLoginCapsTheRateLimitWait() {
    HttpResponse limited = mock(HttpResponse.class);
    when(limited.statusCode()).thenReturn(429);
    when(limited.body()).thenReturn("{\"retry_after_ms\":60000}");
    HttpResponse granted = mock(HttpResponse.class);
    when(granted.statusCode()).thenReturn(200);
    when(granted.body()).thenReturn("{\"access_token\":\"boundedToken\"}");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedPostRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)))
                      .thenReturn(limited, granted);

    long start = System.nanoTime();
    assertTimeout(Duration.ofSeconds(8), () -> matrixHttpClient.getAccessToken(jwtToken, TIMEOUT));
    long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
    assertTrue(elapsedMs >= 4900 && elapsedMs < 7000, "waited " + elapsedMs + " ms");
  }

  /** The pushers listing keeps the fields the reconcile compares. */
  @Test
  void getPushers() throws Exception {
    HttpResponse listed = mock(HttpResponse.class);
    when(listed.statusCode()).thenReturn(200);
    when(listed.body()).thenReturn("""
        {"pushers":[
          {"app_id":"exo.matrix.agent","pushkey":"bGVh.mac","kind":"http","profile_tag":"lea",
           "data":{"format":"event_id_only","url":"https://acme.com/_matrix/push/v1/notify"}},
          {"app_id":"exo.matrix.app","pushkey":"jwt","kind":"http"}
        ]}""");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedGetRequest(anyString(), anyString(), eq(TIMEOUT))).thenReturn(listed);

    List<MatrixPusher> pushers = matrixHttpClient.getPushers(accessToken, TIMEOUT);

    assertEquals(2, pushers.size());
    assertEquals("exo.matrix.agent", pushers.get(0).appId());
    assertEquals("bGVh.mac", pushers.get(0).pushkey());
    assertEquals("https://acme.com/_matrix/push/v1/notify", pushers.get(0).url());
    assertNull(pushers.get(1).url());
    MATRIX_HTTP_HELPER.verify(() -> HTTPHelper.sendBoundedGetRequest(eq("http://matrix:8008/_matrix/client/v3/pushers"),
                                                                     eq(accessToken),
                                                                     eq(TIMEOUT)));
  }

  /** A rejected token and a server error are told apart. */
  @Test
  void getPushersErrors() {
    HttpResponse unauthorized = mock(HttpResponse.class);
    when(unauthorized.statusCode()).thenReturn(401);
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedGetRequest(anyString(), anyString(), eq(TIMEOUT)))
                      .thenReturn(unauthorized, responseNotOK);
    assertThrows(MatrixUnauthorizedException.class, () -> matrixHttpClient.getPushers(accessToken, TIMEOUT));
    assertThrows(MatrixException.class, () -> matrixHttpClient.getPushers(accessToken, TIMEOUT));
  }

  /** A pusher is set as an event-id-only HTTP pusher, without appending. */
  @Test
  void setPusher() throws Exception {
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedPostRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)))
                      .thenReturn(responseOK);
    MatrixPusher pusher = new MatrixPusher("exo.matrix.agent",
                                           "bGVh.mac",
                                           "http",
                                           "eXo digital worker",
                                           "Server",
                                           "lea",
                                           "en",
                                           "https://acme.com/_matrix/push/v1/notify");

    matrixHttpClient.setPusher(pusher, accessToken, TIMEOUT);

    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    MATRIX_HTTP_HELPER.verify(() -> HTTPHelper.sendBoundedPostRequest(eq("http://matrix:8008/_matrix/client/v3/pushers/set"),
                                                                      eq(accessToken),
                                                                      payload.capture(),
                                                                      eq(TIMEOUT)));
    JSONObject json = new JSONObject(payload.getValue());
    assertEquals("exo.matrix.agent", json.getString("app_id"));
    assertEquals("bGVh.mac", json.getString("pushkey"));
    assertEquals("http", json.getString("kind"));
    assertEquals("lea", json.getString("profile_tag"));
    assertFalse(json.getBoolean("append"));
    assertEquals("event_id_only", json.getJSONObject("data").getString("format"));
    assertEquals("https://acme.com/_matrix/push/v1/notify", json.getJSONObject("data").getString("url"));
  }

  /** A pusher is deleted by setting its pair with a null kind. */
  @Test
  void deletePusher() throws Exception {
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedPostRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)))
                      .thenReturn(responseOK);

    matrixHttpClient.deletePusher("exo.matrix.agent", "old.key", accessToken, TIMEOUT);

    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    MATRIX_HTTP_HELPER.verify(() -> HTTPHelper.sendBoundedPostRequest(contains("/pushers/set"),
                                                                      eq(accessToken),
                                                                      payload.capture(),
                                                                      eq(TIMEOUT)));
    JSONObject json = new JSONObject(payload.getValue());
    assertEquals("old.key", json.getString("pushkey"));
    assertTrue(json.has("kind"));
    assertTrue(json.isNull("kind"));
  }

  /** Every kind of the global rule set is listed, with its notify flag. */
  @Test
  void getPushRules() throws Exception {
    HttpResponse listed = mock(HttpResponse.class);
    when(listed.statusCode()).thenReturn(200);
    when(listed.body()).thenReturn("""
        {"global":{
          "override":[{"rule_id":".m.rule.is_room_mention","enabled":true,"default":true,
                       "actions":["notify",{"set_tweak":"highlight"}]},
                      {"rule_id":".m.rule.suppress_notices","enabled":true,"actions":["dont_notify"]}],
          "content":[{"rule_id":".m.rule.contains_user_name","enabled":false,"actions":["notify"]}],
          "underride":[{"rule_id":".m.rule.message","enabled":true,"actions":["notify"]},
                       {"rule_id":"custom","enabled":true,"actions":["notify"]}]
        }}""");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedGetRequest(anyString(), anyString(), eq(TIMEOUT))).thenReturn(listed);

    List<MatrixPushRule> rules = matrixHttpClient.getPushRules(accessToken, TIMEOUT);

    assertEquals(List.of(new MatrixPushRule("override", ".m.rule.is_room_mention", true, true),
                         new MatrixPushRule("override", ".m.rule.suppress_notices", true, false),
                         new MatrixPushRule("content", ".m.rule.contains_user_name", false, true),
                         new MatrixPushRule("underride", ".m.rule.message", true, true),
                         new MatrixPushRule("underride", "custom", true, true)),
                 rules);
    assertTrue(rules.get(0).isServerDefault());
    assertFalse(rules.get(4).isServerDefault());
    MATRIX_HTTP_HELPER.verify(() -> HTTPHelper.sendBoundedGetRequest(eq("http://matrix:8008/_matrix/client/v3/pushrules/global/"),
                                                                     eq(accessToken),
                                                                     eq(TIMEOUT)));
  }

  /** A rule's enabled flag is set through its own path, the id encoded. */
  @Test
  void setPushRuleEnabled() throws Exception {
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedPutRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)))
                      .thenReturn(responseOK);

    matrixHttpClient.setPushRuleEnabled("override", ".m.rule.is_room_mention", false, accessToken, TIMEOUT);

    MATRIX_HTTP_HELPER.verify(() -> HTTPHelper.sendBoundedPutRequest(eq("http://matrix:8008/_matrix/client/v3/pushrules/global/override/.m.rule.is_room_mention/enabled"),
                                                                     eq(accessToken),
                                                                     eq("{\"enabled\":false}"),
                                                                     eq(TIMEOUT)));
  }

  /** The bounded event read goes through the bounded request with its timeout. */
  @Test
  void boundedGetEventById() throws Exception {
    HttpResponse event = mock(HttpResponse.class);
    when(event.statusCode()).thenReturn(200);
    when(event.body()).thenReturn("""
        {"room_id":"!r:matrix.exo.com","type":"m.room.message","sender":"@a:matrix.exo.com",
         "origin_server_ts":"1700000000000","content":{"msgtype":"m.text","body":"hello"}}""");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedGetRequest(anyString(), anyString(), eq(TIMEOUT))).thenReturn(event);

    MatrixMessage message = matrixHttpClient.getEventById("$e/1", "!r:matrix.exo.com", accessToken, TIMEOUT);

    assertEquals("hello", message.getMessageContent());
    MATRIX_HTTP_HELPER.verify(() -> HTTPHelper.sendBoundedGetRequest(contains("/event/%24e%2F1"), eq(accessToken), eq(TIMEOUT)));
  }

  /** An edit and a reply are told apart through the content's relation. */
  @Test
  void getEventByIdReadsTheRelation() throws Exception {
    HttpResponse edit = mock(HttpResponse.class);
    when(edit.statusCode()).thenReturn(200);
    when(edit.body()).thenReturn("""
        {"room_id":"!r:matrix.exo.com","type":"m.room.message","sender":"@a:matrix.exo.com","origin_server_ts":"1",
         "content":{"msgtype":"m.text","body":"* fixed","m.new_content":{"body":"fixed"},
                    "m.relates_to":{"rel_type":"m.replace","event_id":"$original"}}}""");
    HttpResponse reply = mock(HttpResponse.class);
    when(reply.statusCode()).thenReturn(200);
    when(reply.body()).thenReturn("""
        {"room_id":"!r:matrix.exo.com","type":"m.room.message","sender":"@a:matrix.exo.com","origin_server_ts":"1",
         "content":{"msgtype":"m.text","body":"sure","m.relates_to":{"m.in_reply_to":{"event_id":"$question"}}}}""");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedGetRequest(anyString(), anyString(), eq(TIMEOUT))).thenReturn(edit, reply);

    MatrixMessage editMessage = matrixHttpClient.getEventById("$edit", "!r:matrix.exo.com", accessToken, TIMEOUT);
    MatrixMessage replyMessage = matrixHttpClient.getEventById("$reply", "!r:matrix.exo.com", accessToken, TIMEOUT);

    assertEquals("m.replace", editMessage.getRelatesToType());
    assertNull(editMessage.getInReplyTo());
    assertNull(replyMessage.getRelatesToType());
    assertEquals("$question", replyMessage.getInReplyTo());
  }

  /** The room read exposes the relation of a reply and of an edit, and drops an event without a body. */
  @Test
  void getRoomMessagesReadsTheRelations() throws Exception {
    when(responseOK.body()).thenReturn("""
        {
          "chunk": [
            {
              "type": "m.room.message",
              "event_id": "$edit",
              "sender": "@demo:matrix.exo.com",
              "origin_server_ts": 3000,
              "content": {
                "body": "* fixed",
                "msgtype": "m.text",
                "m.new_content": { "body": "fixed", "msgtype": "m.text" },
                "m.relates_to": { "rel_type": "m.replace", "event_id": "$orig" }
              }
            },
            {
              "type": "m.room.message",
              "event_id": "$legacyEdit",
              "sender": "@demo:matrix.exo.com",
              "origin_server_ts": 2500,
              "content": {
                "body": "* legacy",
                "msgtype": "m.text",
                "m.relates_to": { "rel_type": "m.replace", "event_id": "$orig" }
              }
            },
            {
              "type": "m.room.message",
              "event_id": "$reply",
              "sender": "@ghost:matrix.exo.com",
              "origin_server_ts": 2000,
              "content": {
                "body": "answer",
                "msgtype": "m.text",
                "m.relates_to": { "m.in_reply_to": { "event_id": "$orig" } }
              }
            },
            {
              "type": "m.room.message",
              "event_id": "$redactedOriginal",
              "content": {}
            },
            {
              "type": "m.room.message",
              "event_id": "$orig",
              "sender": "@demo:matrix.exo.com",
              "origin_server_ts": 1000,
              "content": { "body": "typo", "msgtype": "m.text" }
            }
          ]
        }""");

    List<MatrixMessage> messages = matrixHttpClient.getRoomMessages("!room1", 50, accessToken);

    assertEquals(4, messages.size());
    MatrixMessage edit = messages.get(0);
    assertEquals("m.replace", edit.getRelatesToType());
    assertEquals("$orig", edit.getReplacedEventId());
    assertEquals("fixed", edit.getEditedContent());
    assertEquals("* fixed", edit.getMessageContent());
    assertNull(messages.get(1).getEditedContent());
    assertEquals("$orig", messages.get(1).getReplacedEventId());
    assertEquals("$orig", messages.get(2).getInReplyTo());
    assertNull(messages.get(2).getReplacedEventId());
    assertNull(messages.get(3).getRelatesToType());
    assertNull(messages.get(3).getReplacedEventId());
  }

  /** The bounded room read goes through the bounded request and throws a timeout. */
  @Test
  void getRoomMessagesWithTimeoutIsBoundedAndPropagatesAFailure() throws Exception {
    when(responseOK.body()).thenReturn("""
        { "chunk": [ { "type": "m.room.message", "event_id": "$e", "content": { "body": "hi" } } ] }""");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedGetRequest(anyString(), anyString(), eq(TIMEOUT))).thenReturn(responseOK);

    List<MatrixMessage> messages = matrixHttpClient.getRoomMessages("!room1", 50, accessToken, TIMEOUT);

    assertEquals(1, messages.size());
    MATRIX_HTTP_HELPER.verify(() -> HTTPHelper.sendBoundedGetRequest(eq("http://matrix:8008/_matrix/client/v3/rooms/!room1:matrix.exo.com/messages?dir=b&limit=50"),
                                                                     eq(accessToken),
                                                                     eq(TIMEOUT)));

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedGetRequest(anyString(), anyString(), eq(TIMEOUT)))
                      .thenThrow(new IOException("timed out"));
    assertThrows(IOException.class, () -> matrixHttpClient.getRoomMessages("!room1", 50, accessToken, TIMEOUT));
  }

  /** A message built by the caller is put as is, bounded, under the given transaction id. */
  @Test
  void sendMessageContentIsPutAsIsAndBounded() throws Exception {
    when(responseOK.body()).thenReturn("{\"event_id\":\"$sentReply\"}");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedPutRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)))
                      .thenReturn(responseOK);
    JSONObject content = new JSONObject().put("msgtype", "m.text")
                                         .put("body", "hi")
                                         .put("m.mentions", new JSONObject().put("user_ids", List.of("@asker:matrix.exo.com")));

    String eventId = matrixHttpClient.sendMessage("!room1", content, "txn-9", accessToken, TIMEOUT);

    assertEquals("$sentReply", eventId);
    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    MATRIX_HTTP_HELPER.verify(() -> HTTPHelper.sendBoundedPutRequest(eq("http://matrix:8008/_matrix/client/v3/rooms/!room1:matrix.exo.com/send/m.room.message/txn-9"),
                                                                     eq(accessToken),
                                                                     payload.capture(),
                                                                     eq(TIMEOUT)));
    assertEquals(content.toString(), payload.getValue());

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedPutRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)))
                      .thenThrow(new IOException("timed out"));
    assertThrows(IOException.class, () -> matrixHttpClient.sendMessage("!room1", content, "txn-9", accessToken, TIMEOUT));
  }

  /** The bounded user read goes through the bounded request, the id encoded, and fails on an error status. */
  @Test
  void boundedGetUser() throws Exception {
    HttpResponse found = mock(HttpResponse.class);
    when(found.statusCode()).thenReturn(200);
    when(found.body()).thenReturn("{\"name\":\"@a:matrix.exo.com\"}");
    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendBoundedGetRequest(anyString(), anyString(), eq(TIMEOUT)))
                      .thenReturn(found, responseNotOK);

    assertEquals("{\"name\":\"@a:matrix.exo.com\"}", matrixHttpClient.getUser("@a:matrix.exo.com", accessToken, TIMEOUT));
    assertThrows(RuntimeException.class, () -> matrixHttpClient.getUser("@a:matrix.exo.com", accessToken, TIMEOUT));
    MATRIX_HTTP_HELPER.verify(() -> HTTPHelper.sendBoundedGetRequest(eq("http://matrix:8008/_synapse/admin/v2/users/%40a%3Amatrix.exo.com"),
                                                                     eq(accessToken),
                                                                     eq(TIMEOUT)),
                              times(2));
  }
}
