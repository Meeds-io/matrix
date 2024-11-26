package io.meeds.chat.dao;

import io.meeds.chat.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatrixRoomDAO extends JpaRepository<RoomEntity, Long> {

  public RoomEntity findByRoomId(String roomId);

  public RoomEntity findBySpaceId(String spaceId);
}
