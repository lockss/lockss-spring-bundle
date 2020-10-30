/*

 Copyright (c) 2017-2020 Board of Trustees of Leland Stanford Jr. University,
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
import java.util.*;
import org.lockss.log.L4JLogger;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Authentication and authorization utility code.
 */
public class AuthUtil {

  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * Called by service impls to check whether the currently authenticated
   * user has the necessary roles for a specific request
   *
   * @param permissibleRoles A String... with the roles permissible for the
   * user to be able to execute an operation.
   * @return true if the user has any roles that fulfill the permissible
   * roles
   */
  public static void checkHasRole(String... permissibleRoles) {
    AuthUtil.checkHasRole(SecurityContextHolder.getContext().getAuthentication(),
			  permissibleRoles);
  }

  /**
   * Checks whether the user has the role required to fulfill a set of roles.
   * Throws AccessControlException if the check fails.
   *
   * @param userName A String with the user name.
   * @param permissibleRoles A String... with the roles permissible for the
   * user to be able to execute an operation.
   */
  public static void checkHasRole(Authentication authToken,
				  String... permissibleRoles) {
    String userName = authToken.getName();
    Collection<String> userRoles = new ArrayList<>();
    for (GrantedAuthority auth : authToken.getAuthorities()) {
      userRoles.add(auth.getAuthority());
    }
    log.debug2("userName = {}", userName);
    log.debug2("userRoles = {}", userRoles);
    log.debug2("permissibleRoles = {}", Arrays.toString(permissibleRoles));

    // An administrator is always authorized.
    if (userRoles.contains(Roles.ROLE_ALL_ACCESS)) {
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
      if (Roles.ROLE_ANY.equals(permissibleRole)) {
	log.debug2("Authorized like everybody else.");
        return;
      }

      // The user is authorized if it has this permissible role.
      if (userRoles.contains(permissibleRole)) {
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

}
