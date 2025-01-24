package io.meeds.chat.rest;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.meeds.chat.model.DirectMessagingRoom;
import io.meeds.chat.model.Room;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.PluginKey;
import org.exoplatform.commons.api.notification.service.storage.NotificationService;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.commons.utils.PropertyManager;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.ws.frameworks.json.impl.JsonGeneratorImpl;
import org.exoplatform.ws.frameworks.json.value.JsonValue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.meeds.chat.service.utils.MatrixConstants.*;

@RestController
@RequestMapping("/matrix")
@Tag(name = "/matrix", description = "Manages Matrix integration")
public class MatrixRest implements ResourceContainer {

  private static final Log    LOG = ExoLogger.getLogger(MatrixRest.class.toString());

  @Autowired
  private SpaceService        spaceService;

  @Autowired
  private MatrixService       matrixService;

  @Autowired
  private IdentityManager     identityManager;

  @Autowired
  private NotificationService notificationService;

  @GetMapping
  @Secured("users")
  @Operation(summary = "Get the matrix room bound to the current space", method = "GET", description = "Get the id of the matrix room bound to the current space")
  @ApiResponses(value = { @ApiResponse(responseCode = "2rest00", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public String getMatrixRoomBySpaceId(@Parameter(description = "The space Id")
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

    return matrixService.getRoomBySpace(space);
  }

  @GetMapping("dmRoom")
  @Secured("users")
  @Operation(summary = "Get the matrix room used for direct messaging between provided users", method = "GET", description = "Get the matrix room used for direct messaging between provided users")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "400", description = "Invalid query input"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public DirectMessagingRoom getDirectMessagingRoom(@Parameter(description = "The first participant")
  @RequestParam(name = "firstParticipant")
  String firstParticipant,
                                                    @Parameter(description = "The second participant")
                                                    @RequestParam(name = "secondParticipant")
                                                    String secondParticipant) {
    if (StringUtils.isBlank(firstParticipant) || StringUtils.isBlank(secondParticipant)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the ids of the participants should not be null");
    }
    DirectMessagingRoom directMessagingRoom = matrixService.getDirectMessagingRoom(firstParticipant, secondParticipant);
    if (directMessagingRoom != null) {
      return directMessagingRoom;
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
  public DirectMessagingRoom getDirectMessagingRoom(@RequestBody(description = "Matrix object to create", required = true)
  @org.springframework.web.bind.annotation.RequestBody
  DirectMessagingRoom directMessagingRoom) {
    if (StringUtils.isBlank(directMessagingRoom.getFirstParticipant())
        || StringUtils.isBlank(directMessagingRoom.getSecondParticipant())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "the ids of the participants should not be null");
    }
    try {
      return matrixService.createDirectMessagingRoom(directMessagingRoom);
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

      String existingRoomId = matrixService.getRoomBySpace(space);
      if (StringUtils.isNotBlank(existingRoomId)) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                          "space with group Id " + spaceGroupId + "has already a room with ID " + existingRoomId);
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
    List<DirectMessagingRoom> rooms = matrixService.getMatrixDMRoomsOfUser(user);
    for (DirectMessagingRoom dmRoom : rooms) {
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
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    } else {
      if (StringUtils.isNotBlank(room.getSpaceId())) {
        Space space = spaceService.getSpaceById(room.getSpaceId());
        return identityManager.getOrCreateSpaceIdentity(space.getPrettyName()).getId();
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

}
