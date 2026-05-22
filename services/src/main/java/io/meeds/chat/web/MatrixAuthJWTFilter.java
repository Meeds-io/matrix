/**
 * This file is part of the Meeds project (https://meeds.io/).
 *
 * Copyright (C) 2020 - 2025 Meeds Association contact@meeds.io
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
package io.meeds.chat.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import io.meeds.chat.service.MatrixService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.web.filter.Filter;

import java.io.IOException;
import java.util.Arrays;

import static io.meeds.chat.service.utils.MatrixConstants.MATRIX_JWT_COOKIE;

public class MatrixAuthJWTFilter implements Filter {

  public MatrixAuthJWTFilter() {
  }

  /**
   * Do filter.
   *
   * @param request the request
   * @param response the response
   * @param chain the chain
   * @throws IOException Signals that an I/O exception has occurred.
   * @throws ServletException the servlet exception
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

    HttpServletRequest httpRequest = (HttpServletRequest) request;
    HttpServletResponse httpResponse = (HttpServletResponse) response;
    MatrixService matrixService = ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(MatrixService.class);
    if (matrixService.isServiceEnabled()) {
      Cookie[] cookies = httpRequest.getCookies();
      if (cookies != null) {
        if (httpRequest.getRemoteUser() != null
            && Arrays.stream(cookies).noneMatch(cookie -> MATRIX_JWT_COOKIE.equals(cookie.getName()))) {
          String userNameOnMatrix = matrixService.getMatrixIdForUser(httpRequest.getRemoteUser());
          if (StringUtils.isNotBlank(userNameOnMatrix)) {
            String sessionToken = matrixService.getJWTSessionToken(userNameOnMatrix);
            Cookie cookie = new Cookie(MATRIX_JWT_COOKIE, sessionToken);
            cookie.setPath("/");
            cookie.setMaxAge(604800); // 7 days in seconds
            cookie.setHttpOnly(false);
            cookie.setSecure(request.isSecure());
            httpResponse.addCookie(cookie);
          }
        } else if (StringUtils.isBlank(httpRequest.getRemoteUser())) {
          Cookie oldCookie = Arrays.stream(cookies)
                                   .filter(cookie -> MATRIX_JWT_COOKIE.equals(cookie.getName()))
                                   .findFirst()
                                   .orElse(null);
          if (oldCookie != null) {
            oldCookie.setValue("");
            oldCookie.setMaxAge(0);
            oldCookie.setPath("/");
            oldCookie.setHttpOnly(false);
            oldCookie.setSecure(request.isSecure());
            httpResponse.addCookie(oldCookie);
          }
        }
      }
    }
    chain.doFilter(request, response);
  }

}
