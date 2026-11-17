/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2026 Meeds Association contact@meeds.io
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package io.meeds.chat.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import org.exoplatform.commons.api.notification.service.template.TemplateContext;
import org.exoplatform.commons.notification.mail.MailPalette;
import org.exoplatform.portal.branding.MailDocumentShell;

import groovy.text.GStringTemplateEngine;

/**
 * The mention mail, rendered as the mail channel renders it (through the
 * template engine), in the palette bound to its recipient.
 */
class MentionReceivedMailTemplateTest {

  private static final Path   TEMPLATE        =
                                       Path.of("../webapp/src/main/webapp/WEB-INF/conf/matrix/templates/notification/mail/MentionReceivedMailPlugin.gtmpl");

  private static final String DARK_SURFACE    = "#1E2328";

  private static final String DARK_TEXT       = "#E6E6E6";

  private static final String DARK_BORDER     = "#3A4148";

  private static final String DARK_BACKGROUND = "#14181B";

  private static final String DARK_LINK       = "#5FA9AC";

  @Test
  void aDarkRecipientGetsTheDarkPalette() throws Exception {
    Map<String, String> palette = new HashMap<>(MailDocumentShell.LIGHT_DEFAULTS);
    palette.put(MailDocumentShell.SURFACE, DARK_SURFACE);
    palette.put(MailDocumentShell.TEXT, DARK_TEXT);
    palette.put(MailDocumentShell.BORDER, DARK_BORDER);
    palette.put(MailDocumentShell.BACKGROUND, DARK_BACKGROUND);
    palette.put(MailDocumentShell.LINK, DARK_LINK);
    palette.put(MailPalette.MODE, "dark");

    String html = render(palette);

    assertTrue(html.contains("bgcolor=\"" + DARK_SURFACE + "\""), html);
    assertTrue(html.contains("background-color: " + DARK_BACKGROUND), html);
    assertTrue(html.contains("color: " + DARK_TEXT + "\" class=\"mail-text\">MESSAGE_CONTENT_VALUE"), html);
    assertTrue(html.contains("color: " + DARK_LINK), html);
    assertTrue(html.contains("class=\"mail-surface mail-text\""), html);
    assertFalse(Stream.of("#ffffff", "#333333", "#d8d8d8", "#f1f1f1", "#999999", "#c7c7c7", "#f9f9f9", "#2f5e92")
                      .anyMatch(html.toLowerCase()::contains),
                html);
  }

  @Test
  void aContextWithoutPaletteRendersInTheLightDefaults() throws Exception {
    String html = render(null);

    assertTrue(html.contains("bgcolor=\"" + MailDocumentShell.LIGHT_DEFAULTS.get(MailDocumentShell.SURFACE) + "\""), html);
    assertFalse(html.contains("null"), html);
  }

  private static String render(Map<String, String> palette) throws Exception {
    StringWriter writer = new StringWriter();
    TemplateContext ctx = new TemplateContext();
    ctx.put("_writer", writer);
    ctx.put("_templateContext", ctx);
    ctx.put("_ctx", new TemplateHelper());
    ctx.put("ROOM_TYPE", "SPACE");
    ctx.put("MESSAGE_CONTENT", "MESSAGE_CONTENT_VALUE");
    ctx.put("MESSAGE_LINK", "https://exo.example.com/chat");
    ctx.put("PROFILE_URL", "https://exo.example.com/profile");
    ctx.put("AVATAR", "https://exo.example.com/avatar");
    ctx.put("USER", "Anne");
    ctx.put("ROOM", "Room");
    ctx.put("FIRSTNAME", "Ben");
    if (palette != null) {
      ctx.put(MailPalette.CONTEXT_KEY, palette);
    }
    String source = Files.readString(TEMPLATE, StandardCharsets.UTF_8);
    new GStringTemplateEngine().createTemplate(source).make(ctx).writeTo(writer);
    return writer.toString();
  }

  /**
   * What the template calls on its {@code _ctx}: labels are echoed back and the shared
   * header and footer are not part of what is rendered here.
   */
  public static class TemplateHelper {
    public String appRes(String key, Object... args) {
      return key + " " + String.join(" ", java.util.Arrays.stream(args).map(String::valueOf).toList());
    }

    public String escapeHTML(String text) {
      return text;
    }

    public void include(String path, Object context) {
      // the shared header and footer have their own tests (commons)
    }
  }
}
