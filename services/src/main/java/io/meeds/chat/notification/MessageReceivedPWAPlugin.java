package io.meeds.chat.notification;

import io.meeds.portal.permlink.model.PermanentLinkObject;
import io.meeds.portal.permlink.service.PermanentLinkService;
import io.meeds.pwa.model.PwaNotificationMessage;
import io.meeds.pwa.plugin.PwaNotificationPlugin;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.services.resources.LocaleConfig;
import org.exoplatform.services.resources.ResourceBundleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_MESSAGE_RECEIVED_NOTIFICATION_PLUGIN;
import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_ROOM_ID;

@Component
public class MessageReceivedPWAPlugin implements PwaNotificationPlugin {

  private ResourceBundleService resourceBundleService;

  private PermanentLinkService  permanentLinkService;

  private static final String   TITLE_LABEL_KEY = "matrix.message.received.pwa.notification.title";

  public MessageReceivedPWAPlugin(ResourceBundleService resourceBundleService, PermanentLinkService permanentLinkService) {
    this.resourceBundleService = resourceBundleService;
    this.permanentLinkService = permanentLinkService;
  }

  @Override
  public PwaNotificationMessage process(NotificationInfo notification, LocaleConfig localeConfig) {
    PwaNotificationMessage notificationMessage = new PwaNotificationMessage();

    String title = resourceBundleService.getSharedString(TITLE_LABEL_KEY, localeConfig.getLocale())
                                        .replace("{0}", notification.getValueOwnerParameter("MATRIX_ROOM_UNREAD_COUNT"))
                                        .replace("{1}", notification.getValueOwnerParameter("MATRIX_ROOM_ID"));

    notificationMessage.setTitle(title);
    notificationMessage.setUrl(permanentLinkService.getPermanentLink(new PermanentLinkObject("matrixChatRoom",
                                                                                             notification.getValueOwnerParameter("MATRIX_ROOM_ID"))));
    return notificationMessage;

  }

  @Override
  public String getId() {
    return MATRIX_MESSAGE_RECEIVED_NOTIFICATION_PLUGIN;
  }
}
