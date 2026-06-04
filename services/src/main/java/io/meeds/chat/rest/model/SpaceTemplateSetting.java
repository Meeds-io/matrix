package io.meeds.chat.rest.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpaceTemplateSetting {
  private long    id;

  private String  name;

  private String  icon;

  private boolean authorized;

  private boolean defaultStatus;
}
