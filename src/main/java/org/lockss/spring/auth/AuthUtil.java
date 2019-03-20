/*

 Copyright (c) 2017-2019 Board of Trustees of Leland Stanford Jr. University,
 all rights reserved.

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in
 all copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL
 STANFORD UNIVERSITY BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.

 Except as contained in this notice, the name of Stanford University shall not
 be used in advertising or otherwise to promote the sale, use or other dealings
 in this Software without prior written authorization from Stanford University.

 */
package org.lockss.spring.auth;

import java.security.AccessControlException;
import java.util.Arrays;
import java.util.Base64;
import org.lockss.account.UserAccount;
import org.lockss.app.LockssDaemon;
import org.lockss.config.Configuration;
import org.lockss.config.CurrentConfig;
import org.lockss.log.L4JLogger;

/**
 * Authentication and authorization utility code.
 */
public class AuthUtil {

  private static final String BASIC_AUTH_TYPE = "basic";
  private static final String NONE_AUTH_TYPE = "none";

  /**
   * Prefix for configuration properties.
   */
  private static final String PREFIX = Configuration.PREFIX + "restAuth.";
  private static final String PARAM_AUTH_TYPE = PREFIX + "authenticationType";
  private static final String DEFAULT_AUTH_TYPE = NONE_AUTH_TYPE;

  private static final String invalidAutheticationType =
      "Invalid Authentication Type (must be BASIC or NONE).";

  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * Decodes the basic authorization header.
   *
   * @param header A String with the Authorization header.
   * @return a String[] with the user name and the password.
   */
  public static String[] decodeBasicAuthorizationHeader(String header) {
    log.debug2("header = {}", header);

    // Get the header meaningful bytes.
    byte[] decodedBytes =
        Base64.getDecoder().decode(header.replaceFirst("[B|b]asic ", ""));

    // Check whether nothing was decoded.
    if (decodedBytes == null || decodedBytes.length == 0) {
      // Yes: Done.
      return null;
    }

    // No: Extract the individual credential items, the user name and the
    // password.
    String[] result = new String(decodedBytes).split(":", 2);
    log.debug2("result = [{}, ****]", result[0]);
    return result;
  }

  /**
   * Checks whether the user has the role required to fulfill a set of roles.
   * Throws AccessControlException if the check fails.
   *
   * @param userName A String with the user name.
   * @param permissibleRoles A String... with the roles permissible for the user to be able to
   * execute an operation.
   */
  public static void checkAuthorization(String userName,
      String... permissibleRoles) {
    log.debug2("userName = {}", userName);
    log.debug2("permissibleRoles = {}", Arrays.toString(permissibleRoles));

    // Check whether authentication is not required at all.
    if (!AuthUtil.isAuthenticationOn()) {
      // Yes: Continue normally.
      log.debug2("Authorized (like everybody else).");
      return;
    }

    // Get the user account.
    UserAccount userAccount = null;

    try {
      userAccount =
          LockssDaemon.getLockssDaemon().getAccountManager().getUser(userName);
      log.trace("userAccount.getRoleSet() = {}", userAccount.getRoleSet());
    } catch (Exception e) {
      log.error("userName = {}", userName);
      log.error("LockssDaemon.getLockssDaemon().getAccountManager()."
          + "getUser(" + userName + ")", e);
      throw new AccessControlException("Unable to get user '" + userName + "'");
    }

    // An administrator is always authorized.
    if (userAccount.isUserInRole(org.lockss.spring.auth.Roles.ROLE_USER_ADMIN))
    {
      log.debug2("Authorized as administrator.");
      return;
    }

    // Check whether there are no permissible roles.
    if (permissibleRoles == null || permissibleRoles.length == 0) {
      // Yes: Normal users are not authorized.
      String message = "Unauthorized like any non-administrator";
      log.debug2(message);
      throw new AccessControlException(message);
    }

    // Loop though all the permissible roles.
    for (String permissibleRole : permissibleRoles) {
      log.trace("permissibleRole = {}", permissibleRole);

      // If any role is permitted, this user is authorized.
      if (org.lockss.spring.auth.Roles.ROLE_ANY.equals(permissibleRole)) {
	log.debug2("Authorized like everybody else.");
        return;
      }

      // The user is authorized if it has this permissible role.
      if (userAccount.isUserInRole(permissibleRole)) {
	log.debug2("Authorized because user is in role.");
        return;
      }
    }

    // The user is not authorized because it does not have any of the
    // permissible roles.
    String message = "Unauthorized because user '" + userName
        + "'does not have any of the permissible roles";
    log.debug2(message);
    throw new AccessControlException(message);
  }

  /**
   * Provides an indication of whether authentication is required. Throws
   * AccessControlException if the the specified authentication method is not
   * valid.
   *
   * @return a boolean with <code>true</code> if authentication is required,
   * <code>false</code> otherwise.
   */
  public static boolean isAuthenticationOn() {
    log.debug2("Invoked.");

    // Get the configured authentication type.
    String authenticationType =
        CurrentConfig.getParam(PARAM_AUTH_TYPE, DEFAULT_AUTH_TYPE);
    log.trace("authenticationType = {}", authenticationType);

    // Check whether access is allowed to anybody.
    if (NONE_AUTH_TYPE.equalsIgnoreCase(authenticationType)) {
      // Yes.
      log.debug2("Authentication is OFF.");
      return false;
      // No: Check whether the authentication type is not "basic".
    } else if (!BASIC_AUTH_TYPE.equalsIgnoreCase(authenticationType)) {
      // Yes: Report the problem.
      log.error(invalidAutheticationType);
      log.error("authenticationType = {}", authenticationType);

      throw new AccessControlException(authenticationType + ": "
          + invalidAutheticationType);
    }

    // No.
    log.debug2("Authentication is ON.");
    return true;
  }
}
