package io.meeds.chat.rest.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class RoomList implements Serializable {
  private List<RoomEntity> rooms;

  private long             totalUnreadMessages;
}
