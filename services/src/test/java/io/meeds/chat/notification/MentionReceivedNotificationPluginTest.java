package io.meeds.chat.notification;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.container.xml.InitParams;
import org.junit.jupiter.api.Test;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MentionReceivedNotificationPluginTest {

  @Test
  void makeNotification() {
    InitParams initParams = new InitParams();
    NotificationContext ctx = mock(NotificationContext.class);
    when(ctx.value(MATRIX_ROOM_ID)).thenReturn("IdentifierOfTheRoom");
    when(ctx.value(MATRIX_ROOM_MEMBER)).thenReturn("user");

    MentionReceivedNotificationPlugin mentionReceivedNotificationPlugin = new MentionReceivedNotificationPlugin(initParams);
    NotificationInfo notificationInfo = mentionReceivedNotificationPlugin.buildNotification(ctx);
    assertNotNull(notificationInfo);
    assertEquals("user", notificationInfo.getTo());
  }
}
