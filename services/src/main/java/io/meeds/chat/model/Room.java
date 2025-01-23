package io.meeds.chat.model;

import lombok.Data;

@Data
public class Room {
  private long   id;

  private String roomId;

  private String spaceId;

  private String firstParticipant;

  private String secondParticipant;
}
