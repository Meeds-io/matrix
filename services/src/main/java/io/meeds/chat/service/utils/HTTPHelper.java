package io.meeds.chat.service.utils;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;

import static io.meeds.chat.service.utils.MatrixConstants.*;

public class HTTPHelper {
  protected static HttpResponse<String> sendHttpGetRequest(String url, String token) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header(AUTHORIZATION, BEARER + token).GET().build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  protected static HttpResponse<String> sendHttpPostRequest(String url, String token, String contentAsJson) throws IOException,
                                                                                                            InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request;
    if (StringUtils.isNotBlank(token)) {
      request = HttpRequest.newBuilder()
                           .uri(URI.create(url))
                           .header(AUTHORIZATION, BEARER + token)
                           .POST(HttpRequest.BodyPublishers.ofString(contentAsJson))
                           .build();
    } else {
      request = HttpRequest.newBuilder().uri(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(contentAsJson)).build();
    }
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  protected static HttpResponse<String> sendHttpPostRequest(String url,
                                                            String token,
                                                            String mimeType,
                                                            byte[] fileContent) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request;
    request = HttpRequest.newBuilder()
                         .uri(URI.create(url))
                         .header(AUTHORIZATION, BEARER + token)
                         .header(CONTENT_TYPE, mimeType)
                         .POST(HttpRequest.BodyPublishers.ofByteArray(fileContent))
                         .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  protected static HttpResponse<String> sendHttpPutRequest(String url, String token, String contentAsJson) throws IOException,
                                                                                                           InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
                                     .uri(URI.create(url))
                                     .header(AUTHORIZATION, BEARER + token)
                                     .PUT(HttpRequest.BodyPublishers.ofString(contentAsJson))
                                     .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  protected static HttpResponse<String> sendHttpDeleteRequest(String url,
                                                              String token,
                                                              String contentAsJson) throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
                                     .uri(URI.create(url))
                                     .header(AUTHORIZATION, BEARER + token)
                                     .method("DELETE", HttpRequest.BodyPublishers.ofString(contentAsJson))
                                     .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  public static void sendInvitationToMembers(ArrayList<String> strings, String matrixRoomId) {

  }
}
