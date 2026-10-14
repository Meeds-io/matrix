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
package io.meeds.chat.job;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_AGENTS_PUSH_GATEWAY_URL;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

import io.meeds.chat.service.MatrixAgentPusherService;
import io.meeds.chat.service.MatrixService;

/**
 * Reconciles, periodically, the chat pusher and push rules of every account
 * of a declared kind (a digital worker), so that a pusher Synapse deleted, a
 * drifted rule or an account the listeners missed is repaired within one
 * period. The schedule is {@code meeds.matrix.agents.pusher.cron}, every 15
 * minutes by default, read at boot. Each node runs it; a reconcile is
 * idempotent across nodes.
 * <p>
 * It opens no transaction itself: listing the accounts and reconciling each
 * one are separate transactions of the service, so one account's turn never
 * holds a session through another's Synapse calls.
 */
@Component
@EnableScheduling
public class MatrixAgentPusherJob {

  private static final Log         LOG = ExoLogger.getLogger(MatrixAgentPusherJob.class);

  @Autowired
  private MatrixService            matrixService;

  @Autowired
  private MatrixAgentPusherService agentPusherService;

  /**
   * Reconciles every account that wants a pusher, one after the other.
   */
  @Scheduled(cron = "${meeds.matrix.agents.pusher.cron:0 0/15 * * * *}")
  public void reconcileAgentPushers() {
    if (!matrixService.isServiceEnabled()) {
      return;
    }
    String gatewayUrl = agentPusherService.resolveGatewayUrl();
    if (gatewayUrl == null) {
      LOG.error("The push gateway url of the digital workers ({}) is invalid: no chat pusher is registered, existing ones are kept",
                PropertyManager.getProperty(MATRIX_AGENTS_PUSH_GATEWAY_URL));
      return;
    }
    for (String login : agentPusherService.getDesiredLogins()) {
      agentPusherService.reconcile(login, gatewayUrl);
    }
  }
}
