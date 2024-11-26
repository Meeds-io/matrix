package io.meeds.chat.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

import java.io.Serial;
import java.io.Serializable;

@Entity(name = "MatrixRoom")
@DynamicUpdate
@Table(name = "MATRIX_ROOM")
public class RoomEntity implements Serializable {

  private static final long serialVersionUID = -4268296851540773942L;

  @Id
  @SequenceGenerator(name = "SEQ_SPACE_MATRIX_ROOM_ID", sequenceName = "SEQ_SPACE_MATRIX_ROOM_ID", allocationSize = 1)
  @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_SPACE_MATRIX_ROOM_ID")
  @Column(name = "ID")
  private Long              id;

  @Column(name = "MATRIX_ROOM_ID", nullable = false)
  public String             roomId;

  @Column(name = "SPACE_ID")
  public String             spaceId;

  @Column(name = "FIRST_PARTICIPANT")
  public String             firstParticipant;

  @Column(name = "SECOND_PARTICIPANT")
  public String             secondParticipant;



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

  public String getFirstParticipant() {
    return firstParticipant;
  }

  public void setFirstParticipant(String firstParticipant) {
    this.firstParticipant = firstParticipant;
  }

  public String getSecondParticipant() {
    return secondParticipant;
  }

  public void setSecondParticipant(String secondParticipant) {
    this.secondParticipant = secondParticipant;
  }
}
