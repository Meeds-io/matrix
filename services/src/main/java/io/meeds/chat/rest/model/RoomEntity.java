package io.meeds.chat.rest.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class RoomEntity implements Serializable {
  private Message      lastMessage;

  private String       topic;

  private String       name;

  private String       avatarUrl;

  private String       id;

  private boolean      isEnabledUser;

  private String       presence;

  private boolean      isDirectChat;

  private long         updated;

  private long         unreadMessages;

  private String       dmMemberId;

  private boolean      isFavorite;

  private boolean      isExternal;

  private List<Member> members;

}
