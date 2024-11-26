package io.meeds.chat.model;

import lombok.Data;

@Data
public class SpaceRoom {

  private long   id;

  private String spaceId;

  private String roomId;

  private String firstParticipant;

  private String secondParticipant;
}
