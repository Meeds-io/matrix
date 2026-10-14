/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.chat.service.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The bounded requests against a real socket, through the JDK client the
 * platform runs: the bound must hold over the response body, which the
 * request's own timeout does not cover.
 */
class HTTPHelperTest {

  private ServerSocket   server;

  private CountDownLatch release = new CountDownLatch(1);

  @BeforeEach
  void startServer() throws Exception {
    server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
    Thread serving = new Thread(() -> {
      try (Socket socket = server.accept()) {
        socket.getInputStream().read(new byte[4096]);
        OutputStream out = socket.getOutputStream();
        // headers then five bytes of a hundred-byte body, then a stall
        out.write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nContent-Type: application/json\r\n\r\n{\"a\":".getBytes(StandardCharsets.US_ASCII));
        out.flush();
        release.await(30, TimeUnit.SECONDS);
      } catch (Exception e) {
        // the client gave up and closed the connection
      }
    });
    serving.setDaemon(true);
    serving.start();
  }

  @AfterEach
  void stopServer() throws Exception {
    release.countDown();
    server.close();
  }

  @Test
  void aBodyThatStallsIsBoundedByTheTimeout() {
    String url = "http://127.0.0.1:" + server.getLocalPort() + "/_matrix/client/v3/pushers";

    long start = System.nanoTime();
    HttpTimeoutException timeout = assertTimeout(Duration.ofSeconds(10),
                                                 () -> assertThrows(HttpTimeoutException.class,
                                                                    () -> HTTPHelper.sendBoundedGetRequest(url,
                                                                                                           "token",
                                                                                                           Duration.ofSeconds(1))));
    long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

    assertTrue(elapsedMs >= 900 && elapsedMs < 5000, "gave up after " + elapsedMs + " ms");
    assertEquals(true, timeout.getMessage().contains("127.0.0.1"));
  }
}
