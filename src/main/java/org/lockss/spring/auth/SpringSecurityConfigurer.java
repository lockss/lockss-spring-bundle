/*

Copyright (c) 2000-2019 Board of Trustees of Leland Stanford Jr. University,
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

import org.lockss.log.L4JLogger;
import org.lockss.app.LockssDaemon;
import org.lockss.config.ConfigManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;

/**
 * Default LOCKSS custom Spring security configurator.
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SpringSecurityConfigurer extends WebSecurityConfigurerAdapter {

  private final static L4JLogger log = L4JLogger.getLogger();

  private LockssDaemon daemon;
  private SpringAuthenticationFilter authFilter;

  // Register config callback
  @EventListener
  public void configMgrCreated(ConfigManager.ConfigManagerCreatedEvent event) {
    log.debug2("ConfigManagerCreatedEvent triggered");
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

  /**
   * Allows through encoded slashes in URLs.
   * 
   * @return an HttpFirewall set up to allow through encoded slashes in URLs.
   */
  @Bean
  public HttpFirewall allowUrlEncodedSlashHttpFirewall() {
    DefaultHttpFirewall firewall = new DefaultHttpFirewall();
    firewall.setAllowUrlEncodedSlash(true);
    return firewall;
  }

  @Override
  public void configure(WebSecurity web) throws Exception {
    web.httpFirewall(allowUrlEncodedSlashHttpFirewall());
  }

  /**
   * Configures the authentication strategy and filter.
   *
   * @param http An HttpSecurity to be configured.
   * @throws Exception if there are problems.
   */
  @Override
  protected void configure(HttpSecurity http) throws Exception {
    log.debug2("Invoked.");

    // Force each and every request to be authenticated.
    http.csrf().disable().authorizeRequests().anyRequest().authenticated();

    log.debug2("Installing auth filter");
    // The Basic authentication filter to be used.
    authFilter = new SpringAuthenticationFilter();
    http.addFilterBefore(authFilter,
        BasicAuthenticationFilter.class);
    log.debug2("Done.");
  }
}
