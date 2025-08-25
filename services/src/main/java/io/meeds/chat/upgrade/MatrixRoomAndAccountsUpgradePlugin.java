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
package io.meeds.chat.upgrade;

import io.meeds.chat.model.Room;
import io.meeds.chat.service.MatrixService;
import io.meeds.chat.service.MatrixSynchronizationService;
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

  private static final Log             LOG = ExoLogger.getExoLogger(MatrixRoomAndAccountsUpgradePlugin.class);

  private MatrixService                matrixService;

  private MatrixSynchronizationService matrixSynchronizationService;

  public MatrixRoomAndAccountsUpgradePlugin(InitParams initParams,
                                            MatrixService matrixService,
                                            MatrixSynchronizationService matrixSynchronizationService) {
    super(initParams);
    this.matrixService = matrixService;
    this.matrixSynchronizationService = matrixSynchronizationService;
  }

  @Override
  public void processUpgrade(String s, String s1) {
    matrixSynchronizationService.synchronizeUsers();
    matrixSynchronizationService.synchronizeSpaces();
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion, String previousVersion) {
    return this.isEnabled() && matrixService.isServiceAvailable();
  }

}
