package io.meeds.chat.notification;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.plugin.BaseNotificationPlugin;
import org.exoplatform.container.xml.InitParams;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_ROOM_UNREAD_COUNT;

public class MessageReceivedNotificationPlugin extends BaseNotificationPlugin {
  public MessageReceivedNotificationPlugin(InitParams initParams) {
    super(initParams);
  }

  @Override
  public String getId() {
    return MATRIX_MESSAGE_RECEIVED_NOTIFICATION_PLUGIN;
  }

  @Override
  public boolean isValid(NotificationContext notificationContext) {
    return true;
  }

  @Override
  protected NotificationInfo makeNotification(NotificationContext notificationContext) {
    String roomId = notificationContext.value(MATRIX_ROOM_ID);
    String userName = notificationContext.value(MATRIX_ROOM_MEMBER);
    Integer unreadMessagesCount = notificationContext.value(MATRIX_ROOM_UNREAD_COUNT);
    return NotificationInfo.instance()
                           .to(userName)
                           .with("ROOM_ID", roomId)
                           .with("UNREAD_MESSAGES_COUNT", String.valueOf(unreadMessagesCount))
                           .key(getId())
                           .end();
  }
}
