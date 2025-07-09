package io.meeds.chat.notification;

import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.container.xml.InitParams;
import org.junit.jupiter.api.Test;

import static io.meeds.chat.service.utils.MatrixConstants.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MessageReceivedNotificationPluginTest {

  @Test
  void makeNotification() {
    InitParams initParams = new InitParams();
    NotificationContext ctx = mock(NotificationContext.class);
    when(ctx.value(MATRIX_ROOM_ID)).thenReturn("IdentifierOfTheRoom");
    when(ctx.value(MATRIX_ROOM_MEMBER)).thenReturn("user");
    when(ctx.value(MATRIX_ROOM_UNREAD_COUNT)).thenReturn(1);

    MessageReceivedNotificationPlugin messageReceivedNotificationPlugin = new MessageReceivedNotificationPlugin(initParams);
    NotificationInfo notificationInfo = messageReceivedNotificationPlugin.buildNotification(ctx);
    assertNotNull(notificationInfo);
    assertEquals("user", notificationInfo.getTo());
    assertEquals("1", notificationInfo.getValueOwnerParameter("UNREAD_MESSAGES_COUNT"));
  }
}
