/*
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.chat.rest;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.meeds.chat.model.Room;
import io.meeds.chat.rest.model.Message;
import io.meeds.chat.rest.model.Presence;
import io.meeds.chat.rest.model.RoomEntity;
import io.meeds.chat.rest.model.RoomList;
import io.meeds.chat.service.MatrixSynchronizationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.service.storage.NotificationService;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.portal.localization.LocaleContextInfoUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.resources.ResourceBundleService;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.service.LinkProvider;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.rest.api.RestUtils;
import org.exoplatform.social.rest.entity.IdentityEntity;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;
import org.exoplatform.ws.frameworks.json.value.JsonValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static org.exoplatform.social.rest.api.EntityBuilder.IDENTITIES_TYPE;
import static org.exoplatform.social.rest.api.EntityBuilder.buildEntityProfile;

@RestController
@RequestMapping("/matrix")
@Tag(name = "/matrix", description = "Manages Matrix integration")
public class MatrixRest implements ResourceContainer {

  private static final Log             LOG = ExoLogger.getLogger(MatrixRest.class.toString());

  @Autowired
  private SpaceService                 spaceService;

  @Autowired
  private MatrixService                matrixService;

  @Autowired
  private MatrixSynchronizationService matrixSynchronizationService;

  @Autowired
  private IdentityManager              identityManager;

  @Autowired
  private NotificationService          notificationService;

  @Autowired
  private ResourceBundleService        resourceBundleService;

  @GetMapping
  @Secured("users")
  @Operation(summary = "Get the matrix room bound to the current space", method = "GET", description = "Get the id of the matrix room bound to the current space")
  @ApiResponses(value = { @ApiResponse(responseCode = "2rest00", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public RoomEntity getMatrixRoomBySpaceId(@Parameter(description = "The space Id")
  @RequestParam(name = "spaceId")
  String spaceId) {
    if (StringUtils.isBlank(spaceId)) {
      LOG.error("Could not get the URL for the space, missing space ID");
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the space Id parameter is required!");
    }
    Space space = this.spaceService.getSpaceById(spaceId);
    if (space == null) {
      LOG.error("Could not find a space with id {}", spaceId);
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Can not find a space with Id = " + spaceId);
    }
    String userName = ConversationState.getCurrent().getIdentity().getUserId();
    if (!spaceService.isMember(space, userName) && !spaceService.isManager(space, userName)
        && !spaceService.isSuperManager(userName)) {
      LOG.error("User is not allowed to get the team associated with the space {}", space.getDisplayName());
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                                        "User " + userName + " is not allowed to get information from space"
                                            + space.getPrettyName());
    }

    Room room = matrixService.getRoomBySpace(space);

    if (room == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "no Matrix room for space " + space.getDisplayName());
    }
    return buildRoomEntityFromRoom(room, userName);
  }

  @GetMapping("dmRoom")
  @Secured("users")
  @Operation(summary = "Get the matrix room used for direct messaging between provided users", method = "GET", description = "Get the matrix room used for direct messaging between provided users")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public RoomEntity getDirectMessagingRoom(HttpServletRequest request,
                                           @Parameter(description = "The first participant")
                                           @RequestParam(name = "firstParticipant")
                                           String firstParticipant,
                                           @Parameter(description = "The second participant")
                                           @RequestParam(name = "secondParticipant")
                                           String secondParticipant) {
    String currentUser = request.getRemoteUser();
    if (StringUtils.isBlank(firstParticipant) || StringUtils.isBlank(secondParticipant)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the ids of the participants should not be null");
    }
    Room directMessagingRoom = matrixService.getDirectMessagingRoom(firstParticipant, secondParticipant);
    if (directMessagingRoom != null) {
      return buildRoomEntityFromRoom(directMessagingRoom, currentUser);
    } else {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                                        "Could not find a room for participants %s and %s".formatted(firstParticipant,
                                                                                                     secondParticipant));
    }
  }

  @PostMapping
  @Secured("users")
  @Operation(summary = "Gets or creates the Matrix room for the direct messaging", method = "POST", description = "Gets or creates the Matrix room for the direct messaging")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public RoomEntity createDirectMessagingRoom(HttpServletRequest request,
                                              @RequestBody(description = "Matrix object to create", required = true)
                                              @org.springframework.web.bind.annotation.RequestBody
                                              Room room) {
    if (StringUtils.isBlank(room.getFirstParticipant()) || StringUtils.isBlank(room.getSecondParticipant())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the ids of the participants should not be null");
    }
    try {
      String currentUserName = request.getRemoteUser();
      return buildRoomEntityFromRoom(matrixService.createDirectMessagingRoom(room), currentUserName);
    } catch (ObjectAlreadyExistsException objectAlreadyExists) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, objectAlreadyExists.getMessage());
    }
  }

  @PostMapping("notify")
  @Operation(summary = "Receives push notification from Matrix", method = "POST", description = "Receives push notification from Matrix")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public String notify(@RequestBody(description = "Notification received from Matrix", required = true)
  @org.springframework.web.bind.annotation.RequestBody
  String notification) {
    JsonGeneratorImpl jsonGenerator = new JsonGeneratorImpl();
    try {
      JsonValue jsonValue = jsonGenerator.createJsonObjectFromString(notification);
      JsonValue notifJsonValue = jsonValue.getElement("notification");
      int unreadCount = notifJsonValue.getElement("counts").getElement("unread").getIntValue();
      String roomId = notifJsonValue.getElement("room_id").getStringValue();
      String eventId = notifJsonValue.getElement("event_id").getStringValue();
      String pushKey = "";
      if (notifJsonValue.getElement("devices").getElements().hasNext()) {
        JsonValue device = notifJsonValue.getElement("devices").getElements().next();
        pushKey = device.getElement("pushkey").getStringValue();
      }
      if (StringUtils.isNotBlank(pushKey)) {
        String userName = parseUserFromToken(pushKey);
        if (StringUtils.isNotBlank(userName)) {
          sendPushNotification(userName, roomId, unreadCount);
        }
      }
      return """
          {
            "rejected": []
          }
          """;
    } catch (Exception e) {
      LOG.error("Problem parsing notification received from Matrix", e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR);
    }

  }

  @GetMapping("linkRoom")
  @Secured("users")
  @Operation(summary = "Set the matrix room bound to the current space", method = "POST", description = "Set the id of the matrix room bound to the current space")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public boolean linkSpaceToRoom(@RequestParam("spaceGroupId")
  String spaceGroupId, @RequestParam(name = "roomId")
  String roomId, @RequestParam(name = "create", required = false)
  Boolean create) {
    try {
      if (StringUtils.isBlank(spaceGroupId)) {
        LOG.error("Could not connect the space to a team, space name is missing");
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "space group Id is required");
      }

      Space space = spaceService.getSpaceByGroupId("/spaces/" + spaceGroupId);

      if (space == null) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "space with group Id " + spaceGroupId + "was not found");
      }

      Room room = matrixService.getRoomBySpace(space);
      if (StringUtils.isNotBlank(room.getRoomId())) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                          "space with group Id " + spaceGroupId + "has already a room with ID "
                                              + room.getRoomId());
      }

      if (StringUtils.isBlank(roomId) && create) {
        roomId = matrixService.createRoomForSpaceOnMatrix(space);
      }

      matrixService.linkSpaceToMatrixRoom(space, roomId);
      return true;
    } catch (Exception e) {
      LOG.error("Could not link space {} to Matrix room {}", spaceGroupId, roomId, e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                                        "Could not link space " + spaceGroupId + " to Matrix room " + roomId + " : "
                                            + e.getMessage());
    }
  }

  @GetMapping("dmRooms")
  @Secured("users")
  @Operation(summary = "Get all the matrix rooms used for direct messaging of a defined user", method = "GET", description = "Get all the matrix rooms used for direct messaging of a defined user")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public Map<String, String[]> getUserDirectMessagingRooms(@Parameter(description = "The user")
  @RequestParam(name = "user")
  String user) {

    Map<String, String[]> userDMRooms = new HashMap<>();
    List<Room> rooms = matrixService.getMatrixDMRoomsOfUser(user);
    for (Room dmRoom : rooms) {
      Identity userIdentity;
      if (dmRoom.getFirstParticipant().equals(user)) {
        userIdentity = identityManager.getOrCreateUserIdentity(dmRoom.getSecondParticipant());
      } else {
        userIdentity = identityManager.getOrCreateUserIdentity(dmRoom.getFirstParticipant());
      }
      if (userIdentity != null) {
        String userMatrixId = "@" + userIdentity.getProfile().getProperty(USER_MATRIX_ID) + ":"
            + PropertyManager.getProperty(MATRIX_SERVER_NAME);
        userDMRooms.put(userMatrixId, new String[] { dmRoom.getRoomId() });
      }
    }
    return userDMRooms;
  }

  @GetMapping("byRoom")
  @Secured("users")
  @Operation(summary = "Get the space linked to the specified matrix room", method = "GET", description = "Get the space linked to the specified matrix room")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "Space not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public String getByRoomId(@Parameter(description = "The room Id")
  @RequestParam(name = "roomId")
  String roomId) {
    if (StringUtils.isNotBlank(roomId) && roomId.contains(PropertyManager.getProperty(MATRIX_SERVER_NAME))) {
      roomId = roomId.substring(0, roomId.indexOf(":"));
    }
    Room room = matrixService.getById(roomId);
    if (room == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no room with ID " + roomId);
    } else {
      if (StringUtils.isNotBlank(room.getSpaceId())) {
        Space space = spaceService.getSpaceById(room.getSpaceId());
        if (space != null) {
          return identityManager.getOrCreateSpaceIdentity(space.getPrettyName()).getId();
        } else {
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There is no space with ID " + room.getSpaceId());
        }
      } else {
        org.exoplatform.services.security.Identity connecteduserIdentity = ConversationState.getCurrent().getIdentity();
        if (room.getFirstParticipant().equals(connecteduserIdentity.getUserId())) {
          return identityManager.getOrCreateUserIdentity(room.getSecondParticipant()).getId();
        } else {
          return identityManager.getOrCreateUserIdentity(room.getFirstParticipant()).getId();
        }
      }
    }
  }

  @GetMapping("byRoomId")
  @Secured("users")
  @Operation(summary = "Get the room by Id", method = "GET", description = "Get the room by Id")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<RoomEntity> getRoomById(HttpServletRequest request,
                                                WebRequest webRequest,
                                                @Parameter(description = "The room Id")
                                                @RequestParam(name = "roomId")
                                                String roomId) {
    String userName = request.getRemoteUser();
    Room room = matrixService.getById(roomId);
    if (room == null || !matrixService.canAccess(room, userName)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    RoomEntity roomEntity = buildRoomEntityFromRoom(room, userName);
    return ResponseEntity.ok().body(roomEntity);
  }

  @GetMapping("spaceRoom")
  @Secured("users")
  @Operation(summary = "Get the room by Id", method = "GET", description = "Get the room by Id")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<RoomEntity> getRoomBySpaceId(HttpServletRequest request,
                                                     WebRequest webRequest,
                                                     @Parameter(description = "The room Id")
                                                     @RequestParam(name = "spaceId")
                                                     long spaceId) {
    String userName = request.getRemoteUser();
    Space space = spaceService.getSpaceById(spaceId);
    if (space == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    Room room = matrixService.getRoomBySpace(space);

    if (room == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    if (!matrixService.canAccess(room, userName)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    RoomEntity roomEntity = buildRoomEntityFromRoom(room, userName);
    return ResponseEntity.ok().body(roomEntity);
  }

  @GetMapping("userByMatrixId")
  @Secured("users")
  @Operation(summary = "Get the user Identity for the provided matrix user Id", method = "GET", description = "Get the user Identity for the provided matrix user Id")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<IdentityEntity> getIdentityByUserMatrixId(HttpServletRequest request,
                                                                  WebRequest webRequest,
                                                                  @Parameter(description = "The user Id on Matrix")
                                                                  @RequestParam(name = "userMatrixId")
                                                                  String userMatrixId) {

    String requestURI = request.getRequestURI();
    Identity foundIdentity = matrixService.findUserByMatrixId(userMatrixId);
    if (foundIdentity == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    if (webRequest.checkNotModified(String.valueOf(foundIdentity.getCacheTime()), foundIdentity.getCacheTime())) {
      return null;
    }
    return ResponseEntity.ok()
                         .eTag(String.valueOf(foundIdentity.getCacheTime()))
                         .body(buildIdentityEntity(foundIdentity, requestURI, "settings"));
  }

  @PostMapping("processRooms")
  @Secured("users")
  @Operation(summary = "Process the list of rooms and add needed information", method = "POST", description = "Process the list of rooms and add needed information")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<RoomList> processRooms(HttpServletRequest request,
                                               @RequestBody(description = "Rooms received from Matrix", required = true)
                                               @org.springframework.web.bind.annotation.RequestBody
                                               RoomList rooms) {
    String userName = request.getRemoteUser();
    rooms = processRooms(rooms, userName);
    return ResponseEntity.ok().body(rooms);
  }

  @PutMapping("setStatus")
  @Secured("users")
  @Operation(summary = "Set the presence status of the user", method = "PUT", description = "Set the presence status of the user")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<String> updatePresenceStatus(@RequestBody(description = "Rooms received from Matrix", required = true)
  @org.springframework.web.bind.annotation.RequestBody
  Presence presence) {
    String presenceStatus = matrixService.updateUserPresence(presence.getUserIdOnMatrix(),
                                                             presence.getPresence(),
                                                             presence.getStatusMessage());
    return ResponseEntity.ok().body(presenceStatus);
  }

  @GetMapping("sync")
  @Secured("administrators")
  @Operation(summary = "Get the user Identity for the provided matrix user Id", method = "GET", description = "Get the user Identity for the provided matrix user Id")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ResponseEntity<String> syncUsersAndSpaces(HttpServletRequest request, WebRequest webRequest) {
    try {
      matrixSynchronizationService.synchronizeUsers();
      matrixSynchronizationService.synchronizeSpaces();
      return ResponseEntity.ok().body("Synchronization finished for users and spaces with Matrix server");
    } catch (Exception e) {
      LOG.error("Could not synchronise users and spaces", e);
      return ResponseEntity.internalServerError()
                           .body("An error occurred when synchronizing users and spaces with Matrix server");
    }
  }

  @GetMapping("/profile/{userMatrixId}")
  @Secured("users")
  @Operation(summary = "Redirect to the user profile using the matrix user Id", method = "GET", description = "Redirect to the user profile using the matrix user Id")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "404", description = "User not found"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public void redirectToProfile(HttpServletResponse response,
                                @Parameter(description = "The user Id on Matrix")
                                @PathVariable(name = "userMatrixId")
                                String userMatrixId) throws IOException {
    Identity userIdentity = matrixService.findUserByMatrixId(matrixService.extractUserId(userMatrixId));
    if(userIdentity != null) {
      response.sendRedirect(LinkProvider.getProfileUri(userIdentity.getRemoteId()));
    }
  }

  private void sendPushNotification(String participant, String roomId, int unreadCount) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance();
    ctx.append(MATRIX_ROOM_ID, roomId);
    ctx.append(MATRIX_ROOM_MEMBER, participant);
    ctx.append(MATRIX_ROOM_UNREAD_COUNT, unreadCount);
    ctx.getNotificationExecutor().with(ctx.makeCommand(PluginKey.key(MATRIX_MESSAGE_RECEIVED_NOTIFICATION_PLUGIN))).execute(ctx);
  }

  private String parseUserFromToken(String token) {
    byte[] secret = PropertyManager.getProperty(MATRIX_JWT_SECRET).getBytes();
    Jws<Claims> jws = Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(secret)).build().parseClaimsJws(token);
    return String.valueOf(jws.getBody().getSubject());
  }

  private IdentityEntity buildIdentityEntity(Identity identity, String restPath, String expand) {
    IdentityEntity identityEntity = new IdentityEntity(identity.getId());
    identityEntity.setHref(RestUtils.getRestUrl(IDENTITIES_TYPE, identity.getId(), restPath));
    identityEntity.setProviderId(identity.getProviderId());
    identityEntity.setRemoteId(identity.getRemoteId());
    identityEntity.setDeleted(identity.isDeleted());
    identityEntity.setProfile(buildEntityProfile(identity.getProfile(), restPath, expand));
    return identityEntity;
  }

  private RoomEntity buildRoomEntityFromRoom(Room room, String currentUserName) {
    RoomEntity roomEntity = new RoomEntity();
    roomEntity.setId(room.getRoomId());
    if (StringUtils.isNotBlank(room.getSpaceId())) {
      roomEntity.setSpaceId(room.getSpaceId());
      roomEntity.setDirectChat(false);
      roomEntity.setSpaceId(room.getSpaceId());
    } else if (StringUtils.isNotBlank(room.getFirstParticipant()) && StringUtils.isNotBlank(room.getSecondParticipant())) {
      roomEntity.setDirectChat(true);
      if (room.getFirstParticipant().equals(currentUserName)) {
        roomEntity.setDmMemberId(room.getSecondParticipant());
      } else {
        roomEntity.setDmMemberId(room.getFirstParticipant());
      }
    }
    // Update room entity with data from Meeds server
    updateRoomEntity(roomEntity, currentUserName);
    return roomEntity;
  }

  /**
   * Process the Matrix rooms and adds the missing information of users and spaces
   *
   * @param roomList the room list received from Matrix par sync API
   * @param currentUserName the current user
   * @return the roo List after processing
   */
  public RoomList processRooms(RoomList roomList, String currentUserName) {
    if (roomList == null || roomList.getRooms() == null) {
      throw new IllegalArgumentException("The room list Object is empty");
    }
    if (StringUtils.isBlank(currentUserName)) {
      throw new IllegalArgumentException("The username of the current user is mandatory");
    }

    for (RoomEntity room : roomList.getRooms()) {
      updateRoomEntity(room, currentUserName);
    }
    return roomList;
  }

  private RoomEntity updateRoomEntity(RoomEntity room, String currentUserName) {
    String roomId = room.getId();
    // Update room information
    if (room.getId().contains(":")) {
      roomId = room.getId().substring(0, room.getId().indexOf(":"));// remove server part
    }
    Room matrixRoom = matrixService.getById(roomId);
    if (matrixRoom != null) {
      if (StringUtils.isNotBlank(matrixRoom.getSpaceId())) {
        Space space = spaceService.getSpaceById(matrixRoom.getSpaceId());
        if (space != null) {
          room.setName(space.getDisplayName());
          room.setAvatarUrl(space.getAvatarUrl());
          room.setSpaceId(matrixRoom.getSpaceId());
          room.setPrettyName(space.getPrettyName());
          room.setDirectChat(false);
        }
      } else if (StringUtils.isNotBlank(matrixRoom.getFirstParticipant())
          && StringUtils.isNotBlank(matrixRoom.getSecondParticipant())) {
        Identity identity = null;
        if (matrixRoom.getFirstParticipant().equals(currentUserName)) {
          identity = identityManager.getOrCreateUserIdentity(matrixRoom.getSecondParticipant());
        } else if (matrixRoom.getSecondParticipant().equals(currentUserName)) {
          identity = identityManager.getOrCreateUserIdentity(matrixRoom.getFirstParticipant());
        }
        if (identity != null) {
          room.setName(identity.getProfile().getFullName());
          room.setAvatarUrl(identity.getProfile().getAvatarUrl());
          room.setUserId(identity.getRemoteId());
          room.setIdentityId(identity.getId());
          room.setDmMemberId(identity.getRemoteId());
          room.setExternal(identity.isExternal());
          room.setEnabledUser(identity.isEnable());
          room.setDeletedUser(identity.isDeleted());
        }
      }
    }

    // Get last message
    Message message = room.getLastMessage();
    if (message != null && StringUtils.isNotBlank(message.getSender())) {
      Identity identity = matrixService.findUserByMatrixId(matrixService.extractUserId(message.getSender()));
      if (identity != null) {
        String updatedContent;
        Locale locale = LocaleContextInfoUtils.getUserLocale(currentUserName);
        if (!identity.getRemoteId().equals(currentUserName)) {
          updatedContent = resourceBundleService.getSharedString(LAST_MESSAGE_PATTERN_STRING, locale)
                                                .replace("{0}", identity.getProfile().getFullName())
                                                .replace("{1}", message.getContent());
        } else {
          String you = resourceBundleService.getSharedString(YOU_STRING, locale);
          updatedContent = resourceBundleService.getSharedString(LAST_MESSAGE_PATTERN_STRING, locale)
                                                .replace("{0}", you)
                                                .replace("{1}", message.getContent());
        }
        message.setContent(updatedContent);
        room.setLastMessage(new Message(updatedContent, identity.getProfile().getFullName()));
      }
    }

    // Add update Date
    if (room.getUpdated() == 0) {
      room.setUpdated(System.currentTimeMillis());
    }
    return room;
  }

}
