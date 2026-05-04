/**
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
  public Long             spaceId;

  @Column(name = "FIRST_PARTICIPANT")
  public String             firstParticipant;

  @Column(name = "SECOND_PARTICIPANT")
  public String             secondParticipant;

  @Enumerated(EnumType.ORDINAL)
  @Column(name = "STATUS")
  public RoomStatus         status           = RoomStatus.ENABLED;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getSpaceId() {
    return spaceId;
  }

  public void setSpaceId(Long spaceId) {
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

  public RoomStatus getStatus() {
    return status;
  }

  public void setStatus(RoomStatus status) {
    this.status = status;
  }
}
