package io.meeds.chat.storage;

import io.meeds.chat.model.SpaceRoom;
import io.meeds.chat.dao.SpaceRoomAssociationDAO;
import io.meeds.chat.entity.SpaceRoomEntity;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class SpaceRoomStorage {
  private static final Log LOG = ExoLogger.getLogger(SpaceRoomStorage.class);

  @Autowired
  private SpaceRoomAssociationDAO spaceRoomAssociationDAO;

  @Autowired
  private SpaceService            spaceService;
  
  public String getMatrixRoomBySpaceId(String spaceId) {
    SpaceRoomEntity spaceRoomAssociationEntity = spaceRoomAssociationDAO.findBySpaceId(spaceId);
    if (spaceRoomAssociationEntity != null) {
      return spaceRoomAssociationEntity.getRoomId();
    } else {
      LOG.warn("Can not find an associated matrix room for the space with ID {}", spaceId);
      return null;
    }
  }  
  public Space getSpaceIdByMatrixRoomId(String roomId) {
    SpaceRoomEntity spaceRoomAssociationEntity = spaceRoomAssociationDAO.findByRoomId(roomId);
    if (spaceRoomAssociationEntity != null) {
      return spaceService.getSpaceById(String.valueOf(spaceRoomAssociationEntity.getSpaceId()));
    } else {
      LOG.warn("Can not find an associated space for the matrix room with ID {}", roomId);
      return null;
    }
  }

  public SpaceRoom createSpaceRoomAssociation(String spaceId, String roomId) {
    SpaceRoomEntity spaceRoomEntity = new SpaceRoomEntity();
    spaceRoomEntity.setSpaceId(spaceId);
    spaceRoomEntity.setRoomId(roomId);
    return toSpaceRoomModel(spaceRoomAssociationDAO.save(spaceRoomEntity));
  }

  private static SpaceRoom toSpaceRoomModel(SpaceRoomEntity spaceRoomEntity) {
    SpaceRoom spaceRoomModel = new SpaceRoom();
    spaceRoomModel.setSpaceId(spaceRoomEntity.getSpaceId());
    spaceRoomModel.setRoomId(spaceRoomEntity.getRoomId());
    return spaceRoomModel;
  }

}
