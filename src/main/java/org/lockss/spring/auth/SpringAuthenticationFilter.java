/*

Copyright (c) 2000-2018 Board of Trustees of Leland Stanford Jr. University,
all rights reserved.

Redistribution and use in source and binary forms, with or without modification,
are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation and/or
other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its contributors
may be used to endorse or promote products derived from this software without
specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */
package org.lockss.spring.auth;

import java.io.IOException;
import java.security.AccessControlException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;

import org.lockss.account.UserAccount;
import org.lockss.app.LockssDaemon;
import org.lockss.util.Logger;

/**
 * Default LOCKSS custom Spring authentication filter.
 */
public class SpringAuthenticationFilter extends GenericFilterBean {

  public static final String noAuthorizationHeader = "No authorization header.";
  public static final String noCredentials = "No userid/password credentials.";
  public static final String badCredentials =
      "Bad userid/password credentials.";
  public static final String noUser = "User not found.";
  private final static Logger log =
      Logger.getLogger(SpringAuthenticationFilter.class);

  /**
   * Authentication filter.
   *
   * @param request A ServletRequest with the incoming servlet request.
   * @param response A ServletResponse with the outgoing servlet response.
   * @param chain A FilterChain with the chain where this filter is set.
   * @throws IOException if there are problems.
   * @throws ServletException if there are problems.
   */
  @Override
  public void doFilter(ServletRequest request, ServletResponse response,
      FilterChain chain) throws IOException, ServletException {
    final String DEBUG_HEADER = "doFilter(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "Invoked.");
    }

    HttpServletRequest httpRequest = (HttpServletRequest) request;

    if (log.isDebug3()) {
      StringBuffer originalUrl = httpRequest.getRequestURL();

      if (httpRequest.getQueryString() != null) {
        originalUrl.append("?").append(httpRequest.getQueryString());
      }

      log.debug3(DEBUG_HEADER + "originalUrl = " + originalUrl);
    }

    HttpServletResponse httpResponse = (HttpServletResponse) response;

    try {
      // Check whether authentication is not required at all.
      if (!AuthUtil.isAuthenticationOn()) {
        // Yes: Continue normally.
        if (log.isDebug3()) {
          log.debug3(DEBUG_HEADER + "Authorized (like everybody else).");
        }

        SecurityContextHolder.getContext().setAuthentication(
            getUnauthenticatedUserToken());

        // Continue the chain.
        chain.doFilter(request, response);
        return;
      }
    } catch (AccessControlException ace) {
      // Report the configuration problem.
      log.error(ace.getMessage());

      SecurityContextHolder.clearContext();
      httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN,
          ace.getMessage());
      return;
    }

    // No: Check whether this request is available to everybody.
    if (isWorldReachable(httpRequest)) {
      // Yes: Continue normally.
      if (log.isDebug3()) {
        log.debug3(DEBUG_HEADER + "Authenticated (like everybody else).");
      }

      SecurityContextHolder.getContext().setAuthentication(
          getUnauthenticatedUserToken());

      // Continue the chain.
      chain.doFilter(request, response);
      return;
    }

    // No: Get the authorization header.
    String authorizationHeader = httpRequest.getHeader("authorization");
    if (log.isDebug3()) {
      log.debug3(DEBUG_HEADER + "authorizationHeader = " + authorizationHeader);
    }

    // Check whether no authorization header was found.
    if (authorizationHeader == null) {
      // Yes: Report the problem.
      log.info(noAuthorizationHeader);

      SecurityContextHolder.clearContext();
      httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED,
          noAuthorizationHeader);
      return;
    }

    // No: Get the user credentials in the authorization header.
    String[] credentials =
        AuthUtil.decodeBasicAuthorizationHeader(authorizationHeader);

    // Check whether no credentials were found.
    if (credentials == null) {
      // Yes: Report the problem.
      log.info(noCredentials);

      SecurityContextHolder.clearContext();
      httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED,
          noCredentials);
      return;
    }

    // No: Check whether the found credentials are not what was expected.
    if (credentials.length != 2) {
      // Yes: Report the problem.
      log.info(badCredentials);
      log.info("bad credentials = " + Arrays.toString(credentials));

      SecurityContextHolder.clearContext();
      httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED,
          badCredentials);
      return;
    }

    if (log.isDebug3()) {
      log.debug3(DEBUG_HEADER + "credentials[0] = " + credentials[0]);
    }

    // No: Get the user account.
    UserAccount userAccount = null;

    try {
      userAccount = LockssDaemon.getLockssDaemon().getAccountManager()
          .getUser(credentials[0]);
    } catch (Exception e) {
      log.error("credentials[0] = " + credentials[0]);
      log.error("credentials[1] = " + credentials[1]);
      log.error("LockssDaemon.getLockssDaemon().getAccountManager()."
          + "getUser(credentials[0])", e);
    }

    // Check whether no user was found.
    if (userAccount == null) {
      // Yes: Report the problem.
      log.info(noUser);

      SecurityContextHolder.clearContext();
      httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED,
          badCredentials);
      return;
    }

    if (log.isDebug3()) {
      log.debug3(DEBUG_HEADER
          + "userAccount.getName() = " + userAccount.getName());
    }

    // No: Verify the user credentials.
    boolean goodCredentials = userAccount.check(credentials[1]);
    if (log.isDebug3()) {
      log.debug3(DEBUG_HEADER + "goodCredentials = " + goodCredentials);
    }

    // Check whether the user credentials are not good.
    if (!goodCredentials) {
      // Yes: Report the problem.
      log.info(badCredentials);
      log.info("userAccount.getName() = " + userAccount.getName());
      log.info("bad credentials = " + Arrays.toString(credentials));

      SecurityContextHolder.clearContext();
      httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED,
          badCredentials);
      return;
    }

    // No: Get the user roles.
    Collection<GrantedAuthority> roles = new HashSet<GrantedAuthority>();

    for (Object role : userAccount.getRoleSet()) {
      if (log.isDebug3()) {
        log.debug3(DEBUG_HEADER + "role = " + role);
      }
      roles.add(new SimpleGrantedAuthority((String) role));
    }

    if (log.isDebug3()) {
      log.debug3(DEBUG_HEADER + "roles = " + roles);
    }

    // Create the completed authentication details.
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(credentials[0],
            credentials[1], roles);
    if (log.isDebug3()) {
      log.debug3(DEBUG_HEADER + "authentication = " + authentication);
    }

    // Provide the completed authentication details.
    SecurityContextHolder.getContext().setAuthentication(authentication);
    log.debug(DEBUG_HEADER + "User successfully authenticated");

    // Continue the chain.
    chain.doFilter(request, response);

    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "Done.");
    }
  }

  /**
   * Provides the completed authentication for an unauthenticated user.
   *
   * @return a UsernamePasswordAuthenticationToken with the completed
   * authentication details.
   */
  private UsernamePasswordAuthenticationToken getUnauthenticatedUserToken() {
    Collection<GrantedAuthority> roles = new HashSet<GrantedAuthority>();
    roles.add(new SimpleGrantedAuthority("unauthenticatedRole"));

    return new UsernamePasswordAuthenticationToken("unauthenticatedUser",
        "unauthenticatedPassword", roles);
  }

  /**
   * Provides an indication of whether this request is available to everybody.
   *
   * @param httpRequest A HttpServletRequest with the incoming request.
   * @return <code>true</code> if this request is available to everybody,
   * <code>false</code> otherwise.
   */
  private boolean isWorldReachable(HttpServletRequest httpRequest) {
    final String DEBUG_HEADER = "isWorldReachable(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "Invoked.");
    }

    // Get the HTTP request method name.
    String httpMethodName = httpRequest.getMethod().toUpperCase();
    if (log.isDebug3()) {
      log.debug3(DEBUG_HEADER + "httpMethodName = " + httpMethodName);
    }

    // Get the HTTP request URI.
    String requestUri = httpRequest.getRequestURI().toLowerCase();
    if (log.isDebug3()) {
      log.debug3(DEBUG_HEADER + "requestUri = " + requestUri);
    }

    // Determine whether it is world-reachable.
    boolean result = getRequestUriAuthenticationBypass()
        .isWorldReachable(httpMethodName, requestUri);
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "result = " + result);
    }
    return result;
  }

  /**
   * Checks whether the current user has the role required to fulfill a set of
   * roles. Throws AccessControlException if the check fails.
   *
   * @param permissibleRoles A String... with the roles permissible for the user to be able to
   * execute an operation.
   */
  public static void checkAuthorization(String... permissibleRoles) {
    final String DEBUG_HEADER = "checkAuthorization(): ";
    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER
          + "permissibleRoles = " + Arrays.toString(permissibleRoles));
    }

    AuthUtil.checkAuthorization(SecurityContextHolder.getContext()
        .getAuthentication().getName(), permissibleRoles);

    if (log.isDebug2()) {
      log.debug2(DEBUG_HEADER + "Done.");
    }
  }

  /**
   * Provides the authentication bypass.
   *
   * @return a RequestUriAuthenticationBypass with the authentication bypass.
   */
  public RequestUriAuthenticationBypass getRequestUriAuthenticationBypass() {
    return new RequestUriAuthenticationBypassImpl();
  }
}
