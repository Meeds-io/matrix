import io.meeds.chat.model.MatrixRoomPermissions;
import io.meeds.chat.service.utils.MatrixHttpClient;
import org.exoplatform.services.organization.User;
import org.exoplatform.services.organization.idm.UserImpl;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.ws.frameworks.json.impl.JsonException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Date;
import java.util.Random;

import static org.junit.Assert.*;

/**
 * This test class requires an available matrix server You can configure the
 * connection inside the setUp function
 */

public class MatrixUtilsTest {

  private String   access_token     = "syt_ZXhv_BDVYgRelgkgjGduvVCyz_1iXdtf";

  MatrixHttpClient matrixHttpClient = new MatrixHttpClient();

  @Before
  public void setUp() {
    System.setProperty("meeds.matrix.server.url", "http://localhost:8008");
    System.setProperty("meeds.matrix.shared_secret_registration", "4fzT.7xvkyp1EA-*bX#fzpVgOc_cb0y9z6*uOCUht1DO5ksad8");
    System.setProperty("meeds.matrix.server.name", "matrix.exo.tn");
  }

  public void testCreateUserAccount() {
    String randomKey = String.valueOf(Math.round(Math.random() * 100));
    User user = new UserImpl("testUser" + randomKey);
    user.setEmail("test@exo.com");
    user.setFirstName("test " + randomKey);
    user.setLastName("User");
    matrixHttpClient.createUserAccount(user, access_token);
  }

  public void testSaveUserAccount() {
    String randomKey = String.valueOf(Math.round(Math.random() * 100));
    Identity user = new Identity("testUser" + randomKey);
    Profile profile = new Profile();
    profile.setProperty(Profile.EMAIL, "test@exo.com");
    profile.setProperty(Profile.FIRST_NAME, "test " + randomKey);
    profile.setProperty(Profile.LAST_NAME, "User");
    matrixHttpClient.saveUserAccount(user, user.getRemoteId(), true, access_token);
  }

  public void testDisableUserAccount() {
    String randomKey = String.valueOf(Math.round(Math.random() * 10000));
    Identity user = new Identity("testUser" + randomKey);
    Profile profile = new Profile();
    profile.setProperty(Profile.EMAIL, "test@exo.com");
    profile.setProperty(Profile.FIRST_NAME, "test " + randomKey);
    profile.setProperty(Profile.LAST_NAME, "User");
    String username = matrixHttpClient.saveUserAccount(user, user.getRemoteId(), true, access_token);
    matrixHttpClient.disableAccount(username, false, access_token);
  }

  public void testRenameSpace() {
    String roomId = null;
    try {
      roomId = matrixHttpClient.createRoom("new room", "new room description", access_token);
    } catch (Exception e) {
      // nothing
    }
    assertNotNull(matrixHttpClient.renameRoom(roomId, "new room renamed" + new Date().getTime(), access_token));
  }

  public void testInviteUser() throws Exception {
    String randomKey = String.valueOf(Math.round(Math.random() * 10000));
    Identity user = new Identity("testUser" + randomKey);
    Profile profile = new Profile();
    profile.setProperty(Profile.EMAIL, "test@exo.com");
    profile.setProperty(Profile.FIRST_NAME, "test " + randomKey);
    profile.setProperty(Profile.LAST_NAME, "User");
    String invitee = matrixHttpClient.saveUserAccount(user, user.getRemoteId(), true, access_token);
    String roomId = matrixHttpClient.createRoom("Football game", "Description of Football team", access_token);
    matrixHttpClient.inviteUserToRoom(roomId, invitee, "Welcome to Football game room !", access_token);
  }

  /*
   * This test requires that we have already a user member of a room to kick him
   * out
   */
  public void testKickUser() {
    String roomId = "!rYdqPkQhIzNWyVPDFX";
    matrixHttpClient.kickUserFromRoom(roomId, "@testuser1:matrix.exo.tn", "Talking too much!", access_token);
  }

  public void updateRoomSettings() throws JsonException, IOException, InterruptedException {
    String roomId = "!rYdqPkQhIzNWyVPDFX";
    MatrixRoomPermissions settings = matrixHttpClient.getRoomSettings(roomId, access_token);
    settings.setInvite("0");
    String updateEventId = matrixHttpClient.updateRoomSettings(roomId, settings, access_token);
  }

  public void updateRoomAvatar() {
    try {
      String roomId = "!HaTqHwWINwoSoIGfZx";
      byte[] resource = getClass().getClassLoader().getResourceAsStream("meeds.png").readAllBytes();
      String imageStored = matrixHttpClient.uploadFile("image.png", "image/png", resource, access_token);
      boolean success = matrixHttpClient.updateRoomAvatar(roomId, imageStored, access_token);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public void updateUserAvatar() {
    try {
      String roomId = "@root:matrix.exo.tn";
      int randomInt = new Random().nextInt(3);
      byte[] resource = getClass().getClassLoader().getResourceAsStream("avatar" + randomInt + ".png").readAllBytes();
      String imageStored = matrixHttpClient.uploadFile("image.png", "image/png", resource, access_token);
      assertTrue(matrixHttpClient.updateUserAvatar(roomId, imageStored, access_token));
    } catch (IOException e) {
      fail();
    }
  }

  @Test
  public void testCleanMatrixUsername() {
    String[] usernames = new String[] { "Samueâl", "fre@d", "Shazia", "gorkef/",
        "²&é\"'(-è_çà)=²1234567890°+'azertyuiopqsdfghjklmù*^$wxcvbn,;:!?./§%µ¨£<>²&~#{[|`\\^@]}" };
    for (String username : usernames) {
      String result = matrixHttpClient.cleanMatrixUsername(username);
      assertNotNull(result);
    }
  }

  public void testDeleteSpace() throws Exception {
    long currentTime = System.currentTimeMillis();
    String matrixRoomId =
                        matrixHttpClient.createRoom("test space" + currentTime, "test description " + currentTime, access_token);
    matrixHttpClient.deleteRoom(matrixRoomId, access_token);
  }

  public void testGetUserPresence() {
    String accessToken = "syt_cm9vdA_MgLOkjIhSlwzrIpMElUo_1kSAtC";
    String userIdOnMatrix = "@root:matrix.exo.tn";
    try {
      String status = matrixHttpClient.getUserPresence(userIdOnMatrix, accessToken);
      assertNotNull(status);
    } catch (Exception e) {
      fail();
    }
  }

  public void testSetUserPresence() {
    String accessToken = "syt_cm9vdA_MgLOkjIhSlwzrIpMElUo_1kSAtC";
    String userIdOnMatrix = "@root:matrix.exo.tn";
    String presence = "online";
    String statusMessage = "Hello I am online";
    try {
      String status = matrixHttpClient.setUserPresence(userIdOnMatrix, presence, statusMessage, accessToken);
      assertNotNull(status);
    } catch (Exception e) {
      fail();
    }
  }
}
