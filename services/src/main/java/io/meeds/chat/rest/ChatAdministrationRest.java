package io.meeds.chat.rest;

import io.meeds.chat.rest.model.RoomEntity;
import io.meeds.chat.service.MatrixService;
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
  private MatrixService matrixService;

  @GetMapping(value = "isChatEnabled", produces = MediaType.APPLICATION_JSON_VALUE)
  @Secured("administrators")
  @Operation(summary = "Check if the Chat feature is enabled or not", method = "GET", description = "Check if the Chat feature is enabled or not")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "503", description = "Service unavailable"), })
  public String isChatFeatureEnabled(HttpServletRequest request) {
    if (!matrixService.isServiceAvailable()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat service is unavailable");
    }
    return """
        {
          "enabled" : %s
        }
        """.formatted(matrixService.isChatFeatureEnabled());
  }

  @PostMapping(value = "enableChat", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  @Secured("administrators")
  @Operation(summary = "Enable the Chat feature", method = "GET", description = "Enable the Chat feature")
  @ApiResponses(value = { @ApiResponse(responseCode = "200", description = "Request fulfilled"),
      @ApiResponse(responseCode = "503", description = "Service unavailable"), })
  public String enableChatFeature(@Parameter(description = "Status of the Chat")
                                  @RequestParam("enabled")
                                  boolean enabled) {
    if (!matrixService.isServiceAvailable()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chat service is unavailable");
    }
    this.matrixService.setChatFeatureEnabled(enabled);
    return """
        {
          "enabled" : %s
        }
        """.formatted(matrixService.isChatFeatureEnabled());
  }
}
