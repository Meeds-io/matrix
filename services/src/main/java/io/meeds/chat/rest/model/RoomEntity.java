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

  private boolean      enabledUser;

  private String       presence;

  private boolean      directChat;

  private long         updated;

  private long         unreadMessages;

  private String       dmMemberId;

  private String       userId;

  private String       spaceId;

  private boolean      favorite;

  private boolean      external;

  private List<Member> members;

}
