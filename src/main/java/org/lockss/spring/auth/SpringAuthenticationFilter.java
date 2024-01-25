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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.lockss.account.*;
import org.lockss.app.LockssDaemon;
import org.lockss.config.*;
import org.lockss.log.L4JLogger;
import org.lockss.util.time.*;
import org.lockss.util.ListUtil;
import org.lockss.util.StringUtil;
import org.lockss.util.IpFilter;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.context.*;
import org.springframework.web.context.support.WebApplicationContextUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Default LOCKSS custom Spring authentication filter.
 */
public class SpringAuthenticationFilter extends GenericFilterBean {
  private final static L4JLogger log = L4JLogger.getLogger();

  private static final String MISSING_AUTH_HEADER =
    "No authentication header.";
  private static final String MISSING_CREDENTIALS =
    "No userid/password credentials.";
  private static final String BAD_CREDENTIALS =
    "Bad userid/password credentials.";
  private static final String INVALID_AUTH_TYPE =
    "Invalid Authentication Type (must be \"basic\" or \"none\").";

  // Use UI access list params for REST also
  private static final String ACCESS_PREFIX = "org.lockss.ui.access.";
  public static final String PARAM_IP_INCLUDE = ACCESS_PREFIX + "ip.include";
  public static final String PARAM_IP_EXCLUDE = ACCESS_PREFIX + "ip.exclude";

  private static final String AUTH_PREFIX = Configuration.PREFIX + "restAuth.";

  public static final String BASIC_AUTH_TYPE = "basic";
  public static final String NONE_AUTH_TYPE = "none";

  /** User authentication type: "none" or "basic" */
  public static final String PARAM_AUTH_TYPE =
    AUTH_PREFIX + "authenticationType";
  public static final String DEFAULT_AUTH_TYPE = NONE_AUTH_TYPE;

  /** If true, read-only requests need not supply authentication credentials */
  public static final String PARAM_ALLOW_UNAUTHENTICATED_READ =
    AUTH_PREFIX + "allowUnauthenticatedRead";
  public static final boolean DEFAULT_ALLOW_UNAUTHENTICATED_READ = false;

  /** If true, requests from the loopback address are accepted regardless
   * of other IP access filters */
  public static final String PARAM_ALLOW_LOOPBACK = ACCESS_PREFIX +
    "allowLoopback";
  public static final boolean DEFAULT_ALLOW_LOOPBACK = true;

  /** Log attempted accesses forbidden by IP access rules */
  public static final String PARAM_LOG_FORBIDDEN = ACCESS_PREFIX +
    "logForbidden";
  public static final boolean DEFAULT_LOG_FORBIDDEN = true;

  private static List<String> LOCAL_IP_FILTERS = ListUtil.list("127.0.0.0/8",
							       "::1");

  private Environment env;		// Spring Environment, access to
					// Spring config props
  private LockssDaemon daemon;
  private boolean isConfigSet = false;
  private String authType = DEFAULT_AUTH_TYPE;
  private boolean allowUnauthenticatedRead =
    DEFAULT_ALLOW_UNAUTHENTICATED_READ;;
  private boolean logForbidden = DEFAULT_LOG_FORBIDDEN;
  private boolean allowLocal = DEFAULT_ALLOW_LOOPBACK;
  private IpFilter ipFilter;
  private IpFilter localFilter;

  public SpringAuthenticationFilter() {
    createLocalFilter(Collections.emptyList());
  }

  public void setConfig(Configuration newConfig, Configuration oldConfig,
			Configuration.Differences changedKeys) {
    log.debug2("setConfig: {}, {}", this, newConfig);
    if (changedKeys.contains(AUTH_PREFIX) ||
	changedKeys.contains(ACCESS_PREFIX) ||
	changedKeys.contains(ConfigManager.PARAM_PLATFORM_CONTAINER_SUBNETS)) {
      authType = newConfig.get(PARAM_AUTH_TYPE, DEFAULT_AUTH_TYPE);
      allowUnauthenticatedRead =
	newConfig.getBoolean(PARAM_ALLOW_UNAUTHENTICATED_READ,
			     DEFAULT_ALLOW_UNAUTHENTICATED_READ);
      logForbidden = newConfig.getBoolean(PARAM_LOG_FORBIDDEN,
					  DEFAULT_LOG_FORBIDDEN);
      allowLocal = newConfig.getBoolean(PARAM_ALLOW_LOOPBACK,
					DEFAULT_ALLOW_LOOPBACK);
      createLocalFilter(ConfigManager.getPlatformContainerSubnets());
      setIpFilter(newConfig.get(PARAM_IP_INCLUDE),
		  newConfig.get(PARAM_IP_EXCLUDE));
    }
    isConfigSet = true;
  }

  private void setIpFilter(String includeIps, String excludeIps) {
    log.debug("Installing new ip filter: incl: {}, excl: {}",
	      includeIps, excludeIps);
    try {
      IpFilter filter = new IpFilter();
      filter.setFilters(includeIps, excludeIps);
      ipFilter = filter;
    } catch (IpFilter.MalformedException e) {
      log.warn("Malformed IP filter, filters not changed", e);
    }
  }

  private void createLocalFilter(List<String> containerSubnets) {
    if (allowLocal) {
      IpFilter filt = new IpFilter();
      try {
	List<String> localSubnets = new ArrayList<>(LOCAL_IP_FILTERS);
	localSubnets.addAll(containerSubnets);
	filt.setFilters(localSubnets, null);
      } catch (IpFilter.MalformedException e) {
	log.error("Failed to allow local addresses" , e);
      }
      localFilter = filt;
    }
  }

  private final static Pattern IPADDR_BRACKETS = Pattern.compile("^\\[(.*)\\]$");

  private static String stripBrackets(String ipaddr) {
    Matcher m = IPADDR_BRACKETS.matcher(ipaddr);
    return m.matches() ? m.group(1) : ipaddr;
  }

  Pattern IP_PROTECTED_PATHS = Pattern.compile("^/(usernames|users).*");

  protected boolean isRestrictedPath(String reqUri) {
    UriComponents reqUriComponents =
        UriComponentsBuilder.fromUriString(reqUri).build();

    return IP_PROTECTED_PATHS.matcher(reqUriComponents.getPath()).matches();
  }

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
    log.debug2("Invoked {}.", this);

    HttpServletRequest httpRequest = (HttpServletRequest) request;

    if (log.isTraceEnabled()) {
      StringBuffer originalUrl = httpRequest.getRequestURL();

      if (httpRequest.getQueryString() != null) {
        originalUrl.append("?").append(httpRequest.getQueryString());
      }

      log.trace("originalUrl = {}", originalUrl);
    }

    String reqUri = httpRequest.getRequestURI();

    boolean isRestrictedPath = isRestrictedPath(reqUri);

    HttpServletResponse httpResponse = (HttpServletResponse) response;

    // Check source IP addr if IP auth is required for this request
    String srcIp = stripBrackets(request.getRemoteAddr());
    if (requiresIpAuthorization(httpRequest)) {
      log.trace("Access to {} requested from {}", reqUri, srcIp);
      if (!isConfigSet) {
	log.debug2("Config not yet loaded, waiting ...");
	if (!waitConfig(request)) {
	  log.warn("Timed out waiting for config, can't check IP access");
	  sendNotReady(httpResponse);
	  return;
	}
      }
      try {
	if (!isIpAuthorized(srcIp, isRestrictedPath)) {
	  // The IP is NOT allowed
	  if (logForbidden) {
	    log.info("Access to {} forbidden from {}", reqUri, srcIp);
	  }
	  sendForbidden(httpResponse, "Forbidden");
	  return;
	}
	String forwardedFor = httpRequest.getHeader("X-Forwarded-For");
	if (!StringUtils.isEmpty(forwardedFor)) {
	  String mostRecentIp = stripBrackets(lastElement(forwardedFor));
	  if (!isIpAuthorized(mostRecentIp, isRestrictedPath)) {
	    // The IP is NOT allowed
	    if (logForbidden) {
	      log.info("Access to {} forbidden for request forwarded from {}",
		       reqUri, mostRecentIp);
	    }
	    sendForbidden(httpResponse, "Forbidden");
	    return;
	  }
	}
      } catch (Exception e) {
	log.warn("Error checking IP", e);
	httpResponse.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
//       httpResponse.setHandled(true);
      }
    } else {
      log.trace("Allowing unchecked access from {} to {}", srcIp, reqUri);
    }

    // Check user credentials if required for this request
    if (!isAuthenticationOn()) {
      // If authentication is disabled, set the authenticated principal
      // to one with maximum capabilities
      log.trace("Authorization is disabled");

      SecurityContextHolder.getContext().setAuthentication(
          getPrivilegedUnauthenticatedUserToken());

      // Continue the chain.
      chain.doFilter(request, response);
      return;
    }

    // Does this request require an authenticated user
    if (!requiresAuthentication(httpRequest)) {
	// No, set the authenticated principal to one with minimal capabilities
      log.trace("Authentication not required for {}", reqUri);

      SecurityContextHolder.getContext().setAuthentication(
          getUnprivilegedUnauthenticatedUserToken());

      // Continue the chain.
      chain.doFilter(request, response);
      return;
    }

    // Authentication required - is it configured yet?
    if (!isConfigSet) {
      log.debug2("Config not yet loaded, waiting ...");
      if (!waitConfig(request)) {
	log.warn("Timed out waiting for config, can't check user auth");
	sendNotReady(httpResponse);
	return;
      }
    }

    AccountManager acctMgr =
      (AccountManager)getLockssDaemon().waitManagerByKey(LockssDaemon.managerKey(AccountManager.class),
							 Deadline.in(getReadyWaitTime(request)));
    if (acctMgr == null) {
      log.warn("Timed out waiting for AccountManager, can't check user auth");
      sendNotReady(httpResponse);
      return;
    }
    if (!acctMgr.isStarted()) {
      log.debug2("AccountManager not started, waiting ...");
      if (!waitUserAccounts(acctMgr, request)) {
	log.warn("Timed out waiting for AccountManager, can't check user auth");
	sendNotReady(httpResponse);
	return;
      }
    }

    // Get the authorization header.
    String authorizationHeader = httpRequest.getHeader("authorization");
    log.trace("authorizationHeader = {}", authorizationHeader);

    if (authorizationHeader == null) {
      log.info(MISSING_AUTH_HEADER);
      sendUnauthenticated(httpResponse, MISSING_AUTH_HEADER);
      return;
    }

    // Get the user credentials in the authorization header.
    String[] credentials = org.lockss.util.auth.AuthUtil
	.decodeBasicAuthorizationHeader(authorizationHeader);
    if (credentials == null) {
      log.info(MISSING_CREDENTIALS);
      sendUnauthenticated(httpResponse, MISSING_CREDENTIALS);
      return;
    }

    // Check whether the found credentials are valid
    if (credentials.length != 2) {
      log.info("Malformed user credentials.  Should have 2 elements, has {}",
               credentials.length);
      sendUnauthenticated(httpResponse, "Malformed user credentials");
      return;
    }

    log.trace("credentials[0] = {}", credentials[0]);

    UserAccount userAccount = acctMgr.getUserOrNull(credentials[0]);
    if (userAccount == null) {
      log.info("Invalid credentials = {}:{}", credentials[0], "********");
      sendUnauthenticated(httpResponse, BAD_CREDENTIALS);
      return;
    }

    log.trace("userAccount.getName() = {}", userAccount.getName());

    // Check whether the user credentials are good.
    if (!userAccount.check(credentials[1])) {
      log.info("Invalid credentials = {}:{}", credentials[0], "********");
      sendUnauthenticated(httpResponse, BAD_CREDENTIALS);
      return;
    }

    // Get the user roles, store in the thread's SecurityContext
    Collection<GrantedAuthority> roles = new HashSet<GrantedAuthority>();

    for (String role : userAccount.getRoleSet()) {
      roles.add(new SimpleGrantedAuthority(role));
    }
    log.trace("roles = {}", roles);

    // Create the completed authentication details.
    UsernamePasswordAuthenticationToken authentication =
      new UsernamePasswordAuthenticationToken(credentials[0],
					      credentials[1],
					      roles);
    log.trace("authentication = {}", authentication);

    // Store in the SecurityContext
    SecurityContextHolder.getContext().setAuthentication(authentication);
    log.debug2("User successfully authenticated");

    // Continue the chain.
    chain.doFilter(request, response);

    log.debug2("Done.");
  }

  String lastElement(String forwardedChain) {
    String[] ips = forwardedChain.split(",");
    return ips[ips.length-1].trim();
  }

  /** Return true is the system is configured to require user authentication */
  boolean isAuthenticationOn() {
    switch (authType) {
    case NONE_AUTH_TYPE: return false;
    case BASIC_AUTH_TYPE: return true;
    default:
      log.error("authenticationType = {}", authType);
      throw new AccessControlException(authType + ": " + INVALID_AUTH_TYPE);
    }
  }

  /** Return true if the IP address is allowed by the IP access filters */
  boolean isIpAuthorized(String ip) throws IpFilter.MalformedException {
    return isIpAuthorized(ip, false);
  }

  boolean isIpAuthorized(String ip, boolean isRestrictedPath) throws IpFilter.MalformedException {
    return ((ipFilter != null && !isRestrictedPath && ipFilter.isIpAllowed(ip)) ||
        (allowLocal && localFilter.isIpAllowed(ip)));
  }

  /** Send 503 Serice Unavailable, with a reason */
  private void sendNotReady(HttpServletResponse httpResponse)
      throws IOException{
    httpResponse.setHeader("Retry-After", "60"); // random, inaccurate guess
    httpResponse.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
			   "Request requires authorization/authentication but service is still starting and cannot perform authentication yet.");
  }

  /** Send 401 Unauthorized (which is really unauthenticated), and ask for
   * authentication */
  private void sendUnauthenticated(HttpServletResponse httpResponse,
				   String msg)
      throws IOException {
    SecurityContextHolder.clearContext();
    httpResponse.setHeader("WWW-Authenticate", "Basic");
    httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, msg);
  }

  /** Send 401 Forbidden */
  private void sendForbidden(HttpServletResponse httpResponse, String msg)
      throws IOException {
    SecurityContextHolder.clearContext();
    httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, msg);
  }

  /**
   * Return an authentication token for a user who can perform all
   * operations
   */
  UsernamePasswordAuthenticationToken getPrivilegedUnauthenticatedUserToken() {
    Collection<GrantedAuthority> roles = new HashSet<GrantedAuthority>();
    roles.add(new SimpleGrantedAuthority(Roles.ROLE_ALL_ACCESS));

    return new UsernamePasswordAuthenticationToken("unauthenticatedPrivUser",
        "unauthenticatedPassword", roles);
  }

  /**
   * Return an authentication token for a user who can perform only
   * non-privileged operations
   */
  UsernamePasswordAuthenticationToken getUnprivilegedUnauthenticatedUserToken() {
    Collection<GrantedAuthority> roles = new HashSet<GrantedAuthority>();
    roles.add(new SimpleGrantedAuthority(Roles.ROLE_CONTENT_ACCESS));

    return new UsernamePasswordAuthenticationToken("unauthenticatedUnprivUser",
        "unauthenticatedPassword", roles);
  }

  /**
   * Return true if this request requires authentication
   *
   * @param httpRequest A HttpServletRequest with the incoming request.
   * @return true if this request requires authentication, false otherwise.
   */
  boolean requiresAuthentication(HttpServletRequest httpRequest) {
    return requiresAuthentication(httpRequest.getMethod().toUpperCase(),
				  httpRequest.getRequestURI().toLowerCase());
  }

  /**
   * Return true if this request method and URI requires authentication
   *
   * @param httpMethodName A String with the request method.
   * @param requestUri A String with the request URI.
   * @return true if this request requires authentication, false otherwise.
   */
  boolean requiresAuthentication(String httpMethodName, String requestUri) {
    log.trace("requiresAuthentication({}, {})", httpMethodName, requestUri);

    boolean result = !isStatusOrDocFetch(httpMethodName, requestUri);

    // Conditionally allow unauthenticated read requests
    if (result && allowUnauthenticatedRead &&
	isReadRequest(httpMethodName, requestUri)) {
      result = false;
    }
    log.trace("result = {}", result);
    return result;
  }

  /**
   * Return true if this request requires IP authorization
   *
   * @param httpRequest A HttpServletRequest with the incoming request.
   * @return true if this request requires IP authorization, false otherwise.
   */
  boolean requiresIpAuthorization(HttpServletRequest httpRequest) {
    return requiresIpAuthorization(httpRequest.getMethod().toUpperCase(),
				   httpRequest.getRequestURI().toLowerCase());
  }

  /*
   * Return true if this request method and URI requires IP authorization
   *
   * @param httpMethodName A String with the request method.
   * @param requestUri A String with the request URI.
   * @return true if this request requires IP authorization, false otherwise.
   */
  boolean requiresIpAuthorization(String httpMethodName, String requestUri) {
    return !isStatusOrDocFetch(httpMethodName, requestUri);
  }

  /**
   * Return true if this is a request for status or api docs
   *
   * @param httpMethodName A String with the request method.
   * @param requestUri A String with the request URI.
   */
  boolean isStatusOrDocFetch(String httpMethodName, String requestUri) {
    boolean result =
      "GET".equals(httpMethodName)
      && ("/status".equals(requestUri)
	  || "/v2/api-docs".equals(requestUri)
	  || "/swagger-ui.html".equals(requestUri)
	  || requestUri.startsWith("/swagger-resources")
	  || requestUri.startsWith("/webjars/springfox-swagger-ui")) ;
    if (result) {
      log.trace("Is status or doc request: {} {}", httpMethodName, requestUri);
    }
    return result;
  }

  /**
   * Return true if this is a read request
   *
   * @param httpMethodName A String with the request method.
   * @param requestUri A String with the request URI.
   */
  boolean isReadRequest(String httpMethodName, String requestUri) {
    return "GET".equals(httpMethodName);
  }

  // This should be in AuthUtil, as it's called statically from service
  // implementations that have no direct relationship with
  // SpringAuthenticationFilter.  Left here while there are still
  // references.

  /**
   * Called by service impls to check whether the currently authenticated
   * user has the necessary roles for a specific request
   *
   * @param permissibleRoles A String... with the roles permissible for the
   * user to be able to execute an operation.
   * @return true if the user has any roles that fulfill the permissible
   * roles
   */
  public static void checkAuthorization(String... permissibleRoles) {
    AuthUtil.checkHasRole(permissibleRoles);
  }

  /** Return the ApplicationContext
   * @param request the ServletRequest
   */
  WebApplicationContext getApplicationContext(ServletRequest request) {
    ServletContext sc = request.getServletContext();
    return WebApplicationContextUtils.getWebApplicationContext(sc);
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


  protected long getReadyWaitTime(ServletRequest request) {
    return getWaitTime(getEnvironment(request).getProperty(PARAM_READY_WAIT_TIME),
		       DEFAULT_READY_WAIT_TIME);
  }

  protected long getConfigWaitTime(ServletRequest request) {
    return getWaitTime(getEnvironment(request).getProperty(PARAM_CONFIG_WAIT_TIME),
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
  protected boolean waitReady(ServletRequest request) {
    return waitReady(getReadyWaitTime(request));
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
  protected boolean waitConfig(ServletRequest request) {
    return waitConfig(getConfigWaitTime(request));
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

  /** Wait for the first config load to complete, return true when it has.
   * Wait time is set by the application property {@value
   * PARAM_CONFIG_WAIT_TIME} (default {@value DEFAULT_CONFIG_WAIT_TIME}),
   * in milliseconds.  Service API handlers that require the config to be
   * loaded but which can perform their function before the daemon is
   * ready should wait for this before servicing an incoming request.
   * Return false if the config load hasn't completed within the allotted
   * time, in which case the service should return an error. */
  protected boolean waitUserAccounts(AccountManager acctMgr,
				     ServletRequest request) {
    return waitUserAccounts(acctMgr, getConfigWaitTime(request));
  }

  /** Wait up to the specified time for the first config load to complete,
   * return true when it has.  Service API handlers that require the config
   * to be loaded but which can perform their function before the daemon is
   * ready should wait for this before servicing an incoming request.
   * Return false if the config load hasn't completed within the allotted
   * time, in which case the service should return an error. */
  protected boolean waitUserAccounts(AccountManager acctMgr, long wait) {
    return acctMgr.waitStarted(Deadline.in(wait));
  }

  /**
   * Return the configuration manager.
   *
   * @return the ConfigManager instance
   */
  private ConfigManager getConfigManager() {
    return ConfigManager.getConfigManager();
  }

  /**
   * Return the LockssDaemon instance, waiting a short time if necessary
   * for it to be created
   *
   * @return the LockssDaemon instance
   */
  private LockssDaemon getLockssDaemon() {
    return LockssDaemon.getLockssDaemon();
  }

  /** Return the Spring Environment
   * @param request the ServletRequest
   * @return the Spring Environment
   */
  Environment getEnvironment(ServletRequest request) {
    if (env == null) {
      WebApplicationContext wac = getApplicationContext(request);
      env = wac.getEnvironment();
    }
    return env;
  }
}
