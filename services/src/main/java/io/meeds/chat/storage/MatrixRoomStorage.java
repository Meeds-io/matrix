package io.meeds.chat.storage;

import io.meeds.chat.dao.MatrixRoomDAO;
import io.meeds.chat.model.DirectMessagingRoom;
import io.meeds.chat.model.SpaceRoom;
import io.meeds.chat.entity.RoomEntity;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MatrixRoomStorage {
  private static final Log LOG = ExoLogger.getLogger(MatrixRoomStorage.class);

  @Autowired
  private MatrixRoomDAO    matrixRoomDAO;

  @Autowired
  private SpaceService     spaceService;

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

  public DirectMessagingRoom saveDirectMessagingRoom(String firstParticipantId, String secondParticipantId, String roomId) {
    RoomEntity roomEntity = new RoomEntity();
    roomEntity.setRoomId(roomId);
    roomEntity.setFirstParticipant(firstParticipantId);
    roomEntity.setSecondParticipant(secondParticipantId);
    return toDirectMessagingRoomModel(matrixRoomDAO.save(roomEntity));
  }

  private DirectMessagingRoom toDirectMessagingRoomModel(RoomEntity room) {
    DirectMessagingRoom directMessagingRoom = new DirectMessagingRoom();
    directMessagingRoom.setRoomId(room.getRoomId());
    directMessagingRoom.setFirstParticipant(room.getFirstParticipant());
    directMessagingRoom.setSecondParticipant(room.getSecondParticipant());
    return directMessagingRoom;
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

  public DirectMessagingRoom getDirectMessagingRoom(String firstParticipantId, String secondParticipantId) {
    RoomEntity directMessagingRoom = matrixRoomDAO.findByFirstParticipantAndSecondParticipant(firstParticipantId,
                                                                                              secondParticipantId);
    if (directMessagingRoom != null) {
      return toDirectMessagingRoomModel(directMessagingRoom);
    }
    return null;
  }

  public List<DirectMessagingRoom> getMatrixDMRoomsOfUser(String user) {
    return toDirectMessagingRoomModelList(matrixRoomDAO.findByFirstParticipantOrSecondParticipant(user, user));
  }

  private List<DirectMessagingRoom> toDirectMessagingRoomModelList(List<RoomEntity> roomEntities) {
    return roomEntities.stream().map(roomEntity -> toDirectMessagingRoomModel(roomEntity)).toList();
  }
}
