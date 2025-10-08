package io.meeds.chat.upgrade;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;
import org.exoplatform.social.core.profileproperty.model.ProfilePropertySetting;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class RemoveProfilePropertyUpgradePluginTest {

  @Test
  void processUpgrade() {
    ProfilePropertyService profilePropertyService = mock(ProfilePropertyService.class);
    SettingService settingService = mock(SettingService.class);
    ProfilePropertySetting profilePropertySetting = new ProfilePropertySetting();
    profilePropertySetting.setPropertyName("matrixId");
    profilePropertySetting.setId(1L);
    when(profilePropertyService.getProfileSettingByName("matrixId")).thenReturn(profilePropertySetting);
    RemoveProfilePropertyUpgradePlugin removeProfilePropertyUpgradePlugin =
                                                                          new RemoveProfilePropertyUpgradePlugin(settingService,
                                                                                                                 new InitParams(),
                                                                                                                 profilePropertyService);
    removeProfilePropertyUpgradePlugin.processUpgrade("1.0", "2.0");
    verify(profilePropertyService, times(1)).deleteProfilePropertySetting(anyLong());
  }
}
