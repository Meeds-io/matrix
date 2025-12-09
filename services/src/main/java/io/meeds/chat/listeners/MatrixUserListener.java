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

import io.meeds.chat.model.Room;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.service.utils.MatrixConstants;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.UserEventListener;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static io.meeds.chat.service.utils.MatrixConstants.*;

@Component
public class MatrixUserListener extends UserEventListener {

  private static final Log    LOG = ExoLogger.getLogger(MatrixUserListener.class);

  @Autowired
  private IdentityManager     identityManager;

  @Autowired
  private MatrixService       matrixService;

  @Autowired
  private OrganizationService organizationService;

  @Autowired
  private SpaceService        spaceService;

  @PostConstruct
  public void init() {
    this.organizationService.getUserHandler().addUserEventListener(this);
  }

  @Override
  public void postSave(User user, boolean isNew) throws Exception {
    if (!matrixService.isServiceAvailable()) {
      return;
    }
    String matrixUserAdmin = PropertyManager.getProperty(MATRIX_ADMIN_USERNAME);
    String[] matrixRestrictedGroups = matrixService.getRestrictedGroups();
    if ((matrixRestrictedGroups != null && matrixRestrictedGroups.length > 0
        && !this.matrixService.isUserMemberOfGroups(user.getUserName(), matrixRestrictedGroups))
        || matrixUserAdmin.equals(user.getUserName())) {
      return;
    }
    try {
      Identity userIdentity = identityManager.getOrCreateUserIdentity(user.getUserName());
      matrixService.saveUserAccount(userIdentity, isNew);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      LOG.error("Can not create the user {} on Matrix", user.getUserName(), ie);
    } catch (Exception e) {
      LOG.error("Can not create the user {} on Matrix", user.getUserName(), e);
    }
  }

  @Override
  public void postSetEnabled(User user) throws Exception {
    if (!matrixService.isServiceAvailable()) {
      return;
    }
    String matrixUserAdmin = PropertyManager.getProperty(MATRIX_ADMIN_USERNAME);
    if (matrixUserAdmin.equals(user.getUserName())) {
      LOG.warn("Could not set enable the Matrix admin user");
      return;
    }
    Identity userIdentity = identityManager.getOrCreateUserIdentity(user.getUserName());
    if (userIdentity != null) {

      Profile userProfile = identityManager.getProfile(userIdentity);
      String matrixUserId = (String) userProfile.getProperty(USER_MATRIX_ID);
      if (StringUtils.isNotBlank(matrixUserId)) {
        if (!user.isEnabled()) {
          String matrixUsername =
                                "@" + user.getUserName() + ":" + PropertyManager.getProperty(MatrixConstants.MATRIX_SERVER_NAME);
          matrixService.disableAccount(matrixUsername);
        } else {
          try {
            matrixService.saveUserAccount(userIdentity, false, true, user.isEnabled());
            ListAccess<Space> spaces = spaceService.getMemberSpaces(user.getUserName());
            Space[] spacesArray = spaces.load(0, spaces.getSize());
            for (Space space : spacesArray) {
              Room room = matrixService.getRoomBySpace(space);
              if (room != null && StringUtils.isNotBlank(room.getRoomId())) {
                matrixService.joinUserToRoom(room.getRoomId(), matrixUserId);
              }
            }
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOG.error("Can not create the user {} on Matrix", user.getUserName(), ie);
          } catch (Exception e) {
            LOG.error("Can not create the user {} on Matrix", user.getUserName(), e);
          }
        }
      }
    }
  }
}
