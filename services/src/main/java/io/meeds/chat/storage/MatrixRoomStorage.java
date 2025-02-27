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
package io.meeds.chat.storage;

import io.meeds.chat.dao.MatrixRoomDAO;
import io.meeds.chat.model.Room;
import io.meeds.chat.entity.RoomEntity;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    if (roomEntity != null && StringUtils.isNotBlank(roomEntity.getSpaceId())) {
      return spaceService.getSpaceById(String.valueOf(roomEntity.getSpaceId()));
    } else {
      LOG.warn("Can not find an associated space for the matrix room with ID {}", roomId);
      return null;
    }
  }

  public Room getDMRoomByRoomId(String roomId) {
    RoomEntity roomEntity = matrixRoomDAO.findByRoomId(roomId);
    if (roomEntity != null && StringUtils.isNotBlank(roomEntity.getFirstParticipant())
        && StringUtils.isNotBlank(roomEntity.getSecondParticipant())) {
      return toRoomModel(roomEntity);
    } else {
      LOG.warn("Can not find an associated space for the matrix room with ID {}", roomId);
      return null;
    }
  }

  public Room saveRoomForSpace(String spaceId, String roomId) {
    RoomEntity roomEntity = new RoomEntity();
    roomEntity.setSpaceId(spaceId);
    roomEntity.setRoomId(roomId);
    return toRoomModel(matrixRoomDAO.save(roomEntity));
  }

  public Room saveDirectMessagingRoom(String firstParticipantId, String secondParticipantId, String roomId) {
    RoomEntity roomEntity = new RoomEntity();
    roomEntity.setRoomId(roomId);
    roomEntity.setFirstParticipant(firstParticipantId);
    roomEntity.setSecondParticipant(secondParticipantId);
    return toRoomModel(matrixRoomDAO.save(roomEntity));
  }

  private static List<Room> toRoomList(List<RoomEntity> roomEntities) {
    List<Room> rooms = new ArrayList<>();
    for (RoomEntity roomEntity : roomEntities) {
      rooms.add(toRoomModel(roomEntity));
    }
    return rooms;
  }

  public long getSpaceRoomCount() {
    return matrixRoomDAO.count();
  }

  public Room getDirectMessagingRoom(String firstParticipantId, String secondParticipantId) {
    RoomEntity directMessagingRoom = matrixRoomDAO.findByFirstParticipantAndSecondParticipant(firstParticipantId,
                                                                                              secondParticipantId);
    if (directMessagingRoom != null) {
      return toRoomModel(directMessagingRoom);
    }
    return null;
  }

  public List<Room> getMatrixDMRoomsOfUser(String user) {
    return toDirectMessagingRoomModelList(matrixRoomDAO.findByFirstParticipantOrSecondParticipant(user, user));
  }

  private List<Room> toDirectMessagingRoomModelList(List<RoomEntity> roomEntities) {
    return roomEntities.stream().map(MatrixRoomStorage::toRoomModel).toList();
  }

  public void removeMatrixRoom(String roomId) {
    RoomEntity roomEntity = matrixRoomDAO.findByRoomId(roomId);
    matrixRoomDAO.delete(roomEntity);
  }

  public Room getById(String roomId) {
    RoomEntity roomEntity = matrixRoomDAO.findByRoomIdStartsWith(roomId);
    if(roomEntity != null) {
      return toRoomModel(roomEntity);
    }
    return null;
  }

  /**
   * Converts a RoomEntity to a Room model
   * 
   * @param roomEntity the JPA entity for room
   * @return Room object
   */
  public static Room toRoomModel(RoomEntity roomEntity) {
    Room room = new Room();
    room.setId(roomEntity.getId());
    room.setRoomId(roomEntity.getRoomId());
    room.setSpaceId(roomEntity.getSpaceId());
    room.setFirstParticipant(roomEntity.getFirstParticipant());
    room.setSecondParticipant(roomEntity.getSecondParticipant());
    return room;
  }

  /**
   * Get a list of rooms linked to spaces
   * 
   * @return List of SpaceRoom
   */
  public List<Room> getSpaceRooms() {
    return toRoomList(matrixRoomDAO.findBySpaceIdIsNotNull());
  }
}
