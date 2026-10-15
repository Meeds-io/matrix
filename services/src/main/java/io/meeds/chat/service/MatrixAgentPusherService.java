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

import static io.meeds.chat.service.utils.MatrixConstants.AGENT_PUSHER_APP_ID;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_AGENTS_PUSH_GATEWAY_URL;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_JWT_SECRET;
import static io.meeds.chat.service.utils.MatrixConstants.PUSH_GATEWAY_PATH;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserStatus;

import io.meeds.chat.model.AgentPusherTurn;
import io.meeds.chat.model.MatrixPushRule;
import io.meeds.chat.model.MatrixPusher;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.common.ContainerTransactional;
import io.meeds.social.core.identity.plugin.AccountKindPlugin;
import io.meeds.social.core.identity.service.AccountKindService;

import jakarta.annotation.PreDestroy;

/**
 * Registers, as the account itself, a notify-only Matrix pusher for every
 * account of a kind an add-on declares (a digital worker), and keeps that
 * account's push rules down to the mentions of the account, so that Synapse
 * pushes to the platform the mentions of a worker that never opens a
 * browser. The server defaults it leaves alone still notify an invitation
 * ({@code .m.rule.invite_for_me}) and a room upgrade
 * ({@code .m.rule.tombstone}); the push gateway drops both, since neither is
 * a message.
 * <p>
 * The pusher's key is derived from the platform's Matrix JWT secret and the
 * login, never stored, and only lets the push gateway recognise the account
 * it is pushed for. Each account is reconciled by {@link #reconcile}: the
 * scheduled job does it for every such account, and the platform listeners
 * queue a turn on one single thread, so a user save or a space join never
 * waits on Synapse.
 */
@Service
public class MatrixAgentPusherService {

  /** Request timeout of every Synapse call of a reconcile turn, the login included. */
  public static final Duration      SYNAPSE_TIMEOUT               = Duration.ofSeconds(10);

  /** How long the decision of the thread that queued a turn stands in for a re-read. */
  public static final Duration      CARRIED_DECISION_VALIDITY     = Duration.ofMinutes(5);

  /** Most extra pushers deleted in one turn; the rest go at the next one. */
  public static final int           MAX_PUSHER_DELETES_PER_TURN   = 10;

  /** Most push rules changed in one turn; the rest go at the next one. */
  public static final int           MAX_RULE_UPDATES_PER_TURN     = 10;

  /** Most queued turns of accounts wanting a pusher; the scheduled job catches up the others. */
  public static final int           MAX_PENDING_DESIRED_TURNS     = 1000;

  /**
   * The server-default overrides and content rules that notify on a message
   * not mentioning the account through {@code m.mentions}: the legacy name
   * matches and the room mentions.
   */
  public static final Set<String>   MANAGED_OVERRIDE_RULES        = Set.of(".m.rule.contains_display_name",
                                                                           ".m.rule.is_room_mention",
                                                                           ".m.rule.roomnotif");

  /** The legacy content rule matching the account's user name in a message body. */
  public static final String        MANAGED_CONTENT_RULE          = ".m.rule.contains_user_name";

  /** The rules never touched: the mention that triggers a worker, and room invitations. */
  public static final Set<String>   UNMANAGED_RULES               = Set.of(".m.rule.is_user_mention", ".m.rule.invite_for_me");

  private static final Log          LOG                           = ExoLogger.getLogger(MatrixAgentPusherService.class);

  private static final String       PUSHKEY_DERIVATION_LABEL      = "exo.matrix.agent.pusher";

  private static final String       HMAC_ALGORITHM                = "HmacSHA256";

  private static final int          LOCK_STRIPES                  = 64;

  private static final int          USERS_PAGE_SIZE               = 100;

  /** Runs the queued turns one after the other; replaced by tests. */
  Executor                          executor;

  /** The clock the carried decision is dated with; replaced by tests. */
  Clock                             clock                         = Clock.systemUTC();

  private final ReentrantLock[]     locks                         = new ReentrantLock[LOCK_STRIPES];

  private final Map<String, AgentPusherTurn> pendingTurns         = new ConcurrentHashMap<>();

  private MatrixService             matrixService;

  private MatrixHttpClient          matrixHttpClient;

  private AccountKindService        accountKindService;

  private OrganizationService       organizationService;

  /**
   * Creates the service.
   *
   * @param matrixService the Matrix service, which mints the accounts' tokens
   * @param matrixHttpClient the Synapse client
   * @param accountKindService the account kinds the add-ons declare
   * @param organizationService the organization service
   */
  @Autowired
  public MatrixAgentPusherService(MatrixService matrixService,
                                  MatrixHttpClient matrixHttpClient,
                                  AccountKindService accountKindService,
                                  OrganizationService organizationService) {
    this.matrixService = matrixService;
    this.matrixHttpClient = matrixHttpClient;
    this.accountKindService = accountKindService;
    this.organizationService = organizationService;
    for (int i = 0; i < LOCK_STRIPES; i++) {
      locks[i] = new ReentrantLock();
    }
    this.executor = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "matrix-agent-pusher");
      thread.setDaemon(true);
      return thread;
    });
  }

  /**
   * Stops the turn thread with the context.
   */
  @PreDestroy
  public void shutdown() {
    if (executor instanceof java.util.concurrent.ExecutorService executorService) {
      executorService.shutdownNow();
    }
  }

  /**
   * Tells whether an account wants a pusher: it is enabled, has a Matrix
   * account, and belongs to the group of an account kind an add-on declares.
   * The chat add-on never names a kind.
   *
   * @param login the platform login
   * @return true when the account wants a pusher
   * @throws Exception when the user or its memberships cannot be read
   */
  public boolean isDesired(String login) throws Exception { // NOSONAR
    if (StringUtils.isBlank(login) || accountKindService.computeAccountKind(login) == null) {
      return false;
    }
    User user = organizationService.getUserHandler().findUserByName(login, UserStatus.ANY);
    return user != null && user.isEnabled() && StringUtils.isNotBlank(matrixService.getMatrixIdForUser(login));
  }

  /**
   * Lists the accounts that want a pusher, over the groups of every account
   * kind an add-on declares, in one transaction of its own.
   *
   * @return the logins, never null
   */
  @ContainerTransactional
  public Set<String> getDesiredLogins() {
    return findDesiredLogins();
  }

  /**
   * Reconciles one account in a transaction of its own: what the scheduled
   * job calls for each account.
   *
   * @param login the platform login
   * @param gatewayUrl the push gateway url, null when the configured one is
   *          invalid: nothing is then registered and existing pushers stay
   */
  @ContainerTransactional
  public void reconcile(String login, String gatewayUrl) {
    reconcileTurn(login, null, gatewayUrl);
  }

  /**
   * What a platform listener calls once its own work is done: decides, on the
   * calling thread, whether the account wants a pusher, and queues a turn
   * carrying that decision and the account's Matrix id when it does. Nothing
   * is queued for an account of no declared kind, and no Synapse call is made
   * on the calling thread.
   *
   * @param login the platform login
   */
  public void enqueueIfDesired(String login) {
    try {
      if (isDesired(login)) {
        enqueueDesired(login, matrixService.getMatrixIdForUser(login));
      }
    } catch (Exception e) {
      LOG.warn("Could not tell whether the account {} wants a chat pusher, left to the scheduled job", login, e);
    }
  }

  /**
   * Queues a turn for an account the calling thread found wanting a pusher,
   * after the listener's own work: the turn then uses that decision instead of
   * re-reading what the calling request may not have committed yet, for
   * {@link #CARRIED_DECISION_VALIDITY}. A pending turn of the same account is
   * replaced, unless it is a left-kind turn. Over
   * {@link #MAX_PENDING_DESIRED_TURNS} pending turns, the turn is dropped and
   * left to the scheduled job.
   *
   * @param login the platform login
   * @param matrixId the account's Matrix id as the calling thread read it
   */
  public void enqueueDesired(String login, String matrixId) {
    enqueue(login, AgentPusherTurn.desired(clock.instant(), matrixId));
  }

  /**
   * Queues a turn for an account that left an account kind's group. It
   * replaces any pending turn of that account and is never dropped; it
   * re-reads whether the account still wants a pusher and, when not,
   * re-enables its managed push rules.
   *
   * @param login the platform login
   */
  public void enqueueLeftKind(String login) {
    enqueue(login, AgentPusherTurn.leftKindTurn());
  }

  /**
   * Runs the pending turn of an account, in a transaction of its own, on the
   * turn thread.
   *
   * @param login the platform login
   */
  @ContainerTransactional
  public void runPendingTurn(String login) {
    processPendingTurn(login);
  }

  /**
   * Resolves the push gateway url: the configured one, else the platform base
   * url followed by {@link io.meeds.chat.service.utils.MatrixConstants#PUSH_GATEWAY_PATH}.
   * It must be an absolute http or https url whose raw path, as Synapse
   * compares it, is exactly that one, the only path Synapse accepts.
   *
   * @return the url, null when it is invalid
   */
  public String resolveGatewayUrl() {
    String configured = PropertyManager.getProperty(MATRIX_AGENTS_PUSH_GATEWAY_URL);
    String url = StringUtils.isBlank(configured) ? getPlatformBaseUrl() + PUSH_GATEWAY_PATH : configured.trim();
    try {
      URI uri = new URI(url);
      boolean valid = ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
          && StringUtils.isNotBlank(uri.getHost()) && PUSH_GATEWAY_PATH.equals(uri.getRawPath()) && uri.getQuery() == null
          && uri.getFragment() == null;
      return valid ? url : null;
    } catch (URISyntaxException e) {
      return null;
    }
  }

  /**
   * Derives the key of an account's pusher:
   * {@code b64url(login) "." b64url(HMAC-SHA256(K, label ":" login))}, where
   * {@code K} is derived from the platform's Matrix JWT secret. Rotating that
   * secret invalidates every worker pusher; the next turn registers new ones.
   *
   * @param login the platform login
   * @return the pushkey
   */
  public String getPushkey(String login) {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    return encoder.encodeToString(login.getBytes(StandardCharsets.UTF_8)) + "." + encoder.encodeToString(mac(login));
  }

  /**
   * Gives the login a pushkey was derived for, after checking its MAC in
   * constant time.
   *
   * @param pushkey the pushkey Synapse pushed with
   * @return the login, null when the key is malformed or its MAC does not
   *         match
   */
  public String getLoginOfPushkey(String pushkey) {
    if (StringUtils.isBlank(pushkey) || StringUtils.countMatches(pushkey, '.') != 1) {
      return null;
    }
    try {
      Base64.Decoder decoder = Base64.getUrlDecoder();
      String login = new String(decoder.decode(StringUtils.substringBefore(pushkey, ".")), StandardCharsets.UTF_8);
      byte[] receivedMac = decoder.decode(StringUtils.substringAfter(pushkey, "."));
      return StringUtils.isNotBlank(login) && MessageDigest.isEqual(mac(login), receivedMac) ? login : null;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /**
   * Tells whether a push rule belongs to the set this service manages on a
   * worker's account: every server-default underride rule that notifies, the
   * legacy name-match and room-mention overrides, and the legacy user-name
   * content rule. The trigger, {@code .m.rule.is_user_mention}, the
   * invitation rule and any rule the account defined are never managed.
   *
   * @param rule the rule
   * @return true when the rule is managed
   */
  public static boolean isManagedRule(MatrixPushRule rule) {
    if (!rule.isServerDefault() || UNMANAGED_RULES.contains(rule.ruleId())) {
      return false;
    }
    return switch (rule.kind()) {
    case "underride" -> rule.notifying();
    case "override" -> MANAGED_OVERRIDE_RULES.contains(rule.ruleId());
    case "content" -> MANAGED_CONTENT_RULE.equals(rule.ruleId());
    default -> false;
    };
  }

  /**
   * Lists the accounts that want a pusher. Un-advised seam of
   * {@link #getDesiredLogins()}.
   *
   * @return the logins, never null
   */
  Set<String> findDesiredLogins() {
    Set<String> logins = new LinkedHashSet<>();
    for (AccountKindPlugin plugin : accountKindService.getPlugins()) {
      try {
        ListAccess<User> users = organizationService.getUserHandler().findUsersByGroupId(plugin.getGroupId());
        int size = users.getSize();
        for (int offset = 0; offset < size; offset += USERS_PAGE_SIZE) {
          for (User user : users.load(offset, Math.min(USERS_PAGE_SIZE, size - offset))) {
            if (user != null && isDesiredQuietly(user.getUserName())) {
              logins.add(user.getUserName());
            }
          }
        }
      } catch (Exception e) {
        LOG.warn("Could not list the accounts of the group {} of the account kind {}", plugin.getGroupId(), plugin.getKind(), e);
      }
    }
    return logins;
  }

  /**
   * Runs the pending turn of an account. Un-advised seam of
   * {@link #runPendingTurn(String)}.
   *
   * @param login the platform login
   */
  void processPendingTurn(String login) {
    AgentPusherTurn turn = pendingTurns.remove(login);
    if (turn == null) {
      return;
    }
    String gatewayUrl = resolveGatewayUrl();
    if (gatewayUrl == null) {
      LOG.error("The push gateway url of the digital workers ({}) is invalid, no pusher is registered for {}",
                PropertyManager.getProperty(MATRIX_AGENTS_PUSH_GATEWAY_URL),
                login);
    }
    reconcileTurn(login, turn, gatewayUrl);
  }

  /**
   * Reconciles one account's pusher and push rules, under the account's lock
   * on this node:
   * <ol>
   * <li>whether the account wants a pusher: the turn's carried decision when
   * it is recent enough, else a re-read. When it does not, nothing is deleted
   * (the gateway rejects that key at its first push, and Synapse then deletes
   * the pusher) and the managed rules are re-enabled;</li>
   * <li>the account's pushers are read; a failed read ends the turn;</li>
   * <li>the expected pusher is set unless one with the same key and gateway
   * url exists;</li>
   * <li>only then, every other pusher of the worker application is deleted, at
   * most {@link #MAX_PUSHER_DELETES_PER_TURN};</li>
   * <li>every enabled managed rule is disabled, at most
   * {@link #MAX_RULE_UPDATES_PER_TURN}.</li>
   * </ol>
   * Every failure is logged and leaves the rest to the next turn.
   * <p>
   * The re-read of step 1 leaves a window: an account disabled or removed
   * from its kind's group after it, during the turn's Synapse calls, still
   * gets its pusher set and its rules disabled in this turn. Its first push is
   * then rejected by the gateway, which no longer finds its kind or Matrix
   * account, and a disabled account's pushers go with its deactivation; the
   * left-kind turn queued by its removal restores its rules.
   *
   * @param login the platform login
   * @param turn the queued turn, null for a scheduled one
   * @param gatewayUrl the push gateway url, null to register nothing
   */
  void reconcileTurn(String login, AgentPusherTurn turn, String gatewayUrl) {
    ReentrantLock lock = locks[Math.floorMod(login.hashCode(), LOCK_STRIPES)];
    lock.lock();
    try {
      boolean desired;
      try {
        desired = isCarriedDecisionValid(turn) || isDesired(login);
      } catch (Exception e) {
        LOG.warn("Could not tell whether the account {} wants a chat pusher, left untouched", login, e);
        return;
      }
      if (!desired) {
        restoreManagedRules(login);
        return;
      }
      if (gatewayUrl == null) {
        return;
      }
      String matrixId = isCarriedDecisionValid(turn) ? turn.matrixId() : null;
      matrixService.callAsUserOrThrow(login, matrixId, SYNAPSE_TIMEOUT, accessToken -> {
        reconcilePushers(login, gatewayUrl, accessToken);
        updateManagedRules(login, false, accessToken);
        return null;
      });
    } catch (Exception e) {
      LOG.warn("Could not reconcile the chat pusher of the account {}, retried at the next turn", login, e);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Returns the platform base url the default push gateway url starts with.
   *
   * @return the base url, without a trailing slash
   */
  String getPlatformBaseUrl() {
    return StringUtils.removeEnd(CommonsUtils.getCurrentDomain(), "/");
  }

  /**
   * Queues a turn and, when the account had none pending, schedules it.
   *
   * @param login the platform login
   * @param turn the turn
   */
  private void enqueue(String login, AgentPusherTurn turn) {
    if (!turn.leftKind() && !pendingTurns.containsKey(login)
        && pendingTurns.values().stream().filter(pending -> !pending.leftKind()).count() >= MAX_PENDING_DESIRED_TURNS) {
      LOG.warn("Too many chat pusher turns pending, the turn of {} is left to the scheduled job", login);
      return;
    }
    boolean[] added = new boolean[1];
    pendingTurns.compute(login, (key, pending) -> {
      if (pending == null) {
        added[0] = true;
        return turn;
      }
      return pending.leftKind() && !turn.leftKind() ? pending : turn;
    });
    if (added[0]) {
      executor.execute(() -> runPendingTurn(login));
    }
  }

  /**
   * Tells whether a turn carries a decision recent enough to stand in for a
   * re-read.
   *
   * @param turn the turn, null for a scheduled one
   * @return true when the carried decision stands
   */
  private boolean isCarriedDecisionValid(AgentPusherTurn turn) {
    return turn != null && turn.decidedAt() != null
        && Duration.between(turn.decidedAt(), clock.instant()).compareTo(CARRIED_DECISION_VALIDITY) < 0;
  }

  /**
   * Tells whether an account wants a pusher, false when that cannot be read.
   *
   * @param login the platform login
   * @return true when the account wants a pusher
   */
  private boolean isDesiredQuietly(String login) {
    try {
      return isDesired(login);
    } catch (Exception e) {
      LOG.warn("Could not tell whether the account {} wants a chat pusher", login, e);
      return false;
    }
  }

  /**
   * Sets the expected pusher unless it exists, then deletes the account's
   * other worker pushers.
   *
   * @param login the platform login
   * @param gatewayUrl the push gateway url
   * @param accessToken the account's access token
   * @throws Exception when a Synapse call fails: nothing is deleted after a
   *           failed read or set
   */
  private void reconcilePushers(String login, String gatewayUrl, String accessToken) throws Exception { // NOSONAR
    String pushkey = getPushkey(login);
    List<MatrixPusher> pushers = matrixHttpClient.getPushers(accessToken, SYNAPSE_TIMEOUT);
    boolean present = pushers.stream()
                             .anyMatch(pusher -> AGENT_PUSHER_APP_ID.equals(pusher.appId()) && pushkey.equals(pusher.pushkey())
                                 && gatewayUrl.equals(pusher.url()));
    if (!present) {
      matrixHttpClient.setPusher(new MatrixPusher(AGENT_PUSHER_APP_ID,
                                                  pushkey,
                                                  "http",
                                                  "eXo digital worker",
                                                  "Server",
                                                  login,
                                                  "en",
                                                  gatewayUrl),
                                 accessToken,
                                 SYNAPSE_TIMEOUT);
      LOG.info("Chat pusher registered for the account {}", login);
    }
    List<MatrixPusher> extras = pushers.stream()
                                       .filter(pusher -> AGENT_PUSHER_APP_ID.equals(pusher.appId())
                                           && !pushkey.equals(pusher.pushkey()))
                                       .toList();
    for (MatrixPusher extra : extras.subList(0, Math.min(extras.size(), MAX_PUSHER_DELETES_PER_TURN))) {
      matrixHttpClient.deletePusher(AGENT_PUSHER_APP_ID, extra.pushkey(), accessToken, SYNAPSE_TIMEOUT);
    }
    if (extras.size() > MAX_PUSHER_DELETES_PER_TURN) {
      LOG.info("{} more stale chat pushers of the account {} are deleted at the next turn",
               extras.size() - MAX_PUSHER_DELETES_PER_TURN,
               login);
    }
  }

  /**
   * Sets the managed push rules of an account found in the other state.
   *
   * @param login the platform login
   * @param enabled the state to set
   * @param accessToken the account's access token
   * @throws Exception when a Synapse call fails
   */
  private void updateManagedRules(String login, boolean enabled, String accessToken) throws Exception { // NOSONAR
    List<MatrixPushRule> toUpdate = matrixHttpClient.getPushRules(accessToken, SYNAPSE_TIMEOUT)
                                                    .stream()
                                                    .filter(rule -> isManagedRule(rule) && rule.enabled() != enabled)
                                                    .toList();
    for (MatrixPushRule rule : toUpdate.subList(0, Math.min(toUpdate.size(), MAX_RULE_UPDATES_PER_TURN))) {
      matrixHttpClient.setPushRuleEnabled(rule.kind(), rule.ruleId(), enabled, accessToken, SYNAPSE_TIMEOUT);
    }
    if (toUpdate.size() > MAX_RULE_UPDATES_PER_TURN) {
      LOG.info("{} more push rules of the account {} are {} at the next turn",
               toUpdate.size() - MAX_RULE_UPDATES_PER_TURN,
               login,
               enabled ? "enabled" : "disabled");
    }
  }

  /**
   * Re-enables the managed push rules of an account that no longer wants a
   * pusher, best effort: only while it keeps a Matrix account and is enabled,
   * since a disabled account is deactivated in Synapse.
   *
   * @param login the platform login
   */
  private void restoreManagedRules(String login) {
    try {
      User user = organizationService.getUserHandler().findUserByName(login, UserStatus.ANY);
      if (user == null || !user.isEnabled() || StringUtils.isBlank(matrixService.getMatrixIdForUser(login))) {
        return;
      }
      matrixService.callAsUserOrThrow(login, null, SYNAPSE_TIMEOUT, accessToken -> {
        updateManagedRules(login, true, accessToken);
        return null;
      });
    } catch (Exception e) {
      LOG.warn("Could not re-enable the push rules of the account {}", login, e);
    }
  }

  /**
   * Computes the MAC of a login with the key derived from the platform's
   * Matrix JWT secret.
   *
   * @param login the platform login
   * @return the MAC
   */
  private byte[] mac(String login) {
    try {
      Mac keyMac = Mac.getInstance(HMAC_ALGORITHM);
      keyMac.init(new SecretKeySpec(PropertyManager.getProperty(MATRIX_JWT_SECRET).getBytes(StandardCharsets.UTF_8),
                                    HMAC_ALGORITHM));
      byte[] key = keyMac.doFinal(PUSHKEY_DERIVATION_LABEL.getBytes(StandardCharsets.UTF_8));
      Mac loginMac = Mac.getInstance(HMAC_ALGORITHM);
      loginMac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
      return loginMac.doFinal((PUSHKEY_DERIVATION_LABEL + ":" + login).getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HMAC-SHA256 is not available", e);
    }
  }
}
