/*

 Copyright (c) 2016 - 2020 Board of Trustees of Leland Stanford Jr. University,
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
import org.lockss.servlet.LockssServlet;

/**
 * The authorization roles.
 */
public class Roles {

  /**
   * User may configure admin access (add/delete/modify users, set admin
   * access list)
   */
  public static final String ROLE_USER_ADMIN = LockssServlet.ROLE_USER_ADMIN;

  /**
   * Maximum capabilities role.
   */
  public static final String ROLE_ALL_ACCESS = ROLE_USER_ADMIN;

  /**
   * User may configure content access (set content access list).
   */
  public static final String ROLE_CONTENT_ADMIN =
    LockssServlet.ROLE_CONTENT_ADMIN;

  /**
   * User may change AU configuration (add/delete content).
   */
  public static final String ROLE_AU_ADMIN = LockssServlet.ROLE_AU_ADMIN;

  /**
   * User may access content.
   */
  public static final String ROLE_CONTENT_ACCESS =
    LockssServlet.ROLE_CONTENT_ACCESS;

  public static final String ROLE_DEBUG = LockssServlet.ROLE_DEBUG;

  /**
   * Minimum role of any authenticated user.
   */
  public static final String ROLE_ANY = "anyRole";
}
