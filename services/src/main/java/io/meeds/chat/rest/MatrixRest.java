package io.meeds.chat.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/matrix")
@Tag(name = "/matrix", description = "Manages Matrix integration")
public class MatrixRest implements ResourceContainer {

  private static final Log      LOG = ExoLogger.getLogger(MatrixRest.class.toString());

  @Autowired
  private SpaceService    spaceService;

  @Autowired
  private MatrixService   matrixService;


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
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User " + userName + " is not allowed to get information from space" + space.getPrettyName());
    }

    return matrixService.getRoomBySpace(space);
  }

  @GetMapping("linkroom")
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
        throw new ResponseStatusException(HttpStatus.CONFLICT, "space with group Id " + spaceGroupId + "has already a room with ID " + existingRoomId);
      }

      if (StringUtils.isBlank(roomId) && create) {
        roomId = matrixService.createMatrixRoomForSpace(space);
      }

      matrixService.createSpaceRoomAssociation(space, roomId);
      return true;
    } catch (Exception e) {
      LOG.error("Could not link space {} to Matrix room {}", spaceGroupId, roomId, e);
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not link space " + spaceGroupId + " to Matrix room " + roomId + " : " + e.getMessage());
    }
  }
}
