/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package io.meeds.chat.service;

import static io.meeds.chat.service.utils.MatrixConstants.AGENT_MENTIONED_EVENT;
import static io.meeds.chat.service.utils.MatrixConstants.USER_MATRIX_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;

import io.meeds.chat.constant.ChatRoomKind;
import io.meeds.chat.model.ChatRoomMention;
import io.meeds.chat.model.MatrixMessage;
import io.meeds.chat.model.Room;
import io.meeds.pwa.service.PwaNotificationService;
import io.meeds.social.core.identity.service.AccountKindService;

@ExtendWith(MockitoExtension.class)
class ChatNotificationServiceAgentPushTest {

  private static final String     LOGIN          = "lea";

  private static final String     LEA_MATRIX_ID  = "@lea:matrix.exo.tn";

  private static final String     ROOM_ID        = "!room:matrix.exo.tn";

  private static final String     EVENT_ID       = "$mention";

  @Mock
  private MatrixService           matrixService;

  @Mock
  private IdentityManager         identityManager;

  @Mock
  private ListenerService         listenerService;

  @Mock
  private AccountKindService      accountKindService;

  @Mock
  private PwaNotificationService  pwaNotificationService;

  @InjectMocks
  private ChatNotificationService chatNotificationService;

  @BeforeEach
  void setUp() {
    lenient().when(matrixService.extractRoomId(anyString())).thenAnswer(invocation -> {
      String id = invocation.getArgument(0);
      return id.endsWith(":matrix.exo.tn") ? id.substring(0, id.indexOf(':')) : id;
    });
    Identity lea = new Identity(LOGIN);
    Profile profile = new Profile(lea);
    profile.setProperty(USER_MATRIX_ID, LEA_MATRIX_ID);
    lea.setProfile(profile);
    lenient().when(identityManager.getOrCreateUserIdentity(LOGIN)).thenReturn(lea);
    lenient().when(matrixService.getUserFullMatrixID(LEA_MATRIX_ID)).thenReturn(LEA_MATRIX_ID);
    lenient().when(matrixService.findUserByMatrixId("@max:matrix.exo.tn")).thenReturn("max");
    lenient().when(matrixService.findUserByMatrixId(LEA_MATRIX_ID)).thenReturn(LOGIN);
  }

  /**
   * A direct message is decided on first: no Synapse read, no login, nothing
   * handed over, so a worker's pusher is never held by a direct message.
   */
  @Test
  void directMessageIsIgnoredBeforeAnySynapseCall() throws Exception {
    when(matrixService.getById(ROOM_ID)).thenReturn(room("!room", null));

    chatNotificationService.onAgentPushReceived(EVENT_ID, ROOM_ID, LOGIN);

    verify(matrixService, never()).getRoomEventOrThrow(anyString(), anyString(), any());
    verify(matrixService, never()).getRoomEvent(anyString(), anyString(), nullable(String.class));
    verify(matrixService, never()).getAccessToken(any());
    verifyNoInteractions(listenerService);
  }

  @Test
  void unknownOrUnreadableRoomIsIgnored() throws Exception {
    when(matrixService.getById(ROOM_ID)).thenReturn(null);
    chatNotificationService.onAgentPushReceived(EVENT_ID, ROOM_ID, LOGIN);

    when(matrixService.getById(ROOM_ID)).thenThrow(new IllegalStateException("non unique"));
    chatNotificationService.onAgentPushReceived(EVENT_ID, ROOM_ID, LOGIN);

    verify(matrixService, never()).getRoomEventOrThrow(anyString(), anyString(), any());
    verifyNoInteractions(listenerService);
  }

  /** The room lookup matches a prefix: a longer stored id is another room. */
  @Test
  void roomFoundByPrefixOnlyIsIgnored() throws Exception {
    when(matrixService.getById("!r")).thenReturn(room("!room", 7L));

    chatNotificationService.onAgentPushReceived(EVENT_ID, "!r", LOGIN);

    verify(matrixService, never()).getRoomEventOrThrow(anyString(), anyString(), any());
    verifyNoInteractions(listenerService);
  }

  @Test
  void mentionInASpaceRoomIsHandedOverOnce() throws Exception {
    when(matrixService.getById(ROOM_ID)).thenReturn(room("!room", 7L));
    when(matrixService.getRoomEventOrThrow(EVENT_ID, ROOM_ID, MatrixAgentPusherService.SYNAPSE_TIMEOUT))
        .thenReturn(message("@max:matrix.exo.tn", List.of(LEA_MATRIX_ID), null));

    chatNotificationService.onAgentPushReceived(EVENT_ID, ROOM_ID, LOGIN);

    ArgumentCaptor<ChatRoomMention> mention = ArgumentCaptor.forClass(ChatRoomMention.class);
    verify(listenerService).broadcast(eq(AGENT_MENTIONED_EVENT), any(Room.class), mention.capture());
    assertEquals(new ChatRoomMention(ChatRoomKind.SPACE,
                                     EVENT_ID,
                                     ROOM_ID,
                                     7L,
                                     LOGIN,
                                     "max",
                                     "@max:matrix.exo.tn",
                                     "@Lea what did we decide?",
                                     List.of(LEA_MATRIX_ID),
                                     1700000000000L),
                 mention.getValue());
  }

  @Test
  void plainMessageRoomMentionOwnMessageAndEditHandNothingOver() throws Exception {
    when(matrixService.getById(ROOM_ID)).thenReturn(room("!room", 7L));
    when(matrixService.getRoomEventOrThrow(EVENT_ID, ROOM_ID, MatrixAgentPusherService.SYNAPSE_TIMEOUT))
        .thenReturn(message("@max:matrix.exo.tn", null, null),
                    message("@max:matrix.exo.tn", List.of("@max:matrix.exo.tn"), null),
                    message(LEA_MATRIX_ID, List.of(LEA_MATRIX_ID), null),
                    message("@max:matrix.exo.tn", List.of(LEA_MATRIX_ID), "m.replace"));

    for (int i = 0; i < 4; i++) {
      chatNotificationService.onAgentPushReceived(EVENT_ID, ROOM_ID, LOGIN);
    }

    verify(matrixService, org.mockito.Mockito.times(4)).getRoomEventOrThrow(EVENT_ID,
                                                                           ROOM_ID,
                                                                           MatrixAgentPusherService.SYNAPSE_TIMEOUT);
    verifyNoInteractions(listenerService);
  }

  /** A reply that mentions the worker is still a mention: only an edit is not. */
  @Test
  void replyMentioningTheWorkerIsHandedOver() throws Exception {
    when(matrixService.getById(ROOM_ID)).thenReturn(room("!room", 7L));
    MatrixMessage reply = message("@max:matrix.exo.tn", List.of(LEA_MATRIX_ID), null);
    reply.setInReplyTo("$question");
    when(matrixService.getRoomEventOrThrow(EVENT_ID, ROOM_ID, MatrixAgentPusherService.SYNAPSE_TIMEOUT)).thenReturn(reply);

    chatNotificationService.onAgentPushReceived(EVENT_ID, ROOM_ID, LOGIN);

    verify(listenerService).broadcast(eq(AGENT_MENTIONED_EVENT), any(Room.class), any(ChatRoomMention.class));
  }

  @Test
  void failuresAreSwallowed() throws Exception {
    when(matrixService.getById(ROOM_ID)).thenReturn(room("!room", 7L));
    when(matrixService.getRoomEventOrThrow(EVENT_ID, ROOM_ID, MatrixAgentPusherService.SYNAPSE_TIMEOUT))
        .thenThrow(new java.io.IOException("request timed out"))
        .thenReturn(message("@max:matrix.exo.tn", List.of(LEA_MATRIX_ID), null));
    doThrow(new IllegalStateException("listener failed")).when(listenerService).broadcast(anyString(), any(), any());

    chatNotificationService.onAgentPushReceived(EVENT_ID, ROOM_ID, LOGIN);
    chatNotificationService.onAgentPushReceived(EVENT_ID, ROOM_ID, LOGIN);

    verify(listenerService).broadcast(eq(AGENT_MENTIONED_EVENT), any(Room.class), any(ChatRoomMention.class));
  }

  /**
   * A worker's account never takes the people's path: no event read with a
   * device token, no mention notification, no popup, no hand-off.
   */
  @Test
  void workerRecipientLeavesThePeoplesPathRightAfterTheRoomLookup() throws Exception {
    lenient().when(matrixService.getById(ROOM_ID)).thenReturn(room("!room", 7L));
    lenient().when(accountKindService.computeAccountKind(LOGIN)).thenReturn("agent");

    chatNotificationService.onMatrixPushReceived(EVENT_ID, ROOM_ID, LOGIN, "jwt");

    verify(matrixService, never()).getRoomEvent(anyString(), anyString(), nullable(String.class));
    verify(matrixService, never()).getAccessToken(any());
    verifyNoInteractions(pwaNotificationService, listenerService);
  }

  /** A person keeps the path: the event is resolved as before. */
  @Test
  void personRecipientKeepsThePeoplesPath() throws Exception {
    when(matrixService.getById(ROOM_ID)).thenReturn(room("!room", 7L));
    lenient().when(accountKindService.computeAccountKind("max")).thenReturn(null);

    chatNotificationService.onMatrixPushReceived(EVENT_ID, ROOM_ID, "max", "jwt");

    verify(matrixService).getRoomEvent(EVENT_ID, ROOM_ID, null);
  }

  private Room room(String roomId, Long spaceId) {
    Room room = new Room();
    room.setRoomId(roomId);
    room.setSpaceId(spaceId);
    return room;
  }

  private MatrixMessage message(String sender, List<String> mentions, String relatesToType) {
    MatrixMessage message = new MatrixMessage();
    message.setEventId(EVENT_ID);
    message.setRoomId(ROOM_ID);
    message.setType("m.room.message");
    message.setSender(sender);
    message.setMessageContent("@Lea what did we decide?");
    message.setMentionedUsers(mentions);
    message.setTimeStamp(1700000000000L);
    message.setRelatesToType(relatesToType);
    return message;
  }
}
