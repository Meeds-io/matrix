package io.meeds.chat.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.exoplatform.addons.matrix.services.MatrixConstants;
import org.exoplatform.addons.matrix.services.MatrixService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.web.filter.Filter;

import java.io.IOException;
import java.util.Arrays;

import static org.exoplatform.addons.matrix.services.MatrixConstants.MATRIX_JWT_COOKIE;

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
    Cookie[] cookies = httpRequest.getCookies();
    if(cookies != null) {
      if (httpRequest.getRemoteUser() != null && Arrays.stream(cookies).noneMatch(cookie -> MATRIX_JWT_COOKIE.equals(cookie.getName()))) {
        MatrixService matrixService = ExoContainerContext.getCurrentContainer().getComponentInstanceOfType(MatrixService.class);
        String sessionToken = matrixService.getJWTSessionToken(httpRequest.getRemoteUser());
        Cookie cookie = new Cookie(MATRIX_JWT_COOKIE, sessionToken);
        cookie.setPath("/");
        cookie.setMaxAge(604800); // 7 days in seconds
        cookie.setHttpOnly(false);
        cookie.setSecure(request.isSecure());
        httpResponse.addCookie(cookie);
      } else if (StringUtils.isBlank(httpRequest.getRemoteUser())) {
        Cookie oldCookie = Arrays.stream(cookies).filter(cookie -> MATRIX_JWT_COOKIE.equals(cookie.getName())).findFirst().orElse(null);
        if(oldCookie != null) {
          oldCookie.setValue("");
          oldCookie.setMaxAge(0);
          oldCookie.setPath("/");
          oldCookie.setHttpOnly(false);
          oldCookie.setSecure(request.isSecure());
          httpResponse.addCookie(oldCookie);
        }
      }
    }

    chain.doFilter(request, response);
  }

}

