package io.meeds.chat.rest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor

@Data
public class Member implements Serializable {
  private String id;

  private String name;

  private String avatarUrl;

  private long   lastUpdated;
}
