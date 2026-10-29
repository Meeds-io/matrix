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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.annotation.PostConstruct;

import org.exoplatform.container.ExoContainer;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.organization.Group;
import org.exoplatform.services.organization.Membership;
import org.exoplatform.services.organization.MembershipHandler;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.impl.MembershipUpdateListener;

import io.meeds.chat.MatrixBaseTest;

class MatrixAgentMembershipListenerTest extends MatrixBaseTest {

  private static final String           LOGIN    = "raul";

  @Autowired
  private OrganizationService           organizationService;

  @Autowired
  private MatrixAgentMembershipListener membershipListener;

  private final ExecutorService         otherThread = Executors.newSingleThreadExecutor();

  @AfterEach
  void stopOtherThread() {
    otherThread.shutdownNow();
  }

  /**
   * The order the delete needs: core's listener, which commits the delete,
   * comes before matrix's in the membership handler's listeners, because
   * matrix registers from a Spring bean's {@code @PostConstruct}, after every
   * kernel plugin.
   */
  @Test
  void registeredAfterCoresMembershipUpdateListener() throws Exception {
    List<Object> listeners = membershipListeners(organizationService.getMembershipHandler());
    assertNotNull(listeners);
    int coreIndex = indexOf(listeners, MembershipUpdateListener.class);
    int matrixIndex = listeners.indexOf(membershipListener);
    assertTrue(coreIndex >= 0, "core's MembershipUpdateListener is registered in the test container: " + listeners);
    assertTrue(matrixIndex > coreIndex, "matrix's listener runs after core's: " + listeners);
    assertTrue(MatrixAgentMembershipListener.class.getMethod("init").isAnnotationPresent(PostConstruct.class),
               "registration is the Spring lifecycle's: without @PostConstruct the listener is never added");
  }

  /**
   * When matrix's {@code postDelete} hands the delete to the pusher service,
   * another thread, the one a left-kind turn runs on, sees the membership gone; before the delete, the same read
   * from that thread sees it, so the probe can tell. The test container's
   * organization service is portal's in-memory one, which has no transaction:
   * here this pins the probe and the listener's queuing, not the commit order,
   * which {@link #registeredAfterCoresMembershipUpdateListener()} pins through
   * the listeners' order and the live check against the IDM store confirms.
   */
  @Test
  void leavingTheKindIsCommittedWhenMatrixsListenerRuns() throws Exception {
    MembershipHandler membershipHandler = organizationService.getMembershipHandler();
    Group group = workersGroup();
    User user = organizationService.getUserHandler().findUserByName(LOGIN);
    membershipHandler.linkMembership(user,
                                     group,
                                     organizationService.getMembershipTypeHandler().findMembershipType("member"),
                                     true);
    ExoContainer container = ExoContainerContext.getCurrentContainer();
    assertFalse(readFromOtherThread(container).isEmpty(), "the probe sees a committed membership from another thread");

    AtomicReference<Collection<Membership>> seenByTheTurnThread = new AtomicReference<>();
    doAnswer(invocation -> {
      seenByTheTurnThread.set(readFromOtherThread(container));
      return null;
    }).when(agentPusherService).onMembershipDeleted(LOGIN, TestWorkerKindPlugin.GROUP_ID);
    clearInvocations(agentPusherService);

    Membership membership = membershipHandler.findMembershipByUserGroupAndType(LOGIN, TestWorkerKindPlugin.GROUP_ID, "member");
    membershipHandler.removeMembership(membership.getId(), true);

    verify(agentPusherService).onMembershipDeleted(LOGIN, TestWorkerKindPlugin.GROUP_ID);
    assertNotNull(seenByTheTurnThread.get());
    assertTrue(seenByTheTurnThread.get().isEmpty(), "matrix's listener runs after the delete is committed");
  }

  /**
   * The listener decides nothing: every deleted membership goes to the pusher
   * service, whose decision is pinned in {@code MatrixAgentPusherServiceTest}.
   */
  @Test
  void everyDeletedMembershipIsHandedToThePusherService() {
    clearInvocations(agentPusherService);
    org.exoplatform.services.organization.impl.MembershipImpl membership =
                                                                  new org.exoplatform.services.organization.impl.MembershipImpl();
    membership.setUserName(LOGIN);
    membership.setGroupId("/platform/users");

    membershipListener.postDelete(membership);

    verify(agentPusherService).onMembershipDeleted(LOGIN, "/platform/users");
    verify(agentPusherService, never()).enqueueLeftKind(anyString());
  }

  private Group workersGroup() throws Exception {
    Group group = organizationService.getGroupHandler().findGroupById(TestWorkerKindPlugin.GROUP_ID);
    if (group == null) {
      group = organizationService.getGroupHandler().createGroupInstance();
      group.setGroupName(TestWorkerKindPlugin.GROUP_ID.substring(1));
      group.setLabel("Matrix test workers");
      organizationService.getGroupHandler().addChild(null, group, true);
      group = organizationService.getGroupHandler().findGroupById(TestWorkerKindPlugin.GROUP_ID);
    }
    assertEquals(TestWorkerKindPlugin.GROUP_ID, group.getId());
    return group;
  }

  private Collection<Membership> readFromOtherThread(ExoContainer container) throws Exception {
    return otherThread.submit(() -> {
      ExoContainerContext.setCurrentContainer(container);
      RequestLifeCycle.begin(container);
      try {
        return organizationService.getMembershipHandler().findMembershipsByUserAndGroup(LOGIN, TestWorkerKindPlugin.GROUP_ID);
      } finally {
        RequestLifeCycle.end();
      }
    }).get();
  }

  /**
   * Reads the membership handler's listeners, in the order it calls them:
   * {@code listeners_} on portal's IDM handler, {@code membershiptListeners}
   * on its in-memory test handler.
   *
   * @param handler the membership handler
   * @return its listeners
   */
  @SuppressWarnings("unchecked")
  private List<Object> membershipListeners(MembershipHandler handler) {
    for (String field : List.of("membershiptListeners", "listeners_")) {
      try {
        return (List<Object>) ReflectionTestUtils.getField(handler, field);
      } catch (IllegalArgumentException e) {
        // not this implementation's field
      }
    }
    throw new IllegalStateException("Unknown membership handler " + handler.getClass());
  }

  private int indexOf(List<Object> listeners, Class<?> type) {
    for (int i = 0; i < listeners.size(); i++) {
      if (type.isInstance(listeners.get(i))) {
        return i;
      }
    }
    return -1;
  }
}
