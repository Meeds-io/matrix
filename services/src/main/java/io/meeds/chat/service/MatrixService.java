package io.meeds.chat.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.meeds.chat.model.SpaceRoom;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.chat.storage.SpaceRoomStorage;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.PropertyManager;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

import static io.meeds.chat.service.utils.MatrixConstants.*;

@Service
public class MatrixService {

  private static final Log LOG = ExoLogger.getLogger(MatrixService.class);

  @Autowired
  private SpaceRoomStorage spaceRoomStorage;

  @Autowired
  private IdentityManager  identityManager;

  @Getter
  private String           matrixAccessToken;

  @PostConstruct
  public void init() {
    try {
      String jwtAccessToken = this.getJWTSessionToken(PropertyManager.getProperty(MATRIX_ADMIN_USERNAME));
      matrixAccessToken = MatrixHttpClient.getAdminAccessToken(jwtAccessToken);
    } catch (JsonException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Returns the ID of the room linked to a space
   * @param space
   * @return the roomId linked to the space
   */
  public String getRoomBySpace(Space space) throws ObjectNotFoundException {
    return spaceRoomStorage.getMatrixRoomBySpaceId(space.getId());
  }

  /**
   * Returns the ID of the room linked to a space
   * @param roomId the Matrix room ID
   * @return the roomId linked to the space
   */
  public Space getSpaceByRoomId(String roomId) {
    return spaceRoomStorage.getSpaceIdByMatrixRoomId(roomId);
  }

  /**
   * records the matrix ID of the room linked top the space
   *
   * @param space  the Space
   * @param roomId the ID of the matrix room
   * @return the room ID
   */
  public SpaceRoom createSpaceRoomAssociation(Space space, String roomId) {
    return spaceRoomStorage.createSpaceRoomAssociation(space.getId(), roomId);
  }

  /**
   * Creates a room for predefined space
   * @param space the space
   * @return String representing the room id
   * @throws JsonException
   * @throws IOException
   * @throws InterruptedException
   */
  public String createMatrixRoomForSpace(Space space) throws JsonException, IOException, InterruptedException {
    String teamDisplayName = space.getDisplayName();
    String description = space.getDescription() != null ? space.getDescription() : "";
    return MatrixHttpClient.createRoom(teamDisplayName, description, getMatrixAccessToken());
  }

  /**
   * Get the matrix ID of a defined user
   * @param userName of the user
   * @return the matrix ID
   */
  public String getMatrixIdForUser(String userName) {
    Identity newMember = identityManager.getOrCreateUserIdentity(userName);
    Profile newMemberProfile = newMember.getProfile();
    if(StringUtils.isNotBlank((String) newMemberProfile.getProperty(USER_MATRIX_ID))) {
      return newMemberProfile.getProperty(USER_MATRIX_ID).toString();
    }
    return null;
  }

  /**
   * Returns the JWT for user authentication
   * @param userNameOnMatrix the username of the current user
   * @return String
   */
  public String getJWTSessionToken(String userNameOnMatrix) {
    return Jwts.builder()
            .setSubject(userNameOnMatrix)
            .signWith(Keys.hmacShaKeyFor(PropertyManager.getProperty(MATRIX_JWT_SECRET).getBytes()))
            .setExpiration(Date.from(LocalDate.now().plusDays(7).atStartOfDay(ZoneId.systemDefault()).toInstant()))
            .compact();

  }
}
