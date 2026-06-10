package io.meeds.chat.rest;

import io.meeds.chat.service.model.ChatSettingsEntity;
import io.meeds.chat.service.model.ChatSettings;
import io.meeds.chat.service.MatrixService;
import io.meeds.social.space.template.service.SpaceTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.exoplatform.services.rest.resource.ResourceContainer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/chatAdministration")
@Tag(name = "/chatAdministration", description = "Manages Chat administration")
public class ChatAdministrationRest implements ResourceContainer {
  @Autowired
  private MatrixService        matrixService;

  @Autowired
  private SpaceTemplateService spaceTemplateService;

  @GetMapping(value = "settings", produces = MediaType.APPLICATION_JSON_VALUE)
  @Secured("administrators")
  @Operation(summary = "Check the Chat settings", method = "GET", description = "Check the Chat settings")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "503", description = "Service unavailable"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ChatSettingsEntity loadChatSettings(HttpServletRequest request) {
    if (!matrixService.isServiceAvailable()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat service is unavailable");
    }
    return matrixService.loadChatSettings(request.getRemoteUser(), request.getLocale());
  }

  @PostMapping(value = "settings", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @Secured("administrators")
  @Operation(summary = "Enable the Chat feature", method = "GET", description = "Enable the Chat feature")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "503", description = "Service unavailable"),
      @ApiResponse(responseCode = "500", description = "Internal server error") })
  public ChatSettingsEntity updateChatSettings(HttpServletRequest request,
                                               @Parameter(description = "Settings of the Chat")
                                               @RequestBody
                                               ChatSettingsEntity chatSettingsEntity) {
    if (!matrixService.isServiceAvailable()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat service is unavailable");
    }
    this.matrixService.saveChatSettings(new ChatSettings(chatSettingsEntity.isChatEnabled(),
                                                         chatSettingsEntity.isPrivateRoomsEnabled(),
                                                         chatSettingsEntity.isSpaceRoomsEnabled()));
    return this.matrixService.loadChatSettings(request.getRemoteUser(), request.getLocale());
  }
}
