package io.meeds.chat.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.io.Serializable;

@Entity(name = "SpaceRoom")
@DynamicUpdate
@Table(name = "SPACE_MATRIX_ROOM")
public class SpaceRoomEntity implements Serializable {

  private static final long serialVersionUID = -4268296851540773942L;

  @Id
  @SequenceGenerator(name = "SEQ_SPACE_MATRIX_ROOM_ID", sequenceName = "SEQ_SPACE_MATRIX_ROOM_ID", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_SPACE_MATRIX_ROOM_ID")
  @Column(name = "ID")
  private Long              id;

  @Column(name = "SPACE_ID", nullable = false)
  public String             spaceId;

  @Column(name = "MATRIX_ROOM_ID", nullable = false)
  public String             roomId;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getSpaceId() {
    return spaceId;
  }

  public void setSpaceId(String spaceId) {
    this.spaceId = spaceId;
  }

  public String getRoomId() {
    return roomId;
  }

  public void setRoomId(String roomId) {
    this.roomId = roomId;
  }
}
