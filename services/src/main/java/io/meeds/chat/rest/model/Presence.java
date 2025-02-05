package io.meeds.chat.rest.model;

import lombok.Data;

@Data
public class Presence {
  private String userIdOnMatrix;

  private String presence;

  private String statusMessage;
}
