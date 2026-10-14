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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import io.meeds.chat.service.MatrixAgentPusherService;
import io.meeds.chat.service.MatrixService;
import io.meeds.common.ContainerTransactional;

@ExtendWith(MockitoExtension.class)
class MatrixAgentPusherJobTest {

  private static final String      GATEWAY = "https://acme.com/_matrix/push/v1/notify";

  @Mock
  private MatrixService            matrixService;

  @Mock
  private MatrixAgentPusherService agentPusherService;

  @InjectMocks
  private MatrixAgentPusherJob     job;

  @Test
  void reconcilesEveryDesiredAccountWithTheGatewayUrl() {
    when(matrixService.isServiceEnabled()).thenReturn(true);
    when(agentPusherService.resolveGatewayUrl()).thenReturn(GATEWAY);
    when(agentPusherService.getDesiredLogins()).thenReturn(new LinkedHashSet<>(List.of("lea", "max")));

    job.reconcileAgentPushers();

    InOrder order = Mockito.inOrder(agentPusherService);
    order.verify(agentPusherService).reconcile("lea", GATEWAY);
    order.verify(agentPusherService).reconcile("max", GATEWAY);
  }

  @Test
  void invalidGatewayUrlReconcilesNothing() {
    when(matrixService.isServiceEnabled()).thenReturn(true);
    when(agentPusherService.resolveGatewayUrl()).thenReturn(null);

    job.reconcileAgentPushers();

    verify(agentPusherService, never()).getDesiredLogins();
    verify(agentPusherService, never()).reconcile(anyString(), anyString());
  }

  @Test
  void disabledChatReconcilesNothing() {
    when(matrixService.isServiceEnabled()).thenReturn(false);

    job.reconcileAgentPushers();

    verify(agentPusherService, never()).getDesiredLogins();
  }

  /**
   * Pins the wiring the framework does: removing {@code @Scheduled} or
   * {@code @EnableScheduling} leaves the job never run, a mutant no call by
   * hand can kill. The job opens no transaction of its own: the service's
   * listing and each reconcile are transactions of their own.
   */
  @Test
  void isScheduledEveryFifteenMinutesWithoutATransactionOfItsOwn() throws Exception {
    Method method = MatrixAgentPusherJob.class.getMethod("reconcileAgentPushers");
    assertTrue(MatrixAgentPusherJob.class.isAnnotationPresent(EnableScheduling.class));
    assertTrue(method.isAnnotationPresent(Scheduled.class));
    assertEquals("${meeds.matrix.agents.pusher.cron:0 0/15 * * * *}", method.getAnnotation(Scheduled.class).cron());
    assertFalse(method.isAnnotationPresent(ContainerTransactional.class));
    assertTrue(MatrixAgentPusherService.class.getMethod("reconcile", String.class, String.class)
                                             .isAnnotationPresent(ContainerTransactional.class));
    assertTrue(MatrixAgentPusherService.class.getMethod("getDesiredLogins").isAnnotationPresent(ContainerTransactional.class));
    assertTrue(MatrixAgentPusherService.class.getMethod("runPendingTurn", String.class)
                                             .isAnnotationPresent(ContainerTransactional.class));
  }
}
