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

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;

import static io.meeds.chat.service.utils.MatrixConstants.*;

public class HTTPHelper {

  // Bound the TCP connect phase so an unreachable Matrix host fails fast (and can
  // be retried) instead of hanging the caller until the OS-level socket timeout.
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

  private static HttpClient newHttpClient() {
    return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
  }

  protected static HttpResponse<String> sendHttpGetRequest(String url, String token) throws IOException, InterruptedException {
    HttpClient client = newHttpClient();
    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header(AUTHORIZATION, BEARER + token).GET().build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  protected static HttpResponse<String> sendHttpPostRequest(String url, String token, String contentAsJson) throws IOException,
                                                                                                            InterruptedException {
    HttpClient client = newHttpClient();
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
    HttpClient client = newHttpClient();
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
    HttpClient client = newHttpClient();
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
    HttpClient client = newHttpClient();
    HttpRequest request = HttpRequest.newBuilder()
                                     .uri(URI.create(url))
                                     .header(AUTHORIZATION, BEARER + token)
                                     .method("DELETE", HttpRequest.BodyPublishers.ofString(contentAsJson))
                                     .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Sends a GET request whose whole exchange, response headers included, is
   * bounded by {@code timeout}: an unresponsive Matrix server then fails the
   * call with an {@link java.net.http.HttpTimeoutException} instead of holding
   * the calling thread.
   *
   * @param url the request url
   * @param token the bearer access token
   * @param timeout the request timeout
   * @return the response
   * @throws IOException when the request fails or times out
   * @throws InterruptedException when the request is interrupted
   */
  protected static HttpResponse<String> sendBoundedGetRequest(String url, String token, Duration timeout) throws IOException,
                                                                                                         InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
                                     .uri(URI.create(url))
                                     .timeout(timeout)
                                     .header(AUTHORIZATION, BEARER + token)
                                     .GET()
                                     .build();
    return newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Sends a POST request bounded by {@code timeout}, see
   * {@link #sendBoundedGetRequest(String, String, Duration)}.
   *
   * @param url the request url
   * @param token the bearer access token, none when blank
   * @param contentAsJson the JSON body
   * @param timeout the request timeout
   * @return the response
   * @throws IOException when the request fails or times out
   * @throws InterruptedException when the request is interrupted
   */
  protected static HttpResponse<String> sendBoundedPostRequest(String url,
                                                               String token,
                                                               String contentAsJson,
                                                               Duration timeout) throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
                                             .uri(URI.create(url))
                                             .timeout(timeout)
                                             .POST(HttpRequest.BodyPublishers.ofString(contentAsJson));
    if (StringUtils.isNotBlank(token)) {
      builder.header(AUTHORIZATION, BEARER + token);
    }
    return newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  /**
   * Sends a PUT request bounded by {@code timeout}, see
   * {@link #sendBoundedGetRequest(String, String, Duration)}.
   *
   * @param url the request url
   * @param token the bearer access token
   * @param contentAsJson the JSON body
   * @param timeout the request timeout
   * @return the response
   * @throws IOException when the request fails or times out
   * @throws InterruptedException when the request is interrupted
   */
  protected static HttpResponse<String> sendBoundedPutRequest(String url,
                                                              String token,
                                                              String contentAsJson,
                                                              Duration timeout) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder()
                                     .uri(URI.create(url))
                                     .timeout(timeout)
                                     .header(AUTHORIZATION, BEARER + token)
                                     .PUT(HttpRequest.BodyPublishers.ofString(contentAsJson))
                                     .build();
    return newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  public static void sendInvitationToMembers(ArrayList<String> strings, String matrixRoomId) {

  }
}
