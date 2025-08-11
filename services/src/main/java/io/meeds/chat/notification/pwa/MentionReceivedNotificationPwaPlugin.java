package io.meeds.chat.notification.pwa;

import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.PwaNotificationPlugin;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN;

public class MentionReceivedNotificationPwaPlugin implements PwaNotificationPlugin {

  private ResourceBundleService resourceBundleService;

  public MentionReceivedNotificationPwaPlugin(ResourceBundleService resourceBundleService) {
    this.resourceBundleService = resourceBundleService;
  }

  @Override
  public String getId() {
    return MATRIX_MENTION_RECEIVED_NOTIFICATION_PLUGIN;
  }

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();
    String roomType = notification.getValueOwnerParameter("MATRIX_ROOM_TYPE");
    String sender = notification.getValueOwnerParameter("MATRIX_SENDER_FULL_NAME");
    String roomName = notification.getValueOwnerParameter("MATRIX_ROOM_NAME");
    if ("SPACE".equals(roomType)) {
      notificationMessage.setTitle(resourceBundleService.getSharedString("Notification.body.space.MentionReceivedNotificationPlugin",
                                                                         localeConfig.getLocale())
                                                        .replace("{0}", sender)
                                                        .replace("{1}", roomName));
    } else {
      notificationMessage.setTitle(resourceBundleService.getSharedString("Notification.body.onetoone.MentionReceivedNotificationPlugin",
                                                                         localeConfig.getLocale())
                                                        .replace("{0}", sender));
    }
    notificationMessage.setBody(notification.getValueOwnerParameter("MATRIX_MESSAGE_CONTENT"));
    notificationMessage.setUrl(notification.getValueOwnerParameter("MATRIX_MESSAGE_URL"));
    return notificationMessage;
  }
}
