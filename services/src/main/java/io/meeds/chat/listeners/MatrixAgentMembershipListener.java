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
package io.meeds.chat.listeners;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.MembershipEventListener;
import org.exoplatform.services.organization.OrganizationService;

import io.meeds.chat.service.MatrixAgentPusherService;
import io.meeds.chat.service.MatrixService;
import io.meeds.social.core.identity.service.AccountKindService;

import jakarta.annotation.PostConstruct;

/**
 * Queues a left-kind turn when an account leaves the group of every account
 * kind it belonged to, so that its managed push rules return to the server
 * default once the turn finds it no longer wants a chat pusher.
 * <p>
 * It is registered from {@link #init()}, a Spring bean's
 * {@code @PostConstruct}, and not as a kernel plugin, because it must run
 * after core's {@code MembershipUpdateListener}, which commits the delete
 * ({@code RequestLifeCycle.restartTransaction()}): the kernel adds every
 * plugin of the organization service before it hands the service to Spring,
 * while a kernel plugin of an add-on would be sorted against core's by
 * priority and then by a configuration load order nothing guarantees. Run
 * after that commit, the turn thread's re-read sees the membership gone.
 */
@Component
public class MatrixAgentMembershipListener extends MembershipEventListener {

  private static final Log         LOG = ExoLogger.getLogger(MatrixAgentMembershipListener.class);

  @Autowired
  private OrganizationService      organizationService;

  @Autowired
  private AccountKindService       accountKindService;

  @Autowired
  private MatrixService            matrixService;

  @Autowired
  private MatrixAgentPusherService agentPusherService;

  /**
   * Registers the listener behind every listener the kernel added.
   */
  @PostConstruct
  public void init() {
    organizationService.getMembershipHandler().addMembershipEventListener(this);
  }

  /**
   * Queues a left-kind turn when the deleted membership was in an account
   * kind's group and the account now belongs to no kind's group.
   *
   * @param membership the deleted membership
   */
  @Override
  public void postDelete(Membership membership) {
    if (!matrixService.isServiceEnabled() || accountKindService.getPluginByGroup(membership.getGroupId()) == null) {
      return;
    }
    String login = membership.getUserName();
    try {
      if (accountKindService.computeAccountKind(login) == null) {
        agentPusherService.enqueueLeftKind(login);
      }
    } catch (Exception e) {
      LOG.warn("Could not tell whether the account {} left its account kind, its push rules are left as they are", login, e);
    }
  }
}
