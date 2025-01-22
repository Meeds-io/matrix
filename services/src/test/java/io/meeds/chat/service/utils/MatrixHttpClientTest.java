package io.meeds.chat.service.utils;

import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.net.http.HttpResponse;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_SERVER_URL;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MatrixHttpClientTest {

  MockedStatic<MatrixHttpClient> MATRIX_HTTP_CLIENT;

  String                         jwtToken = "ThisIsAJWTToken";

  HttpResponse<String>           response;

  @Test
  void getAdminAccessToken() throws JsonException, IOException, InterruptedException {
    PropertyManager.setProperty(MATRIX_SERVER_URL, "http://matrix:8008");

    // response OK
    when(response.statusCode()).thenReturn(200);
    when(response.body()).thenReturn("{\"access_token\":\"thisIsAnAccessToken\"}");

    MATRIX_HTTP_CLIENT.when(() -> MatrixHttpClient.sendHttpPostRequest(anyString(), anyString(), anyString()))
            .thenReturn(response);
    String result = "";
    try {
      result = MatrixHttpClient.getAdminAccessToken(jwtToken);
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

    MATRIX_HTTP_CLIENT.when(() -> MatrixHttpClient.sendHttpPostRequest(anyString(), anyString(), anyString()))
            .thenReturn(response1, response2);
    try {
      result = MatrixHttpClient.getAdminAccessToken(jwtToken);
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
    response = mock(HttpResponse.class);
    MATRIX_HTTP_CLIENT = mockStatic(MatrixHttpClient.class);
    MATRIX_HTTP_CLIENT.when(() -> MatrixHttpClient.getAdminAccessToken(anyString())).thenCallRealMethod();
  }

  @AfterEach
  void tearDown() {
    MATRIX_HTTP_CLIENT.close();
  }
}
