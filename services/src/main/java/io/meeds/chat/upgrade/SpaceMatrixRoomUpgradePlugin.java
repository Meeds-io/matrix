package io.meeds.chat.upgrade;

import io.meeds.chat.service.MatrixService;
import io.meeds.chat.service.utils.MatrixConstants;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.upgrade.UpgradeProductPlugin;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.SpaceMemberFilterListAccess;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.jpa.storage.dao.SpaceMemberDAO;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.SpaceFilter;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.core.storage.api.IdentityStorage;

public class SpaceMatrixRoomUpgradePlugin extends UpgradeProductPlugin {

  private static final Log LOG              = ExoLogger.getExoLogger(SpaceMatrixRoomUpgradePlugin.class);

  private SpaceService     spaceService;

  private MatrixService    matrixService;

  private IdentityManager  identityManager;

  private int              SPACES_THRESHOLD = 20;

  public SpaceMatrixRoomUpgradePlugin(InitParams initParams, SpaceService spaceService, MatrixService matrixService, IdentityManager identityManager) {
    super(initParams);
    this.spaceService = spaceService;
    this.matrixService = matrixService;
    this.identityManager = identityManager;
  }

  @Override
  public void processUpgrade(String s, String s1) {
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
              roomId = this.matrixService.createMatrixRoomForSpace(space);
              matrixService.createSpaceRoomAssociation(space, roomId);
              for(String member : space.getMembers()) {
                Identity memberIdentity = identityManager.getOrCreateUserIdentity(member);
                if(memberIdentity != null && StringUtils.isNotBlank((String) memberIdentity.getProfile().getProperty(MatrixConstants.USER_MATRIX_ID))) {
                  String matrixIdOfUser = (String) memberIdentity.getProfile().getProperty(MatrixConstants.USER_MATRIX_ID);
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
    if(failedToMigrateSpaces > 0) {
      throw new RuntimeException("Some spaces were not upgraded!");
    }
    LOG.info("End:: create Matrix rooms for spaces in {}", System.currentTimeMillis() - startupTime);
  }

  @Override
  public boolean shouldProceedToUpgrade(String newVersion, String previousVersion) {
    if (!this.isEnabled()) {
      return false;
    }
    try {
      return spaceService.getAllSpacesByFilter(new SpaceFilter()).getSize() > matrixService.getAllLinkedRooms();
    } catch (Exception e) {
      LOG.debug("Could not get the number of spaces", e);
      return false;
    }
  }
}
