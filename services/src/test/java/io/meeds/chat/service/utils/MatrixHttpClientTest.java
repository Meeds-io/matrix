package io.meeds.chat.service.utils;

import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.impl.UserImpl;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.net.http.HttpResponse;

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

  @Test
  void getAdminAccessToken() throws JsonException, IOException, InterruptedException {
    // response OK
    when(responseOK.statusCode()).thenReturn(200);
    when(responseOK.body()).thenReturn("{\"access_token\":\"thisIsAnAccessToken\"}");

    MATRIX_HTTP_HELPER.when(() -> HTTPHelper.sendHttpPostRequest(anyString(), anyString(), anyString())).thenReturn(responseOK);
    String result = "";
    try {
      result = matrixHttpClient.getAdminAccessToken(jwtToken);
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
      result = matrixHttpClient.getAdminAccessToken(jwtToken);
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
  }

  @Test
  void makeUserAdminInRoom() {
  }

  @Test
  void getRoomSettings() {
  }

  @Test
  void updateRoomSettings() {
  }

  @Test
  void uploadFile() {
  }

  @Test
  void updateRoomAvatar() {
  }

  @Test
  void updateUserAvatar() {
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
}
