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

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_SERVER_URL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import org.exoplatform.commons.utils.PropertyManager;

import io.meeds.chat.model.MatrixPushRule;
import io.meeds.chat.model.MatrixPusher;
import io.meeds.chat.service.MatrixAgentPusherService;

/**
 * Replays an exchange recorded between this client and a live Synapse 1.155.0
 * ({@code matrixdotorg/synapse:v1.155.0}, a fresh account, the server's
 * default push rules): each {@code *-request.json} under
 * {@code /synapse-1.155.0/} holds the bytes this client sent and Synapse
 * answered with a 200, each {@code *-response.json} the bytes Synapse
 * answered, kept verbatim. A request built otherwise, or a parse that reads
 * another shape than the one Synapse answers, fails here. To record again for
 * another Synapse version, put a logging proxy between the client and the
 * server and replace the files with what it saw.
 */
class MatrixHttpClientSynapseRecordingTest {

  private static final String           RECORDING        = "/synapse-1.155.0/";

  private static final Duration         TIMEOUT          = Duration.ofSeconds(10);

  private static final String           ACCESS_TOKEN     = "workerToken";

  private static final String           PUSHKEY          = "bGVh.q1F3cXkzWmV0c0R0b1pJZ2JmNVRtTkJ4X1lxd0VQcmY";

  private static final String           GATEWAY          = "https://acme.com/_matrix/push/v1/notify";

  /** The server-default rules a worker's reconcile disables, as Synapse 1.155.0 names them. */
  private static final Set<String>      MANAGED_RULE_IDS = Set.of("override/.m.rule.contains_display_name",
                                                                  "override/.m.rule.is_room_mention",
                                                                  "override/.m.rule.roomnotif",
                                                                  "content/.m.rule.contains_user_name",
                                                                  "underride/.m.rule.call",
                                                                  "underride/.m.rule.encrypted_room_one_to_one",
                                                                  "underride/.m.rule.room_one_to_one",
                                                                  "underride/.m.rule.message",
                                                                  "underride/.m.rule.encrypted",
                                                                  "underride/.im.vector.jitsi");

  private MockedStatic<HTTPHelper>      httpHelper;

  private final MatrixHttpClient        matrixHttpClient = new MatrixHttpClient();

  @BeforeEach
  void setUp() {
    PropertyManager.setProperty(MATRIX_SERVER_URL, "http://matrix:8008");
    httpHelper = mockStatic(HTTPHelper.class);
  }

  @AfterEach
  void tearDown() {
    httpHelper.close();
  }

  @Test
  void setPusherSendsTheBodySynapseAccepted() throws Exception {
    HttpResponse<String> answer = answer(200, "{}");
    httpHelper.when(() -> HTTPHelper.sendBoundedPostRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)))
              .thenReturn(answer);

    matrixHttpClient.setPusher(new MatrixPusher(MatrixConstants.AGENT_PUSHER_APP_ID,
                                                PUSHKEY,
                                                "http",
                                                "eXo digital worker",
                                                "Server",
                                                "lea",
                                                "en",
                                                GATEWAY),
                               ACCESS_TOKEN,
                               TIMEOUT);

    assertSameJson("pushers-set-request.json", sentPost("http://matrix:8008/_matrix/client/v3/pushers/set"));
  }

  @Test
  void deletePusherSendsTheBodySynapseAccepted() throws Exception {
    HttpResponse<String> answer = answer(200, "{}");
    httpHelper.when(() -> HTTPHelper.sendBoundedPostRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)))
              .thenReturn(answer);

    matrixHttpClient.deletePusher(MatrixConstants.AGENT_PUSHER_APP_ID, PUSHKEY, ACCESS_TOKEN, TIMEOUT);

    assertSameJson("pushers-delete-request.json", sentPost("http://matrix:8008/_matrix/client/v3/pushers/set"));
  }

  @Test
  void getPushersReadsTheAnswerOfSynapse() throws Exception {
    HttpResponse<String> answer = answer(200, recorded("pushers-response.json"));
    httpHelper.when(() -> HTTPHelper.sendBoundedGetRequest(eq("http://matrix:8008/_matrix/client/v3/pushers"),
                                                           eq(ACCESS_TOKEN),
                                                           eq(TIMEOUT)))
              .thenReturn(answer);

    assertEquals(List.of(new MatrixPusher(MatrixConstants.AGENT_PUSHER_APP_ID,
                                          PUSHKEY,
                                          "http",
                                          "eXo digital worker",
                                          "Server",
                                          "lea",
                                          "en",
                                          GATEWAY)),
                 matrixHttpClient.getPushers(ACCESS_TOKEN, TIMEOUT));
  }

  /**
   * Synapse refuses a pusher whose gateway path is not the push gateway's,
   * the answer the gateway url check of the pusher service anticipates.
   */
  @Test
  void setPusherWithAnotherGatewayPathFailsOnSynapsesAnswer() {
    HttpResponse<String> answer = answer(400, recorded("pushers-set-wrong-path-response.json"));
    httpHelper.when(() -> HTTPHelper.sendBoundedPostRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)))
              .thenReturn(answer);

    assertThrows(MatrixException.class,
                 () -> matrixHttpClient.setPusher(new MatrixPusher(MatrixConstants.AGENT_PUSHER_APP_ID,
                                                                   PUSHKEY,
                                                                   "http",
                                                                   "eXo digital worker",
                                                                   "Server",
                                                                   "lea",
                                                                   "en",
                                                                   "https://acme.com/matrix/rest/matrix/notify"),
                                                  ACCESS_TOKEN,
                                                  TIMEOUT));
  }

  /**
   * The server's default rules are read with their kind, enabled flag and
   * notify action; the rules the reconcile manages are exactly the notifying
   * underrides, the legacy name and room-mention overrides and the user-name
   * content rule, and the trigger and the invitation rule are read notifying
   * and left alone.
   */
  @Test
  void getPushRulesReadsTheDefaultRulesOfSynapse() throws Exception {
    HttpResponse<String> answer = answer(200, recorded("pushrules-global-response.json"));
    httpHelper.when(() -> HTTPHelper.sendBoundedGetRequest(eq("http://matrix:8008/_matrix/client/v3/pushrules/global/"),
                                                           eq(ACCESS_TOKEN),
                                                           eq(TIMEOUT)))
              .thenReturn(answer);

    Map<String, MatrixPushRule> rules = byKindAndId(matrixHttpClient.getPushRules(ACCESS_TOKEN, TIMEOUT));

    assertEquals(19, rules.size());
    assertEquals(MANAGED_RULE_IDS,
                 rules.entrySet()
                      .stream()
                      .filter(rule -> MatrixAgentPusherService.isManagedRule(rule.getValue()))
                      .map(Map.Entry::getKey)
                      .collect(Collectors.toSet()));
    MANAGED_RULE_IDS.forEach(id -> assertTrue(rules.get(id).enabled() && rules.get(id).notifying(), id));
    assertTrue(rules.get("override/.m.rule.is_user_mention").notifying());
    assertTrue(rules.get("override/.m.rule.invite_for_me").notifying());
    assertFalse(rules.get("override/.m.rule.master").enabled());
    assertFalse(rules.get("override/.m.rule.suppress_notices").notifying());
  }

  /** The rule update is sent as Synapse accepted it, and the next read shows every managed rule off. */
  @Test
  void setPushRuleEnabledSendsTheBodySynapseAcceptedAndTheReadShowsIt() throws Exception {
    HttpResponse<String> answer = answer(200, "{}");
    httpHelper.when(() -> HTTPHelper.sendBoundedPutRequest(anyString(), anyString(), anyString(), eq(TIMEOUT)))
              .thenReturn(answer);

    matrixHttpClient.setPushRuleEnabled("override", ".m.rule.contains_display_name", false, ACCESS_TOKEN, TIMEOUT);

    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    httpHelper.verify(() -> HTTPHelper.sendBoundedPutRequest(eq("http://matrix:8008/_matrix/client/v3/pushrules/global/override/.m.rule.contains_display_name/enabled"),
                                                             eq(ACCESS_TOKEN),
                                                             payload.capture(),
                                                             eq(TIMEOUT)));
    assertSameJson("pushrule-enabled-request.json", payload.getValue());

    HttpResponse<String> disabledAnswer = answer(200, recorded("pushrules-global-disabled-response.json"));
    httpHelper.when(() -> HTTPHelper.sendBoundedGetRequest(anyString(), anyString(), eq(TIMEOUT)))
              .thenReturn(disabledAnswer);
    Map<String, MatrixPushRule> rules = byKindAndId(matrixHttpClient.getPushRules(ACCESS_TOKEN, TIMEOUT));
    MANAGED_RULE_IDS.forEach(id -> assertFalse(rules.get(id).enabled(), id));
    assertTrue(rules.get("override/.m.rule.is_user_mention").enabled());
  }

  private String sentPost(String url) {
    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    httpHelper.verify(() -> HTTPHelper.sendBoundedPostRequest(eq(url), eq(ACCESS_TOKEN), payload.capture(), eq(TIMEOUT)));
    return payload.getValue();
  }

  private static void assertSameJson(String recordedFile, String sent) throws IOException {
    String expected = recorded(recordedFile);
    assertTrue(new JSONObject(expected).similar(new JSONObject(sent)), "sent " + sent + ", Synapse accepted " + expected);
  }

  private static Map<String, MatrixPushRule> byKindAndId(List<MatrixPushRule> rules) {
    return rules.stream().collect(Collectors.toMap(rule -> rule.kind() + "/" + rule.ruleId(), Function.identity()));
  }

  @SuppressWarnings("unchecked")
  private static HttpResponse<String> answer(int status, String body) {
    HttpResponse<String> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(status);
    when(response.body()).thenReturn(body);
    return response;
  }

  private static String recorded(String file) {
    try (InputStream stream = MatrixHttpClientSynapseRecordingTest.class.getResourceAsStream(RECORDING + file)) {
      assertNotNull(stream, file);
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException(file, e);
    }
  }
}
