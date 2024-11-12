package io.meeds.chat.dao;

import io.meeds.chat.entity.SpaceRoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceRoomAssociationDAO extends JpaRepository<SpaceRoomEntity, Long> {

  public SpaceRoomEntity findByRoomId(String roomId);

  public SpaceRoomEntity findBySpaceId(String spaceId);
}
