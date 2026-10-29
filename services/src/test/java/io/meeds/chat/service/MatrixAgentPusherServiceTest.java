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
package io.meeds.chat.service;

import static io.meeds.chat.service.MatrixAgentPusherService.SYNAPSE_TIMEOUT;
import static io.meeds.chat.service.utils.MatrixConstants.AGENT_PUSHER_APP_ID;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_AGENTS_PUSH_GATEWAY_URL;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_JWT_SECRET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserHandler;
import org.exoplatform.services.organization.UserStatus;
import org.exoplatform.social.core.identity.model.Identity;

import io.meeds.chat.model.MatrixPushRule;
import io.meeds.chat.model.MatrixPusher;
import io.meeds.chat.service.utils.MatrixException;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.chat.service.utils.MatrixUserCall;
import io.meeds.social.core.identity.plugin.AccountKindPlugin;
import io.meeds.social.core.identity.service.AccountKindService;

@ExtendWith(MockitoExtension.class)
class MatrixAgentPusherServiceTest {

  private static final String      LOGIN       = "lea";

  private static final String      MATRIX_ID   = "@lea:matrix.exo.tn";

  private static final String      GATEWAY     = "https://acme.com/_matrix/push/v1/notify";

  private static final String      TOKEN       = "leaToken";

  private static final Instant     NOW         = Instant.parse("2026-10-14T12:00:00Z");

  private static final String      KIND_GROUP  = "/platform/agents";

  @Mock
  private MatrixService            matrixService;

  @Mock
  private MatrixHttpClient         matrixHttpClient;

  @Mock
  private AccountKindService       accountKindService;

  @Mock
  private OrganizationService      organizationService;

  @Mock
  private UserHandler              userHandler;

  private final List<Runnable>     scheduled   = new ArrayList<>();

  private MatrixAgentPusherService service;

  @BeforeEach
  void setUp() throws Exception {
    PropertyManager.setProperty(MATRIX_JWT_SECRET, "a-test-secret-of-at-least-thirty-two-bytes");
    PropertyManager.setProperty(MATRIX_AGENTS_PUSH_GATEWAY_URL, GATEWAY);
    service = new MatrixAgentPusherService(matrixService, matrixHttpClient, accountKindService, organizationService);
    service.shutdown();
    service.executor = scheduled::add;
    service.clock = Clock.fixed(NOW, ZoneOffset.UTC);
    lenient().when(organizationService.getUserHandler()).thenReturn(userHandler);
    lenient().doAnswer(invocation -> invocation.<MatrixUserCall<?>> getArgument(3).call(TOKEN))
             .when(matrixService)
             .callAsUserOrThrow(anyString(), any(), eq(SYNAPSE_TIMEOUT), any());
  }

  @AfterEach
  void tearDown() {
    PropertyManager.setProperty(MATRIX_AGENTS_PUSH_GATEWAY_URL, "");
  }

  @Test
  void pushkeyIsDerivedPerLoginAndVerified() {
    String pushkey = service.getPushkey(LOGIN);

    assertEquals(pushkey, service.getPushkey(LOGIN));
    assertNotEquals(pushkey, service.getPushkey("max"));
    assertEquals(LOGIN, service.getLoginOfPushkey(pushkey));
    assertTrue(pushkey.startsWith("bGVh."));
  }

  @Test
  void pushkeyWithABadMacOrShapeIsRefused() {
    String pushkey = service.getPushkey(LOGIN);
    String forged = "bWF4." + pushkey.substring(pushkey.indexOf('.') + 1);

    assertNull(service.getLoginOfPushkey(forged));
    assertNull(service.getLoginOfPushkey(pushkey.substring(0, pushkey.length() - 2) + "AA"));
    assertNull(service.getLoginOfPushkey("bGVh"));
    assertNull(service.getLoginOfPushkey("bGVh.a.b"));
    assertNull(service.getLoginOfPushkey("!!!.???"));
    assertNull(service.getLoginOfPushkey(""));
    assertNull(service.getLoginOfPushkey(null));
  }

  @Test
  void rotatingTheJwtSecretInvalidatesEveryPushkey() {
    String pushkey = service.getPushkey(LOGIN);
    PropertyManager.setProperty(MATRIX_JWT_SECRET, "another-test-secret-of-at-least-thirty-two-bytes");

    assertNull(service.getLoginOfPushkey(pushkey));
  }

  @Test
  void gatewayAcceptsTheKeyOfAWorkerWithAKindAndAMatrixAccount() {
    when(accountKindService.computeAccountKind(LOGIN)).thenReturn("agent");
    when(matrixService.getMatrixIdForUser(LOGIN)).thenReturn(MATRIX_ID);

    assertEquals(LOGIN, service.getPushableLogin(service.getPushkey(LOGIN)));
  }

  /**
   * The kind is computed from the memberships, the rule that registered the
   * pusher, never read from the kind the profile shows.
   */
  @Test
  void gatewayRejectsTheKeyOfAnAccountOfNoKind() {
    when(accountKindService.computeAccountKind(LOGIN)).thenReturn(null);

    assertNull(service.getPushableLogin(service.getPushkey(LOGIN)));
    verify(accountKindService, never()).getDisplayedKind(any(Identity.class));
    verify(matrixService, never()).getMatrixIdForUser(anyString());
  }

  @Test
  void gatewayRejectsTheKeyOfAWorkerWithoutMatrixAccount() {
    when(accountKindService.computeAccountKind(LOGIN)).thenReturn("agent");
    when(matrixService.getMatrixIdForUser(LOGIN)).thenReturn(null, "");

    assertNull(service.getPushableLogin(service.getPushkey(LOGIN)));
    assertNull(service.getPushableLogin(service.getPushkey(LOGIN)));
  }

  @Test
  void gatewayRejectsAForgedKeyWithoutReadingTheAccount() {
    String pushkey = service.getPushkey(LOGIN);

    assertNull(service.getPushableLogin("bWF4." + pushkey.substring(pushkey.indexOf('.') + 1)));
    assertNull(service.getPushableLogin(null));
    verifyNoInteractions(accountKindService, matrixService);
  }

  /**
   * Unreadable memberships are thrown, never answered as an account of no
   * kind: the gateway then answers that nothing is rejected, and the pusher
   * of a worker stays registered through an IDM outage.
   */
  @Test
  void gatewayDecisionThrowsWhenTheMembershipsCannotBeRead() {
    when(accountKindService.computeAccountKind(LOGIN)).thenThrow(new IllegalStateException("IDM down"));
    String pushkey = service.getPushkey(LOGIN);

    assertThrows(IllegalStateException.class, () -> service.getPushableLogin(pushkey));
  }

  /**
   * The rig's notifying rules, three experimental underrides off: every one of
   * them is managed, and nothing else.
   */
  @Test
  void managedRuleSet() {
    List<String> managedUnderrides = List.of(".m.rule.call",
                                             ".m.rule.encrypted_room_one_to_one",
                                             ".m.rule.room_one_to_one",
                                             ".m.rule.message",
                                             ".m.rule.encrypted",
                                             ".im.vector.jitsi");
    managedUnderrides.forEach(id -> assertTrue(MatrixAgentPusherService.isManagedRule(rule("underride", id, true)), id));
    assertTrue(MatrixAgentPusherService.isManagedRule(rule("override", ".m.rule.contains_display_name", true)));
    assertTrue(MatrixAgentPusherService.isManagedRule(rule("override", ".m.rule.is_room_mention", true)));
    assertTrue(MatrixAgentPusherService.isManagedRule(rule("override", ".m.rule.roomnotif", true)));
    assertTrue(MatrixAgentPusherService.isManagedRule(rule("content", ".m.rule.contains_user_name", true)));

    assertFalse(MatrixAgentPusherService.isManagedRule(rule("override", ".m.rule.is_user_mention", true)));
    assertFalse(MatrixAgentPusherService.isManagedRule(rule("override", ".m.rule.invite_for_me", true)));
    assertFalse(MatrixAgentPusherService.isManagedRule(rule("override", ".m.rule.suppress_notices", false)));
    assertFalse(MatrixAgentPusherService.isManagedRule(rule("override", ".m.rule.master", false)));
    assertFalse(MatrixAgentPusherService.isManagedRule(rule("underride", ".m.rule.not_notifying", false)));
    assertFalse(MatrixAgentPusherService.isManagedRule(rule("underride", "custom.rule", true)));
    assertFalse(MatrixAgentPusherService.isManagedRule(rule("content", "my.keyword", true)));
    assertFalse(MatrixAgentPusherService.isManagedRule(rule("room", ".m.rule.room", true)));
  }

  @Test
  void gatewayUrl() {
    PropertyManager.setProperty(MATRIX_AGENTS_PUSH_GATEWAY_URL, "");
    MatrixAgentPusherService defaulted = spy(service);
    when(defaulted.getPlatformBaseUrl()).thenReturn("https://acme.com:8443");
    assertEquals("https://acme.com:8443/_matrix/push/v1/notify", defaulted.resolveGatewayUrl());

    PropertyManager.setProperty(MATRIX_AGENTS_PUSH_GATEWAY_URL, " http://notify.internal/_matrix/push/v1/notify ");
    assertEquals("http://notify.internal/_matrix/push/v1/notify", service.resolveGatewayUrl());

    List.of("https://acme.com/matrix/rest/matrix/notify",
            "ftp://acme.com/_matrix/push/v1/notify",
            "/_matrix/push/v1/notify",
            "https://acme.com/_matrix/push/v1/notify?x=1",
            "https://acme.com/_matrix/push/v1/notify#f",
            "https://acme.com/_matrix/push/v1/notify/",
            // Synapse compares the raw path: an escaped letter is another path
            "https://acme.com/_matrix/push/v1/%6Eotify",
            "not a url")
        .forEach(url -> {
          PropertyManager.setProperty(MATRIX_AGENTS_PUSH_GATEWAY_URL, url);
          assertNull(service.resolveGatewayUrl(), url);
        });
  }

  @Test
  void isDesired() throws Exception {
    when(accountKindService.computeAccountKind(LOGIN)).thenReturn("agent");
    User enabled = user(true);
    when(userHandler.findUserByName(LOGIN, UserStatus.ANY)).thenReturn(enabled);
    when(matrixService.getMatrixIdForUser(LOGIN)).thenReturn(MATRIX_ID);
    assertTrue(service.isDesired(LOGIN));

    when(matrixService.getMatrixIdForUser(LOGIN)).thenReturn(null);
    assertFalse(service.isDesired(LOGIN));

    lenient().when(matrixService.getMatrixIdForUser(LOGIN)).thenReturn(MATRIX_ID);
    when(enabled.isEnabled()).thenReturn(false);
    assertFalse(service.isDesired(LOGIN));

    when(accountKindService.computeAccountKind("john")).thenReturn(null);
    assertFalse(service.isDesired("john"));
    verify(userHandler, never()).findUserByName(eq("john"), any());
  }

  @Test
  void noPusherIsSet() throws Exception {
    desired();
    when(matrixHttpClient.getPushers(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of());
    when(matrixHttpClient.getPushRules(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of());

    service.reconcileTurn(LOGIN, null, GATEWAY);

    verify(matrixHttpClient).setPusher(eq(new MatrixPusher(AGENT_PUSHER_APP_ID,
                                                           service.getPushkey(LOGIN),
                                                           "http",
                                                           "eXo digital worker",
                                                           "Server",
                                                           LOGIN,
                                                           "en",
                                                           GATEWAY)),
                                       eq(TOKEN),
                                       eq(SYNAPSE_TIMEOUT));
    verify(matrixHttpClient, never()).deletePusher(anyString(), anyString(), anyString(), any());
  }

  @Test
  void matchingPusherIsKeptAndBrowserPushersIgnored() throws Exception {
    desired();
    when(matrixHttpClient.getPushers(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of(agentPusher(service.getPushkey(LOGIN), GATEWAY),
                                                                                 new MatrixPusher("exo.matrix.app",
                                                                                                  "jwt",
                                                                                                  "http",
                                                                                                  null,
                                                                                                  null,
                                                                                                  null,
                                                                                                  null,
                                                                                                  GATEWAY)));
    when(matrixHttpClient.getPushRules(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of());

    service.reconcileTurn(LOGIN, null, GATEWAY);

    verify(matrixHttpClient, never()).setPusher(any(), anyString(), any());
    verify(matrixHttpClient, never()).deletePusher(anyString(), anyString(), anyString(), any());
  }

  @Test
  void expectedKeyWithAStaleUrlIsSetAgain() throws Exception {
    desired();
    when(matrixHttpClient.getPushers(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of(agentPusher(service.getPushkey(LOGIN),
                                                                                             "https://old.com/_matrix/push/v1/notify")));
    when(matrixHttpClient.getPushRules(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of());

    service.reconcileTurn(LOGIN, null, GATEWAY);

    verify(matrixHttpClient).setPusher(any(), eq(TOKEN), eq(SYNAPSE_TIMEOUT));
    verify(matrixHttpClient, never()).deletePusher(anyString(), anyString(), anyString(), any());
  }

  @Test
  void extraPushersAreDeletedAfterTheSetTenPerTurn() throws Exception {
    desired();
    List<MatrixPusher> pushers = new ArrayList<>(IntStream.range(0, 12).mapToObj(i -> agentPusher("old" + i, GATEWAY)).toList());
    when(matrixHttpClient.getPushers(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(pushers);
    when(matrixHttpClient.getPushRules(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of());

    service.reconcileTurn(LOGIN, null, GATEWAY);

    var order = org.mockito.Mockito.inOrder(matrixHttpClient);
    order.verify(matrixHttpClient).setPusher(any(), eq(TOKEN), eq(SYNAPSE_TIMEOUT));
    order.verify(matrixHttpClient, times(10)).deletePusher(eq(AGENT_PUSHER_APP_ID), anyString(), eq(TOKEN), eq(SYNAPSE_TIMEOUT));
    verify(matrixHttpClient, never()).deletePusher(anyString(), eq("old10"), anyString(), any());
    verify(matrixHttpClient, never()).deletePusher(anyString(), eq("old11"), anyString(), any());
  }

  @Test
  void failedSetDeletesNothing() throws Exception {
    desired();
    when(matrixHttpClient.getPushers(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of(agentPusher("old", GATEWAY)));
    doThrow(new MatrixException("HTTP 500")).when(matrixHttpClient).setPusher(any(), anyString(), any());

    service.reconcileTurn(LOGIN, null, GATEWAY);

    verify(matrixHttpClient, never()).deletePusher(anyString(), anyString(), anyString(), any());
    verify(matrixHttpClient, never()).getPushRules(anyString(), any());
  }

  @Test
  void failedPushersReadSetsAndDeletesNothing() throws Exception {
    desired();
    when(matrixHttpClient.getPushers(TOKEN, SYNAPSE_TIMEOUT)).thenThrow(new IOException("request timed out"));

    service.reconcileTurn(LOGIN, null, GATEWAY);

    verify(matrixHttpClient, never()).setPusher(any(), anyString(), any());
    verify(matrixHttpClient, never()).deletePusher(anyString(), anyString(), anyString(), any());
  }

  @Test
  void onlyDriftedManagedRulesAreDisabledTenPerTurn() throws Exception {
    desired();
    when(matrixHttpClient.getPushers(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of(agentPusher(service.getPushkey(LOGIN), GATEWAY)));
    List<MatrixPushRule> rules = new ArrayList<>();
    rules.add(rule("override", ".m.rule.is_user_mention", true));
    rules.add(rule("override", ".m.rule.invite_for_me", true));
    rules.add(rule("underride", "custom.rule", true));
    rules.add(rule("underride", ".m.rule.message", false));
    rules.add(new MatrixPushRule("override", ".m.rule.roomnotif", true, true));
    IntStream.range(0, 11).forEach(i -> rules.add(rule("underride", ".org.example.flag" + i, true)));
    when(matrixHttpClient.getPushRules(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(rules);

    service.reconcileTurn(LOGIN, null, GATEWAY);

    verify(matrixHttpClient, times(10)).setPushRuleEnabled(anyString(), anyString(), eq(false), eq(TOKEN), eq(SYNAPSE_TIMEOUT));
    verify(matrixHttpClient).setPushRuleEnabled("override", ".m.rule.roomnotif", false, TOKEN, SYNAPSE_TIMEOUT);
    verify(matrixHttpClient, never()).setPushRuleEnabled(anyString(), eq(".m.rule.is_user_mention"), anyBoolean(), any(), any());
    verify(matrixHttpClient, never()).setPushRuleEnabled(anyString(), eq(".m.rule.invite_for_me"), anyBoolean(), any(), any());
    verify(matrixHttpClient, never()).setPushRuleEnabled(anyString(), eq("custom.rule"), anyBoolean(), any(), any());
    verify(matrixHttpClient, never()).setPushRuleEnabled(anyString(), eq(".m.rule.message"), anyBoolean(), any(), any());
    verify(matrixHttpClient, never()).setPushRuleEnabled(anyString(), anyString(), eq(true), any(), any());
  }

  /**
   * An account that no longer wants a pusher loses none: the gateway rejects
   * its key at the first push. Its managed rules return to the server default.
   */
  @Test
  void accountThatLeftItsKindDeletesNothingAndRestoresItsRules() throws Exception {
    when(accountKindService.computeAccountKind(LOGIN)).thenReturn(null);
    User enabled = user(true);
    when(userHandler.findUserByName(LOGIN, UserStatus.ANY)).thenReturn(enabled);
    when(matrixService.getMatrixIdForUser(LOGIN)).thenReturn(MATRIX_ID);
    when(matrixHttpClient.getPushRules(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of(rule("underride", ".m.rule.message", false),
                                                                                   rule("override", ".m.rule.is_room_mention", false),
                                                                                   rule("underride", "custom.rule", false),
                                                                                   rule("override", ".m.rule.is_user_mention", true)));

    service.reconcileTurn(LOGIN, null, GATEWAY);

    verify(matrixHttpClient).setPushRuleEnabled("underride", ".m.rule.message", true, TOKEN, SYNAPSE_TIMEOUT);
    verify(matrixHttpClient).setPushRuleEnabled("override", ".m.rule.is_room_mention", true, TOKEN, SYNAPSE_TIMEOUT);
    verify(matrixHttpClient, times(2)).setPushRuleEnabled(anyString(), anyString(), anyBoolean(), any(), any());
    verify(matrixHttpClient, never()).getPushers(anyString(), any());
    verify(matrixHttpClient, never()).setPusher(any(), anyString(), any());
    verify(matrixHttpClient, never()).deletePusher(anyString(), anyString(), anyString(), any());
  }

  @Test
  void disabledAccountIsLeftToSynapseDeactivation() throws Exception {
    when(accountKindService.computeAccountKind(LOGIN)).thenReturn("agent");
    User disabled = user(false);
    when(userHandler.findUserByName(LOGIN, UserStatus.ANY)).thenReturn(disabled);

    service.reconcileTurn(LOGIN, null, GATEWAY);

    verifyNoInteractions(matrixHttpClient);
  }

  @Test
  void unreadableDecisionLeavesTheAccountUntouched() throws Exception {
    when(accountKindService.computeAccountKind(LOGIN)).thenThrow(new IllegalStateException("IDM down"));

    service.reconcileTurn(LOGIN, null, GATEWAY);

    verifyNoInteractions(matrixHttpClient);
    verify(matrixService, never()).callAsUserOrThrow(anyString(), any(), any(), any());
  }

  @Test
  void invalidGatewayRegistersNothing() throws Exception {
    desired();

    service.reconcileTurn(LOGIN, null, null);

    verifyNoInteractions(matrixHttpClient);
  }

  /**
   * A queued turn younger than five minutes trusts the calling thread, whose
   * request may not have committed the re-enable, the kind membership or the
   * Matrix id yet: it reads none of them back and uses the carried Matrix id.
   */
  @Test
  void recentCarriedDecisionIsTrusted() throws Exception {
    when(matrixHttpClient.getPushers(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of());
    when(matrixHttpClient.getPushRules(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of(rule("underride", ".m.rule.message", true)));
    service.clock = Clock.fixed(NOW.minus(Duration.ofMinutes(4)), ZoneOffset.UTC);
    service.enqueueDesired(LOGIN, MATRIX_ID);
    service.clock = Clock.fixed(NOW, ZoneOffset.UTC);

    service.processPendingTurn(LOGIN);

    verifyNoInteractions(accountKindService);
    verify(matrixService, never()).getMatrixIdForUser(anyString());
    verify(matrixService).callAsUserOrThrow(eq(LOGIN), eq(MATRIX_ID), eq(SYNAPSE_TIMEOUT), any());
    verify(matrixHttpClient).setPusher(any(), eq(TOKEN), eq(SYNAPSE_TIMEOUT));
    verify(matrixHttpClient).setPushRuleEnabled("underride", ".m.rule.message", false, TOKEN, SYNAPSE_TIMEOUT);
  }

  @Test
  void staleCarriedDecisionIsReadAgain() throws Exception {
    when(accountKindService.computeAccountKind(LOGIN)).thenReturn(null);
    when(userHandler.findUserByName(LOGIN, UserStatus.ANY)).thenReturn(null);
    service.clock = Clock.fixed(NOW.minus(Duration.ofMinutes(6)), ZoneOffset.UTC);
    service.enqueueDesired(LOGIN, MATRIX_ID);
    service.clock = Clock.fixed(NOW, ZoneOffset.UTC);

    service.processPendingTurn(LOGIN);

    verify(accountKindService).computeAccountKind(LOGIN);
    verifyNoInteractions(matrixHttpClient);
  }

  @Test
  void queueMergeRules() throws Exception {
    service.enqueueDesired(LOGIN, MATRIX_ID);
    service.enqueueDesired(LOGIN, MATRIX_ID);
    assertEquals(1, scheduled.size());

    service.enqueueLeftKind(LOGIN);
    service.enqueueDesired(LOGIN, MATRIX_ID);
    assertEquals(1, scheduled.size());

    // the pending turn is the left-kind one: it re-reads, finds no kind
    when(accountKindService.computeAccountKind(LOGIN)).thenReturn(null);
    when(userHandler.findUserByName(LOGIN, UserStatus.ANY)).thenReturn(null);
    service.processPendingTurn(LOGIN);
    verify(accountKindService).computeAccountKind(LOGIN);
    verifyNoInteractions(matrixHttpClient);

    // once run, the next turn is scheduled again
    service.enqueueDesired(LOGIN, MATRIX_ID);
    assertEquals(2, scheduled.size());
  }

  @Test
  void fullQueueDropsDesiredTurnsNeverLeftKindOnes() {
    IntStream.range(0, MatrixAgentPusherService.MAX_PENDING_DESIRED_TURNS)
             .forEach(i -> service.enqueueDesired("worker" + i, "@worker" + i + ":matrix.exo.tn"));
    assertEquals(MatrixAgentPusherService.MAX_PENDING_DESIRED_TURNS, scheduled.size());

    service.enqueueDesired(LOGIN, MATRIX_ID);
    assertEquals(MatrixAgentPusherService.MAX_PENDING_DESIRED_TURNS, scheduled.size());

    service.enqueueLeftKind(LOGIN);
    assertEquals(MatrixAgentPusherService.MAX_PENDING_DESIRED_TURNS + 1, scheduled.size());

    // a pending account's newer desired turn still replaces its own
    service.enqueueDesired("worker0", "@worker0:matrix.exo.tn");
    assertEquals(MatrixAgentPusherService.MAX_PENDING_DESIRED_TURNS + 1, scheduled.size());
  }

  @Test
  void leavingTheLastKindGroupQueuesALeftKindTurn() {
    when(matrixService.isServiceEnabled()).thenReturn(true);
    when(accountKindService.getPluginByGroup(KIND_GROUP)).thenReturn(mock(AccountKindPlugin.class));
    when(accountKindService.computeAccountKind(LOGIN)).thenReturn(null);

    service.onMembershipDeleted(LOGIN, KIND_GROUP);

    assertEquals(1, scheduled.size());
    // a left-kind turn is never dropped by a full queue, nor replaced by a desired one
    service.enqueueDesired(LOGIN, MATRIX_ID);
    assertEquals(1, scheduled.size());
  }

  /**
   * Another membership of a kind's group was deleted, the account keeps its
   * kind.
   */
  @Test
  void leavingAKindGroupWhileStillOfAKindQueuesNothing() {
    when(matrixService.isServiceEnabled()).thenReturn(true);
    when(accountKindService.getPluginByGroup(KIND_GROUP)).thenReturn(mock(AccountKindPlugin.class));
    when(accountKindService.computeAccountKind(LOGIN)).thenReturn("agent");

    service.onMembershipDeleted(LOGIN, KIND_GROUP);

    assertTrue(scheduled.isEmpty());
  }

  @Test
  void leavingAnotherGroupReadsNoMembershipAndQueuesNothing() {
    when(matrixService.isServiceEnabled()).thenReturn(true);
    when(accountKindService.getPluginByGroup("/platform/users")).thenReturn(null);

    service.onMembershipDeleted(LOGIN, "/platform/users");

    assertTrue(scheduled.isEmpty());
    verify(accountKindService, never()).computeAccountKind(anyString());
  }

  @Test
  void membershipDeletedWhileChatIsDisabledQueuesNothing() {
    when(matrixService.isServiceEnabled()).thenReturn(false);

    service.onMembershipDeleted(LOGIN, KIND_GROUP);

    assertTrue(scheduled.isEmpty());
    verifyNoInteractions(accountKindService);
  }

  @Test
  void unreadableMembershipsAfterADeleteQueueNothingAndDoNotThrow() {
    when(matrixService.isServiceEnabled()).thenReturn(true);
    when(accountKindService.getPluginByGroup(KIND_GROUP)).thenReturn(mock(AccountKindPlugin.class));
    when(accountKindService.computeAccountKind(LOGIN)).thenThrow(new IllegalStateException("IDM down"));

    service.onMembershipDeleted(LOGIN, KIND_GROUP);

    assertTrue(scheduled.isEmpty());
  }

  /**
   * Two reconciles of one account on one node, against one stubbed Synapse
   * that keeps its pushers, end with one pusher: the second turn sees the
   * first one's.
   */
  @Test
  void concurrentReconcilesOfOneAccountSetOnePusher() throws Exception {
    desired();
    List<MatrixPusher> synapsePushers = Collections.synchronizedList(new ArrayList<>());
    CountDownLatch firstReadDone = new CountDownLatch(1);
    when(matrixHttpClient.getPushers(TOKEN, SYNAPSE_TIMEOUT)).thenAnswer(invocation -> {
      firstReadDone.countDown();
      return List.copyOf(synapsePushers);
    });
    doAnswer(invocation -> {
      Thread.sleep(200);
      synapsePushers.add(invocation.getArgument(0));
      return null;
    }).when(matrixHttpClient).setPusher(any(), anyString(), any());
    when(matrixHttpClient.getPushRules(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of());

    Thread first = new Thread(() -> service.reconcileTurn(LOGIN, null, GATEWAY));
    first.start();
    assertTrue(firstReadDone.await(5, TimeUnit.SECONDS));
    service.reconcileTurn(LOGIN, null, GATEWAY);
    first.join(5000);

    assertEquals(1, synapsePushers.size());
    verify(matrixHttpClient, times(1)).setPusher(any(), anyString(), any());
  }

  @Test
  void desiredLoginsAreTheEnabledMatrixAccountsOfEveryKindGroup() throws Exception {
    io.meeds.social.core.identity.plugin.AccountKindPlugin agents = mock(io.meeds.social.core.identity.plugin.AccountKindPlugin.class);
    when(agents.getGroupId()).thenReturn("/platform/agents");
    when(accountKindService.getPlugins()).thenReturn(List.of(agents));
    org.exoplatform.commons.utils.ListAccess<User> members = mock(org.exoplatform.commons.utils.ListAccess.class);
    User lea = user(true);
    when(lea.getUserName()).thenReturn(LOGIN);
    User max = user(true);
    when(max.getUserName()).thenReturn("max");
    when(members.getSize()).thenReturn(2);
    when(members.load(0, 2)).thenReturn(new User[] { lea, max });
    when(userHandler.findUsersByGroupId("/platform/agents")).thenReturn(members);
    when(accountKindService.computeAccountKind(anyString())).thenReturn("agent");
    when(userHandler.findUserByName(LOGIN, UserStatus.ANY)).thenReturn(lea);
    when(userHandler.findUserByName("max", UserStatus.ANY)).thenReturn(max);
    when(matrixService.getMatrixIdForUser(LOGIN)).thenReturn(MATRIX_ID);
    when(matrixService.getMatrixIdForUser("max")).thenReturn(null);

    assertEquals(java.util.Set.of(LOGIN), service.findDesiredLogins());
  }

  private void desired() throws Exception {
    lenient().when(accountKindService.computeAccountKind(LOGIN)).thenReturn("agent");
    User enabled = user(true);
    lenient().when(userHandler.findUserByName(LOGIN, UserStatus.ANY)).thenReturn(enabled);
    lenient().when(matrixService.getMatrixIdForUser(LOGIN)).thenReturn(MATRIX_ID);
  }

  private User user(boolean enabled) {
    User user = mock(User.class);
    lenient().when(user.isEnabled()).thenReturn(enabled);
    return user;
  }

  private MatrixPusher agentPusher(String pushkey, String url) {
    return new MatrixPusher(AGENT_PUSHER_APP_ID, pushkey, "http", null, null, LOGIN, null, url);
  }

  private MatrixPushRule rule(String kind, String id, boolean enabled) {
    return new MatrixPushRule(kind, id, enabled, !id.contains("not_notifying") && !id.contains("suppress") && !id.contains("master"));
  }

  @Test
  void personsListenerTurnQueuesNothing() throws Exception {
    when(accountKindService.computeAccountKind("john")).thenReturn(null);

    service.enqueueIfDesired("john");

    assertTrue(scheduled.isEmpty());
    verifyNoInteractions(matrixHttpClient);
  }

  @Test
  void workersListenerTurnCarriesTheCallingThreadsMatrixId() throws Exception {
    desired();

    service.enqueueIfDesired(LOGIN);

    assertEquals(1, scheduled.size());
    verifyNoInteractions(matrixHttpClient);
    // the turn runs while the calling request has not committed its writes yet;
    // these stubs stay unused: the carried decision and Matrix id stand in for them
    lenient().when(accountKindService.computeAccountKind(LOGIN)).thenReturn(null);
    lenient().when(matrixService.getMatrixIdForUser(LOGIN)).thenReturn(null);
    when(matrixHttpClient.getPushers(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of());
    when(matrixHttpClient.getPushRules(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of());
    service.processPendingTurn(LOGIN);
    verify(matrixService).callAsUserOrThrow(eq(LOGIN), eq(MATRIX_ID), eq(SYNAPSE_TIMEOUT), any());
    verify(matrixHttpClient).setPusher(any(), eq(TOKEN), eq(SYNAPSE_TIMEOUT));
  }

  @Test
  void unreadableListenerDecisionQueuesNothingAndDoesNotThrow() throws Exception {
    when(accountKindService.computeAccountKind(LOGIN)).thenThrow(new IllegalStateException("IDM down"));

    service.enqueueIfDesired(LOGIN);

    assertTrue(scheduled.isEmpty());
  }

  /**
   * The job selects its accounts once, then re-checks each one at its turn:
   * an account disabled after the selection gets no pusher, and a turn whose
   * re-check fails leaves the next account's turn untouched.
   */
  @Test
  void jobRechecksEachSelectedAccountAtItsTurn() throws Exception {
    User lea = user(true);
    when(lea.getUserName()).thenReturn(LOGIN);
    User max = user(true);
    when(max.getUserName()).thenReturn("max");
    User tom = user(true);
    when(tom.getUserName()).thenReturn("tom");
    io.meeds.social.core.identity.plugin.AccountKindPlugin agents = mock(io.meeds.social.core.identity.plugin.AccountKindPlugin.class);
    when(agents.getGroupId()).thenReturn("/platform/agents");
    when(accountKindService.getPlugins()).thenReturn(List.of(agents));
    org.exoplatform.commons.utils.ListAccess<User> members = mock(org.exoplatform.commons.utils.ListAccess.class);
    when(members.getSize()).thenReturn(3);
    when(members.load(0, 3)).thenReturn(new User[] { lea, tom, max });
    when(userHandler.findUsersByGroupId("/platform/agents")).thenReturn(members);
    when(userHandler.findUserByName(LOGIN, UserStatus.ANY)).thenReturn(lea);
    when(userHandler.findUserByName("tom", UserStatus.ANY)).thenReturn(tom);
    when(userHandler.findUserByName("max", UserStatus.ANY)).thenReturn(max);
    when(matrixService.getMatrixIdForUser(anyString())).thenAnswer(invocation -> "@" + invocation.getArgument(0) + ":matrix.exo.tn");
    // tom's re-check at his turn fails; lea is disabled once selected
    when(accountKindService.computeAccountKind(anyString())).thenReturn("agent");
    when(accountKindService.computeAccountKind("tom")).thenReturn("agent").thenThrow(new IllegalStateException("IDM down"));
    when(matrixHttpClient.getPushers(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of());
    when(matrixHttpClient.getPushRules(TOKEN, SYNAPSE_TIMEOUT)).thenReturn(List.of());
    MatrixAgentPusherService advisedEntryPoints = spy(service);
    doAnswer(invocation -> {
      java.util.Set<String> selected = service.findDesiredLogins();
      when(lea.isEnabled()).thenReturn(false);
      return selected;
    }).when(advisedEntryPoints).getDesiredLogins();
    doAnswer(invocation -> {
      service.reconcileTurn(invocation.getArgument(0), null, invocation.getArgument(1));
      return null;
    }).when(advisedEntryPoints).reconcile(anyString(), anyString());
    when(matrixService.isServiceEnabled()).thenReturn(true);
    io.meeds.chat.job.MatrixAgentPusherJob job = new io.meeds.chat.job.MatrixAgentPusherJob();
    org.springframework.test.util.ReflectionTestUtils.setField(job, "matrixService", matrixService);
    org.springframework.test.util.ReflectionTestUtils.setField(job, "agentPusherService", advisedEntryPoints);

    job.reconcileAgentPushers();

    org.mockito.ArgumentCaptor<MatrixPusher> pushers = org.mockito.ArgumentCaptor.forClass(MatrixPusher.class);
    verify(matrixHttpClient).setPusher(pushers.capture(), eq(TOKEN), eq(SYNAPSE_TIMEOUT));
    assertEquals("max", pushers.getValue().profileTag());
    assertEquals(service.getPushkey("max"), pushers.getValue().pushkey());
    verify(advisedEntryPoints).reconcile(LOGIN, GATEWAY);
    verify(advisedEntryPoints).reconcile("tom", GATEWAY);
    verify(advisedEntryPoints).reconcile("max", GATEWAY);
  }
}
