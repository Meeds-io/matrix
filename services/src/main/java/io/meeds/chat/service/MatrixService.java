package io.meeds.chat.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.meeds.chat.model.DirectMessagingRoom;
import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.model.SpaceRoom;
import io.meeds.chat.service.utils.MatrixHttpClient;
import io.meeds.chat.storage.MatrixRoomStorage;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.commons.ObjectAlreadyExistsException;
import org.exoplatform.commons.utils.PropertyManager;

import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.services.organization.User;
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
import java.util.List;

import static io.meeds.chat.service.utils.MatrixConstants.*;

@Service
public class MatrixService {

  private static final Log LOG = ExoLogger.getLogger(MatrixService.class);

  @Autowired
  private MatrixRoomStorage matrixRoomStorage;

  @Autowired
  private IdentityManager  identityManager;


  private String           matrixAccessToken;

  @PostConstruct
  public void init() {
    this.getMatrixAccessToken();
  }

  private String getMatrixAccessToken() {
    if(StringUtils.isBlank(this.matrixAccessToken)) {
      try {
        String jwtAccessToken = this.getJWTSessionToken(PropertyManager.getProperty(MATRIX_ADMIN_USERNAME));
        this.matrixAccessToken = MatrixHttpClient.getAdminAccessToken(jwtAccessToken);
      } catch (JsonException | IOException | InterruptedException e) {
        LOG.error("Could not get Matrix Access token for the administrator account !");
      }
    }
    return this.matrixAccessToken;
  }

  /**
   * Returns the ID of the room linked to a space
   * @param space
   * @return the roomId linked to the space
   */
  public String getRoomBySpace(Space space) {
    return matrixRoomStorage.getMatrixRoomBySpaceId(space.getId());
  }

  /**
   * Returns the ID of the room linked to a space
   * @param roomId the Matrix room ID
   * @return the roomId linked to the space
   */
  public Space getSpaceByRoomId(String roomId) {
    return matrixRoomStorage.getSpaceIdByMatrixRoomId(roomId);
  }

  /**
   * records the matrix ID of the room linked top the space
   *
   * @param space  the Space
   * @param roomId the ID of the matrix room
   * @return the room ID
   */
  public SpaceRoom createMatrixRoom(Space space, String roomId) {
    return matrixRoomStorage.saveRoomForSpace(space.getId(), roomId);
  }

  /**
   * Creates a room for predefined space
   * @param space the space
   * @return String representing the room id
   * @throws JsonException
   * @throws IOException
   * @throws InterruptedException
   */
  public String createMatrixRoomForSpace(Space space) throws Exception {
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

  /**
   * Saves a new user on Matrix
   * @param user the user to create on Matrix
   * @param isNew boolean if the user is new, then true
   * @return String the matrix user ID
   */
  public String saveUserAccount(User user, boolean isNew, boolean isEnableUserOperation) {
    return MatrixHttpClient.saveUserAccount(user, user.getUserName(), isNew, this.getMatrixAccessToken(), isEnableUserOperation);
  }

  public String uploadFileOnMatrix(String fileName, String mimeType, byte[] fileBytes) {
    return MatrixHttpClient.uploadFile(fileName, mimeType, fileBytes, this.getMatrixAccessToken());
  }

  public void updateUserAvatar(String userMatrixID, String userAvatarUrl) {
    MatrixHttpClient.updateUserAvatar(userMatrixID, userAvatarUrl, this.getMatrixAccessToken());
  }

  public void disableAccount(String matrixUsername) {
    MatrixHttpClient.disableAccount(matrixUsername, false, this.getMatrixAccessToken());
  }

  public boolean updateRoomDescription(String roomId, String description) {
    return MatrixHttpClient.updateRoomDescription(roomId, description, this.getMatrixAccessToken());
  }

  public void updateRoomAvatar(String roomId, String avatarURL) {
    MatrixHttpClient.updateRoomAvatar(roomId, avatarURL, this.getMatrixAccessToken());
  }

  public MatrixRoomPermissions getRoomSettings(String roomId) {
    return MatrixHttpClient.getRoomSettings(roomId, this.getMatrixAccessToken());
  }

  public boolean updateRoomSettings(String roomId, MatrixRoomPermissions matrixRoomPermissions) {
    return MatrixHttpClient.updateRoomSettings(roomId, matrixRoomPermissions, this.getMatrixAccessToken()) != null;
  }

  public void kickUserFromRoom(String roomId, String matrixIdOfUser, String message) {
    MatrixHttpClient.kickUserFromRoom(roomId, matrixIdOfUser, message, this.getMatrixAccessToken());
  }

  public void joinUserToRoom(String roomId, String matrixIdOfUser) {
    MatrixHttpClient.joinUserToRoom(roomId, matrixIdOfUser, this.getMatrixAccessToken());
  }

  public void renameRoom(String roomId, String spaceDisplayName) {
    MatrixHttpClient.renameRoom(roomId, spaceDisplayName, this.getMatrixAccessToken());
  }

  public void makeUserAdminInRoom(String matrixRoomId, String matrixIdOfUser) {
    MatrixHttpClient.makeUserAdminInRoom(matrixRoomId, matrixIdOfUser, this.getMatrixAccessToken());
  }

  public String createRoom(String teamDisplayName, String description) throws Exception {
    return MatrixHttpClient.createRoom(teamDisplayName, description, this.getMatrixAccessToken());
  }

  public long getAllLinkedRooms() {
    return matrixRoomStorage.getSpaceRoomCount();
  }

  public DirectMessagingRoom getDirectMessagingRoom(String firstParticipant, String secondParticipant) {
    return matrixRoomStorage.getDirectMessagingRoom(firstParticipant, secondParticipant);
  }

  public DirectMessagingRoom createDirectMessagingRoom(DirectMessagingRoom directMessagingRoom) throws ObjectAlreadyExistsException {
    String firstParticipant = directMessagingRoom.getFirstParticipant();
    String secondParticipant = directMessagingRoom.getSecondParticipant();
    if(StringUtils.isBlank(firstParticipant) || StringUtils.isBlank(secondParticipant)) {
      throw new IllegalArgumentException("The ids of the room participants should not be null");
    }
    if (identityManager.getOrCreateUserIdentity(directMessagingRoom.getFirstParticipant()) == null || identityManager.getOrCreateUserIdentity(directMessagingRoom.getSecondParticipant()) == null) {
      throw new IllegalArgumentException("The ids of the room participants should be valid user identity ids");
    }
    DirectMessagingRoom matrixRoom = matrixRoomStorage.getDirectMessagingRoom(firstParticipant, secondParticipant);
    if(matrixRoom == null) {
      return matrixRoomStorage.saveDirectMessagingRoom(directMessagingRoom.getFirstParticipant(), directMessagingRoom.getSecondParticipant(), directMessagingRoom.getRoomId());
    } else {
      throw new ObjectAlreadyExistsException("A direct messaging room is already created for the users %s and %s".formatted(firstParticipant, secondParticipant));
    }
  }

  public List<DirectMessagingRoom> getMatrixDMRoomsOfUser(String user) {
    return matrixRoomStorage.getMatrixDMRoomsOfUser(user);
  }
}
