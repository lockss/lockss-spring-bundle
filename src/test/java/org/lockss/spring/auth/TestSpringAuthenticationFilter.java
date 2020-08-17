/*

 Copyright (c) 2020 Board of Trustees of Leland Stanford Jr. University,
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

import org.junit.*;
import org.lockss.spring.test.SpringLockssTestCase4;
import org.lockss.config.*;
import org.lockss.log.*;
import org.lockss.test.ConfigurationUtil;

/**
 * Test class for org.lockss.spring.auth.SpringAuthenticationFilter
 */
public class TestSpringAuthenticationFilter extends SpringLockssTestCase4 {
  L4JLogger log = L4JLogger.getLogger();

  SpringAuthenticationFilter authFilter;

  @Before
  public void setUpAuthFilter() throws Exception {
    authFilter = new SpringAuthenticationFilter();
    // This is normally done by SpringSecurityConfigurer but I haven't
    // figured out how to get Spring to create that bean in a test
    ConfigManager.getConfigManager().registerConfigurationCallback(new ConfigCallback());
  }

  private class ConfigCallback
    implements org.lockss.config.Configuration.Callback {
    public void configurationChanged(org.lockss.config.Configuration newConfig,
				     org.lockss.config.Configuration oldConfig,
				     org.lockss.config.Configuration.Differences changedKeys) {
      log.debug2("configurationChanged: {}", newConfig);
      authFilter.setConfig(newConfig, oldConfig, changedKeys);
    }
  }

  @Test
  public void testRequiresUserAuth() {
    assertTrue(authFilter.requiresAuthentication("PUT", "/endpoint"));
    assertTrue(authFilter.requiresAuthentication("GET", "/endpoint"));
    assertFalse(authFilter.requiresAuthentication("GET", "/status"));
    assertTrue(authFilter.requiresAuthentication("PUT", "/status"));
    assertFalse(authFilter.requiresAuthentication("GET", "/v2/api-docs"));
    assertFalse(authFilter.requiresAuthentication("GET", "/swagger-ui.html"));
    assertFalse(authFilter.requiresAuthentication("GET",
						  "/swagger-resources/foo"));
    assertFalse(authFilter.requiresAuthentication("GET",
						  "/webjars/springfox-swagger-ui/bar"));

    ConfigurationUtil.addFromArgs(SpringAuthenticationFilter.PARAM_ALLOW_UNAUTHENTICATED_READ, "true");
    assertTrue(authFilter.requiresAuthentication("PUT", "/endpoint"));
    assertFalse(authFilter.requiresAuthentication("GET", "/endpoint"));
  }

  @Test
  public void testRequiresIpAuth() {
    assertTrue(authFilter.requiresIpAuthorization("PUT", "/endpoint"));
    assertTrue(authFilter.requiresIpAuthorization("GET", "/endpoint"));
    assertFalse(authFilter.requiresIpAuthorization("GET", "/status"));
    assertTrue(authFilter.requiresIpAuthorization("PUT", "/status"));
    assertFalse(authFilter.requiresIpAuthorization("GET", "/v2/api-docs"));
    assertFalse(authFilter.requiresIpAuthorization("GET", "/swagger-ui.html"));
    assertFalse(authFilter.requiresIpAuthorization("GET",
						  "/swagger-resources/foo"));
    assertFalse(authFilter.requiresIpAuthorization("GET",
						  "/webjars/springfox-swagger-ui/bar"));

    ConfigurationUtil.addFromArgs(SpringAuthenticationFilter.PARAM_ALLOW_UNAUTHENTICATED_READ, "true");
    assertTrue(authFilter.requiresIpAuthorization("PUT", "/endpoint"));
    assertTrue(authFilter.requiresIpAuthorization("GET", "/endpoint"));
  }

  @Test
  public void testIsIpAuthorized() throws Exception {
    // Loopback is allowed by default
    assertTrue(authFilter.isIpAuthorized("127.0.0.1"));
    assertFalse(authFilter.isIpAuthorized("1.2.3.4"));

    ConfigurationUtil.addFromArgs(SpringAuthenticationFilter.PARAM_ALLOW_LOOPBACK,
				  "false");
    assertFalse(authFilter.isIpAuthorized("127.0.0.1"));
    assertFalse(authFilter.isIpAuthorized("1.2.3.4"));

    ConfigurationUtil.addFromArgs(SpringAuthenticationFilter.PARAM_IP_INCLUDE,
				  "1.2.3.0/24;4.3.2.1");
    assertFalse(authFilter.isIpAuthorized("127.0.0.1"));
    assertTrue(authFilter.isIpAuthorized("1.2.3.4"));
  }

}
