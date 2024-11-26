package io.meeds.chat.model;

import lombok.Data;

@Data
public class DirectMessagingRoom {

    private long   id;

    private String roomId;

    private String firstParticipant;

    private String secondParticipant;
}

