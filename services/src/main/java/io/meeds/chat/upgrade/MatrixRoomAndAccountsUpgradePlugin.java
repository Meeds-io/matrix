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
package io.meeds.chat.upgrade;

import io.meeds.chat.service.MatrixService;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.User;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.SpaceFilter;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_RESTRICTED_USERS_GROUP;
import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;

public class MatrixRoomAndAccountsUpgradePlugin extends UpgradeProductPlugin {

  private static final Log    LOG                = ExoLogger.getExoLogger(MatrixRoomAndAccountsUpgradePlugin.class);

  private SpaceService        spaceService;

  private MatrixService       matrixService;

  private IdentityManager     identityManager;

  private OrganizationService organizationService;

  private int                 SPACES_THRESHOLD   = 20;

  private int                 LOADED_USERS_COUNT = 50;

  public MatrixRoomAndAccountsUpgradePlugin(InitParams initParams,
                                            SpaceService spaceService,
                                            MatrixService matrixService,
                                            OrganizationService organizationService,
                                            IdentityManager identityManager) {
    super(initParams);
    this.spaceService = spaceService;
    this.matrixService = matrixService;
    this.identityManager = identityManager;
    this.organizationService = organizationService;
  }

  @Override
  public void processUpgrade(String s, String s1) {
    synchronizeUsers();
    synchronizeSpaces();
  }

  private void synchronizeSpaces() {
    long startupTime = System.currentTimeMillis();

    LOG.info("Start:: create Matrix rooms for spaces");
    int failedToMigrateSpaces = 0;
    int successfullyMigratedSpaces = 0;
    int spacesCount = 0;
    int ignoredSpaces = 0;

    ListAccess<Space> spaces = spaceService.getAllSpacesByFilter(new SpaceFilter());
    try {
      spacesCount = spaces.getSize();
      int loadedSpaces = 0;
      while (loadedSpaces < spacesCount) {
        int actualSpacesToLoadCount =
                                    loadedSpaces + SPACES_THRESHOLD < spacesCount ? SPACES_THRESHOLD : spacesCount - loadedSpaces;
        Space[] spacesToMigrate = spaces.load(loadedSpaces, actualSpacesToLoadCount);
        for (Space space : spacesToMigrate) {
          String roomId = matrixService.getRoomBySpace(space);
          if (StringUtils.isBlank(roomId)) {
            try {
              roomId = this.matrixService.createRoom(space);
              for (String member : space.getMembers()) {
                Identity memberIdentity = identityManager.getOrCreateUserIdentity(member);
                if (memberIdentity != null
                    && StringUtils.isNotBlank((String) memberIdentity.getProfile().getProperty(USER_MATRIX_ID))) {
                  String matrixIdOfUser = (String) memberIdentity.getProfile().getProperty(USER_MATRIX_ID);
                  matrixService.joinUserToRoom(roomId, matrixIdOfUser);
                }
              }
              successfullyMigratedSpaces++;
            } catch (Exception e) {
              LOG.error("Could not create a room for space {}", space.getDisplayName(), e);
              failedToMigrateSpaces++;
            }
          } else {
            ignoredSpaces++;
            LOG.debug("The space {} has already a room with Id {}", space.getDisplayName(), roomId);
          }
          if (StringUtils.isNotBlank(roomId)) {
            matrixService.updateRoomAvatar(space, roomId);
          }
        }
        loadedSpaces += spacesToMigrate.length;
      }
    } catch (Exception e) {
      throw new RuntimeException("Error while retrieving spaces", e);
    }
    LOG.info("Summary :: create Matrix rooms for spaces, {} created rooms for {} spaces, {} ignored spaces, {} rooms failed to be created !",
             successfullyMigratedSpaces,
             spacesCount,
             ignoredSpaces,
             failedToMigrateSpaces);
    if (failedToMigrateSpaces > 0) {
      throw new RuntimeException("Some spaces were not upgraded!");
    }
    LOG.info("End:: create Matrix rooms for spaces in {}", System.currentTimeMillis() - startupTime);
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion, String previousVersion) {
    return this.isEnabled();
  }

  private void synchronizeUsers() {
    LOG.info("Start:: create Matrix accounts for users");
    long startupTime = System.currentTimeMillis();

    int checkedUsers = 0;
    int usersCount = 0;

    ListAccess<User> users = null;
    RequestLifeCycle.begin(ExoContainerContext.getCurrentContainer());
    try {
      if (StringUtils.isNotBlank(PropertyManager.getProperty(MATRIX_RESTRICTED_USERS_GROUP))) {
        users =
              organizationService.getUserHandler().findUsersByGroupId(PropertyManager.getProperty(MATRIX_RESTRICTED_USERS_GROUP));
      } else {
        users = organizationService.getUserHandler().findAllUsers();
      }
      usersCount = users.getSize();
    } catch (Exception e) {
      LOG.error("Error while checking users", e);
    } finally {
      RequestLifeCycle.end();
    }

    if (usersCount == 0) {
      throw new IllegalStateException("No users to migrate, please check the value of the property matrix.restricted.users.groupId or remove it to select all users.");
    }

    RequestLifeCycle.begin(ExoContainerContext.getCurrentContainer());
    try {
      while (checkedUsers < usersCount) {
        int usersToCheck = usersCount > checkedUsers + LOADED_USERS_COUNT ? LOADED_USERS_COUNT : (usersCount - checkedUsers);
        User[] usersArray = users.load(checkedUsers, usersToCheck);
        for (User user : usersArray) {
          Identity userIdentity = identityManager.getOrCreateUserIdentity(user.getUserName());
          Profile userProfile = userIdentity.getProfile();
          String userMatrixId = (String) userProfile.getProperty(USER_MATRIX_ID);
          if (StringUtils.isBlank(userMatrixId)) {
            userMatrixId = matrixService.saveUserAccount(user, true, false);
          }
          if (StringUtils.isNotBlank(userMatrixId)) {
            // Update user avatar
            matrixService.updateUserAvatar(userProfile, userMatrixId);

            // Add user to spaces already sync with Matrix
            ListAccess<Space> userSpaces = spaceService.getMemberSpaces(user.getUserName());
            Space[] spaceArray = userSpaces.load(0, userSpaces.getSize());
            for (Space space : spaceArray) {
              String spaceRoomId = matrixService.getRoomBySpace(space);
              if (StringUtils.isNotBlank(spaceRoomId)) {
                matrixService.joinUserToRoom(spaceRoomId, userMatrixId);
              }
            }
          }
        }
        checkedUsers += usersArray.length;
        LOG.info("Checked Matrix account for {} of {} users", checkedUsers, usersCount);
      }
    } catch (Exception e) {
      throw new RuntimeException("Error while creating accounts for users on Matrix", e);
    } finally {
      RequestLifeCycle.end();
    }
    LOG.info("Summary :: create Matrix accounts for {} users, {} users were checked with their Matrix accounts, {} accounts failed to be created !",
             checkedUsers,
             usersCount,
             usersCount - checkedUsers);
    if (usersCount - checkedUsers > 0) {
      throw new RuntimeException("Some user accounts were not synchronized with Matrix!");
    }
    LOG.info("End:: create Matrix accounts for users took {}", System.currentTimeMillis() - startupTime);
  }
}
