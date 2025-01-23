package io.meeds.chat.dao;

import io.meeds.chat.entity.RoomEntity;
import io.meeds.chat.model.DirectMessagingRoom;
import io.meeds.chat.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MatrixRoomDAO extends JpaRepository<RoomEntity, Long> {

  public RoomEntity findByRoomId(String roomId);

  public RoomEntity findBySpaceId(String spaceId);

  @Query("""
          SELECT m from MatrixRoom m
          WHERE (m.firstParticipant = ?1
          AND m.secondParticipant = ?2)
          OR
          (m.firstParticipant = ?2
          AND m.secondParticipant = ?1)
      """)
  public RoomEntity findByFirstParticipantAndSecondParticipant(String firstParticipant, String secondParticipant);

  public List<RoomEntity> findByFirstParticipantOrSecondParticipant(String userOne, String userTwo);

  public RoomEntity findByRoomIdStartsWith(String roomId);
}
