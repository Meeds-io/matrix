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
package io.meeds.chat.listeners;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.security.ConversationRegistry;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.services.security.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_ADMIN_USERNAME;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_RESTRICTED_USERS_GROUP;

@Component
public class MatrixUserLoginListener extends Listener<ConversationRegistry, ConversationState> {

  private static final Log LOG = ExoLogger.getLogger(MatrixUserLoginListener.class);

  @Autowired
  private IdentityManager  identityManager;

  @Autowired
  private MatrixService    matrixService;

  @Autowired
  private ListenerService  listenerService;

  @Autowired
  private UserACL          userACL;

  @PostConstruct
  public void init() {
    listenerService.addListener("exo.core.security.ConversationRegistry.register", this);
  }

  public void onEvent(Event<ConversationRegistry, ConversationState> event) {
    if (!matrixService.isServiceEnabled()) {
      return;
    }
    String userId = event.getData().getIdentity().getUserId();
    Identity connectedUserIdentity = event.getData().getIdentity();
    String matrixUserAdmin = PropertyManager.getProperty(MATRIX_ADMIN_USERNAME);
    if (matrixUserAdmin.equals(userId)) {
      return;
    }
    RequestLifeCycle.begin(PortalContainer.getInstance());
    try {
      String[] matrixRestrictedGroups = matrixService.getRestrictedGroups();
      if (matrixRestrictedGroups != null && matrixRestrictedGroups.length > 0
          && !matrixService.isUserMemberOfGroups(connectedUserIdentity.getUserId(), matrixRestrictedGroups)) {
        return;
      }
      String matrixUserId = matrixService.getMatrixIdForUser(userId);
      if (StringUtils.isBlank(matrixUserId)) {
        org.exoplatform.social.core.identity.model.Identity userIdentity = identityManager.getOrCreateUserIdentity(userId);
        matrixService.saveUserAccount(userIdentity, true);
      }
    } catch (Exception e) {
      LOG.error("Could not add matrix information for user {}", userId, e);
    } finally {
      RequestLifeCycle.end();
    }
  }
}
