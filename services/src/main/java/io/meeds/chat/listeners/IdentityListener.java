/*
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
import io.meeds.chat.service.MatrixService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.profile.ProfileLifeCycleEvent;
import org.exoplatform.social.core.profile.ProfileListenerPlugin;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;

@Component
public class IdentityListener extends ProfileListenerPlugin {

  private static final Log LOG = ExoLogger.getLogger(IdentityListener.class);

  @Autowired
  private IdentityManager identityManager;

  @Autowired
  private MatrixService   matrixService;

  @PostConstruct
  public void init() {
    this.identityManager.registerProfileListener(this);
  }

  @Override
  public void avatarUpdated(ProfileLifeCycleEvent event) {
    Profile profile = event.getProfile();
    String userMatrixId = (String) profile.getProperty(USER_MATRIX_ID);
    try {
      matrixService.updateUserAvatar(profile, userMatrixId);
    } catch (JsonException | IOException | InterruptedException e) {
      LOG.error("Could not update the avatar of the user {} on Matrix", profile.getFullName(), e);
    }
  }
}
