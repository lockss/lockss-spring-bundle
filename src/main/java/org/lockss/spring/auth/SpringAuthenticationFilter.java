/*

Copyright (c) 2000-2020 Board of Trustees of Leland Stanford Jr. University,
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
import org.lockss.account.UserAccount;
import org.lockss.app.LockssDaemon;
import org.lockss.config.ConfigManager;
import org.lockss.log.L4JLogger;
import org.lockss.util.time.*;
import org.lockss.util.StringUtil;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.context.*;
import org.springframework.web.context.support.WebApplicationContextUtils;

/**
 * Default LOCKSS custom Spring authentication filter.
 */
public class SpringAuthenticationFilter extends GenericFilterBean {

  private static final String noAuthorizationHeader =
      "No authorization header.";
  private static final String noCredentials = "No userid/password credentials.";
  private static final String badCredentials =
      "Bad userid/password credentials.";
  private static final String noUser = "User not found.";
  private final static L4JLogger log = L4JLogger.getLogger();

  private Environment env;		// Spring Environment, access to
					// config props

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
    log.debug2("Invoked.");

    HttpServletRequest httpRequest = (HttpServletRequest) request;

    if (log.isTraceEnabled()) {
      StringBuffer originalUrl = httpRequest.getRequestURL();

      if (httpRequest.getQueryString() != null) {
        originalUrl.append("?").append(httpRequest.getQueryString());
      }

      log.trace("originalUrl = {}", originalUrl);
    }

    HttpServletResponse httpResponse = (HttpServletResponse) response;

    try {
      // Check whether authentication is not required at all.
      if (!AuthUtil.isAuthenticationOn()) {
        // Yes: Continue normally.
	log.trace("Authorized (like everybody else).");

        SecurityContextHolder.getContext().setAuthentication(
            getUnauthenticatedUserToken());

        // Continue the chain.
        chain.doFilter(request, response);
        return;
      }
    } catch (AccessControlException ace) {
      // Report the configuration problem.
      String message = ace.getMessage();
      log.error(message);

      SecurityContextHolder.clearContext();
      httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, message);
      return;
    }

    // No: Check whether this request is available to everybody.
    if (isWorldReachable(httpRequest)) {
      // Yes: Continue normally.
      log.trace("Authenticated (like everybody else).");

      SecurityContextHolder.getContext().setAuthentication(
          getUnauthenticatedUserToken());

      // Continue the chain.
      chain.doFilter(request, response);
      return;
    }

    // No: Get the authorization header.
    String authorizationHeader = httpRequest.getHeader("authorization");
    log.trace("authorizationHeader = {}", authorizationHeader);

    // Check whether no authorization header was found.
    if (authorizationHeader == null) {
      // Yes: Report the problem.
      log.info(noAuthorizationHeader);

      sendUnauthenticated(httpResponse, noAuthorizationHeader);
      return;
    }

    // No: Get the user credentials in the authorization header.
    String[] credentials = org.lockss.util.auth.AuthUtil
	.decodeBasicAuthorizationHeader(authorizationHeader);

    // Check whether no credentials were found.
    if (credentials == null) {
      // Yes: Report the problem.
      log.info(noCredentials);

      sendUnauthenticated(httpResponse, noCredentials);
      return;
    }

    // No: Check whether the found credentials are not what was expected.
    if (credentials.length != 2) {
      // Yes: Report the problem.
      log.info(badCredentials);
      log.info("bad credentials = " + Arrays.toString(credentials));

      sendUnauthenticated(httpResponse, badCredentials);
      return;
    }

    log.trace("credentials[0] = {}", credentials[0]);

    // Wait until startup has progressed to the point where AccountManager
    // should be available.
    if (!waitReady()) {
      httpResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
			     "Not Ready");
      return;
    }

    // No: Get the user account.
    UserAccount userAccount = null;

    try {
      userAccount = LockssDaemon.getLockssDaemon().getAccountManager()
          .getUser(credentials[0]);
    } catch (Exception e) {
      log.error("credentials[0] = {}", credentials[0]);
      log.error("credentials[1] = {}", credentials[1]);
      log.error("LockssDaemon.getLockssDaemon().getAccountManager()."
          + "getUser(credentials[0])", e);
      httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
			     "AccountManager not available");
      return;
    }

    // Check whether no user was found.
    if (userAccount == null) {
      // Yes: Report the problem.
      log.info(noUser);

      sendUnauthenticated(httpResponse, badCredentials);
      return;
    }

    log.trace("userAccount.getName() = {}", userAccount.getName());

    // No: Verify the user credentials.
    boolean goodCredentials = userAccount.check(credentials[1]);
    log.trace("goodCredentials = {}", goodCredentials);

    // Check whether the user credentials are not good.
    if (!goodCredentials) {
      // Yes: Report the problem.
      log.info(badCredentials);
      log.info("userAccount.getName() = {}", userAccount.getName());
      log.info("bad credentials = {}", Arrays.toString(credentials));

      sendUnauthenticated(httpResponse, badCredentials);
      return;
    }

    // No: Get the user roles.
    Collection<GrantedAuthority> roles = new HashSet<GrantedAuthority>();

    for (Object role : userAccount.getRoleSet()) {
      log.trace("role = {}", role);
      roles.add(new SimpleGrantedAuthority((String) role));
    }

    log.trace("roles = {}", roles);

    // Create the completed authentication details.
    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(credentials[0],
            credentials[1], roles);
    log.trace("authentication = {}", authentication);

    // Provide the completed authentication details.
    SecurityContextHolder.getContext().setAuthentication(authentication);
    log.debug("User successfully authenticated");

    // Continue the chain.
    chain.doFilter(request, response);

    log.debug2("Done.");
  }

  private void sendUnauthenticated(HttpServletResponse httpResponse,
				   String msg)
      throws IOException {
    SecurityContextHolder.clearContext();
    httpResponse.setHeader("WWW-Authenticate", "Basic");
    httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, msg);
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
    log.debug2("Invoked.");

    // Get the HTTP request method name.
    String httpMethodName = httpRequest.getMethod().toUpperCase();
    log.trace("httpMethodName = {}", httpMethodName);

    // Get the HTTP request URI.
    String requestUri = httpRequest.getRequestURI().toLowerCase();
    log.trace("requestUri = {}", requestUri);

    // Determine whether it is world-reachable.
    boolean result = getRequestUriAuthenticationBypass()
        .isWorldReachable(httpMethodName, requestUri);
    log.debug2("result = {}", result);
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
    log.debug2("permissibleRoles = {}", Arrays.toString(permissibleRoles));

    AuthUtil.checkAuthorization(SecurityContextHolder.getContext()
        .getAuthentication().getName(), permissibleRoles);

    log.debug2("Done.");
  }

  /**
   * Provides the authentication bypass.
   *
   * @return a RequestUriAuthenticationBypass with the authentication bypass.
   */
  public RequestUriAuthenticationBypass getRequestUriAuthenticationBypass() {
    return new RequestUriAuthenticationBypassImpl();
  }

  /** Return the Spring Environment
   * @Param request the ServletRequest
   */
  Environment getEnvironment(ServletRequest request) {
    if (env == null) {
      javax.servlet.ServletContext sc = request.getServletContext();
      WebApplicationContext wac =
	WebApplicationContextUtils.getWebApplicationContext(sc);
      env = wac.getEnvironment();
    }
    return env;
  }

  // XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX
  // The waitReady() mechanism from BaseSpringApiServiceImpl is
  // replicated here for expedience, should be moved somewhere independent
  // of auth.
  // XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX

  /** Amount of time services will wait for the underlying daemon to become
   * ready before returning a 503 Unavailable response.  Can only be set in
   * a Spring config file. */
  public static String PARAM_READY_WAIT_TIME = "org.lockss.service.readyWait";
  public static long DEFAULT_READY_WAIT_TIME = TimeUtil.MINUTE;

  /** Amount of time services will wait for the initial config load to
   * complete, before returning a 503 Unavailable response.  Can only be
   * set in a Spring config file. */
  public static String PARAM_CONFIG_WAIT_TIME = "org.lockss.service.configWait";
  public static long DEFAULT_CONFIG_WAIT_TIME = TimeUtil.MINUTE;


  protected long getReadyWaitTime() {
    return getWaitTime(getEnvironment().getProperty(PARAM_READY_WAIT_TIME),
		       DEFAULT_READY_WAIT_TIME);
  }

  protected long getConfigWaitTime() {
    return getWaitTime(getEnvironment().getProperty(PARAM_CONFIG_WAIT_TIME),
		       DEFAULT_CONFIG_WAIT_TIME);
  }

  protected long getWaitTime(String paramVal, long dfault) {
    if (!StringUtil.isNullString(paramVal)) {
      try {
	return Long.parseLong(paramVal);
      } catch (NumberFormatException e) {
	log.warn("Can't parse wait time", e);
      }
    }
    return dfault;
  }

  /** Wait for the daemon to fully start, return true when it has.  Wait
   * time is set by the application property {@value PARAM_READY_WAIT_TIME}
   * (default {@value DEFAULT_READY_WAIT_TIME}), in milliseconds.  Most
   * service API handlers should wait for this before servicing an incoming
   * request.  Return false if the daemon doesn't start within the allotted
   * time, in which case the service should return an error. */
  protected boolean waitReady() {
    return waitReady(getReadyWaitTime());
  }

  /** Wait up to the specified time for the daemon to fully start, return
   * true when it has.  Most service API handlers should wait for this
   * before servicing an incoming request.  Return false if the daemon
   * doesn't start within the allotted time, in which case the service
   * should return an error. */
  protected boolean waitReady(long wait) {
    try {
      return getLockssDaemon().waitUntilAppRunning(Deadline.in(wait));
    } catch (InterruptedException e) {
      return false;
    }
  }

  /** Wait for the first config load to complete, return true when it has.
   * Wait time is set by the application property {@value
   * PARAM_CONFIG_WAIT_TIME} (default {@value DEFAULT_CONFIG_WAIT_TIME}),
   * in milliseconds.  Service API handlers that require the config to be
   * loaded but which can perform their function before the daemon is
   * ready should wait for this before servicing an incoming request.
   * Return false if the config load hasn't completed within the allotted
   * time, in which case the service should return an error. */
  protected boolean waitConfig() {
    return waitConfig(getConfigWaitTime());
  }

  /** Wait up to the specified time for the first config load to complete,
   * return true when it has.  Service API handlers that require the config
   * to be loaded but which can perform their function before the daemon is
   * ready should wait for this before servicing an incoming request.
   * Return false if the config load hasn't completed within the allotted
   * time, in which case the service should return an error. */
  protected boolean waitConfig(long wait) {
    return getConfigManager().waitConfig(Deadline.in(wait));
  }

  /**
   * Return the configuration manager.
   *
   * @return a ConfigManager with the configuration manager.
   */
  private ConfigManager getConfigManager() {
    return ConfigManager.getConfigManager();
  }

  /**
   * Return the LockssDaemon instance, waiting a short time if necessary
   * for it to be created
   *
   * @return the LockssDaemon instance
   *
   */
  private LockssDaemon getLockssDaemon() {
    return LockssDaemon.getLockssDaemon();
  }

}
