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

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;
import org.exoplatform.social.core.profileproperty.model.ProfilePropertySetting;

/**
 * Upgrade plugin to remove the profile property matrixId
 */
public class RemoveProfilePropertyUpgradePlugin extends UpgradeProductPlugin {
  private static final Log       LOG = ExoLogger.getExoLogger(RemoveProfilePropertyUpgradePlugin.class);

  private final ProfilePropertyService profilePropertyService;

  public RemoveProfilePropertyUpgradePlugin(SettingService settingService,
                                            InitParams initParams,
                                            ProfilePropertyService profilePropertyService) {
    super(settingService, initParams);
    this.profilePropertyService = profilePropertyService;
  }

  @Override
  public void processUpgrade(String s, String s1) {
    LOG.info("Start:: Remove the profile property MatrixId");
    ProfilePropertySetting profilePropertySetting = this.profilePropertyService.getProfileSettingByName("matrixId");
    if(profilePropertySetting != null) {
      this.profilePropertyService.deleteProfilePropertySetting(profilePropertySetting.getId());
      LOG.info("Summary :: removed successfully the profile property matrixId !");
    }
  }
}
