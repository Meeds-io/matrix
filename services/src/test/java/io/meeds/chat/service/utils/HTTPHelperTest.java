/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

  @Test
  void sendHttpGetRequestGivesUpOnAServerThatAcceptsTheConnectionAndNeverAnswers() throws Exception {
    assertGivesUpOn(null);
  }

  @Test
  void sendHttpGetRequestGivesUpOnAServerThatSendsTheHeadersAndStallsTheBody() throws Exception {
    assertGivesUpOn("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100\r\n\r\n{");
  }

  @Test
  void sendHttpGetRequestAbortsTheExchangeWhenTheCallerIsInterrupted() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      AtomicReference<Throwable> thrown = new AtomicReference<>();
      Thread caller = new Thread(() -> {
        try {
          HTTPHelper.sendHttpGetRequest("http://127.0.0.1:" + server.getLocalPort() + "/stall", "token", Duration.ofMinutes(1));
        } catch (Throwable e) {
          thrown.set(e);
        }
      });
      caller.setDaemon(true);
      caller.start();
      try (Socket socket = server.accept()) {
        socket.getOutputStream()
              .write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\n\r\n{".getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().flush();
        Thread.sleep(300);
        caller.interrupt();
        caller.join(5000);
        assertInstanceOf(InterruptedException.class, thrown.get());

        // the exchange is aborted: the client closes the connection instead of waiting for the body
        socket.setSoTimeout(5000);
        assertEquals(-1, readUntilClosed(socket));
      }
    }
  }

  private int readUntilClosed(Socket socket) throws Exception {
    try {
      int read;
      do {
        read = socket.getInputStream().read();
      } while (read != -1);
      return read;
    } catch (java.net.SocketException e) {
      return -1;
    }
  }

  private void assertGivesUpOn(String partialAnswer) throws Exception {
    List<Socket> accepted = new ArrayList<>();
    try (ServerSocket server = new ServerSocket(0)) {
      Thread acceptor = new Thread(() -> {
        try {
          Socket socket = server.accept();
          accepted.add(socket);
          if (partialAnswer != null) {
            socket.getOutputStream().write(partialAnswer.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
          }
        } catch (Exception e) {
          // the server socket is closed at the end of the test
        }
      });
      acceptor.setDaemon(true);
      acceptor.start();

      assertTimeoutPreemptively(Duration.ofSeconds(5),
                                () -> assertThrows(HttpTimeoutException.class,
                                                   () -> HTTPHelper.sendHttpGetRequest("http://127.0.0.1:" + server.getLocalPort()
                                                       + "/never", "token", Duration.ofMillis(500))));
    } finally {
      for (Socket socket : accepted) {
        socket.close();
      }
    }
  }
}
