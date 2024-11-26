package io.meeds.chat.storage;

import io.meeds.chat.dao.MatrixRoomDAO;
import io.meeds.chat.model.SpaceRoom;
import io.meeds.chat.entity.RoomEntity;
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
    private MatrixRoomDAO matrixRoomDAO;

    @Autowired
    private SpaceService spaceService;

    public String getMatrixRoomBySpaceId(String spaceId) {
        RoomEntity roomEntity = matrixRoomDAO.findBySpaceId(spaceId);
        if (roomEntity != null) {
            return roomEntity.getRoomId();
        } else {
            LOG.warn("Can not find an associated matrix room for the space with ID {}", spaceId);
            return null;
        }
    }

    public Space getSpaceIdByMatrixRoomId(String roomId) {
        RoomEntity roomEntity = matrixRoomDAO.findByRoomId(roomId);
        if (roomEntity != null) {
            return spaceService.getSpaceById(String.valueOf(roomEntity.getSpaceId()));
        } else {
            LOG.warn("Can not find an associated space for the matrix room with ID {}", roomId);
            return null;
        }
    }

    public SpaceRoom saveRoomForSpace(String spaceId, String roomId) {
        RoomEntity roomEntity = new RoomEntity();
        roomEntity.setSpaceId(spaceId);
        roomEntity.setRoomId(roomId);
        return toSpaceRoomModel(matrixRoomDAO.save(roomEntity));
    }

    private static SpaceRoom toSpaceRoomModel(RoomEntity roomEntity) {
        SpaceRoom spaceRoomModel = new SpaceRoom();
        spaceRoomModel.setSpaceId(roomEntity.getSpaceId());
        spaceRoomModel.setRoomId(roomEntity.getRoomId());
        return spaceRoomModel;
    }

    public long getSpaceRoomCount() {
        return matrixRoomDAO.count();
    }
}
