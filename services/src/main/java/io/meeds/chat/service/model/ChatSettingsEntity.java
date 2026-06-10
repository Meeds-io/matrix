package io.meeds.chat.service.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatSettingsEntity {
  private boolean            chatEnabled;

  private boolean            privateRoomsEnabled;

  private boolean            spaceRoomsEnabled;

  List<SpaceTemplateSetting> spaceTemplateSetting;
}
