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

import org.lockss.account.AccountManager;
import org.lockss.log.L4JLogger;
import org.lockss.app.LockssDaemon;
import org.lockss.config.ConfigManager;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.firewall.DefaultHttpFirewall;
import org.springframework.security.web.firewall.HttpFirewall;

/**
 * Default LOCKSS custom Spring security configurator.
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SpringSecurityConfigurer {

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

  @Bean
  public WebSecurityCustomizer webSecurityCustomizer() {
    return (web) -> web.httpFirewall(allowUrlEncodedSlashHttpFirewall());
  }

  /**
   * Configures the authentication strategy and filter.
   *
   * @param http An HttpSecurity to be configured.
   * @throws Exception if there are problems.
   */
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    log.debug2("Invoked.");

    // Force each and every request to be authenticated.
    http.csrf().disable().authorizeRequests().anyRequest().authenticated();

    log.debug2("Installing auth filter");
    // The Basic authentication filter to be used.
    authFilter = new SpringAuthenticationFilter();
    http.addFilterBefore(authFilter,
        BasicAuthenticationFilter.class);
    log.debug2("Done.");

    return http.build();
  }

  /**
   * This {@link AuthenticationManager} was introduced to suppress the generation of a default password,
   * since excluding {@link SecurityAutoConfiguration} does not appear to work. This is a temporary fix.
   * According to the Spring Security Architecture documentation, an {@link AuthenticationManager} that
   * returns {@code null} was unable to decide the authentication of the {@link Authentication} request.
   * <p>
   * Our authentication is currently handled by a {@link SecurityFilterChain} customized with our
   * {@link SpringAuthenticationFilter}, which defers user authentication to the LOCKSS
   * {@link AccountManager} infrastructure. We should consider refactoring portions of the filter into a
   * custom {@link AuthenticationProvider} or {@link UserDetailsService} to better align with Spring.
   *
   * @see AuthenticationManager
   * @see ProviderManager
   * @see AuthenticationProvider
   * @see UserDetailsService
   * @see SpringAuthenticationFilter
   * @see AccountManager
   */
  @Bean
  public AuthenticationManager authenticationManager() {
    return new UndecidedAuthenticationManager();
  }

  private class UndecidedAuthenticationManager implements AuthenticationManager {
    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
      log.warn("Invoked an AuthenticationManager that was not intended to be used", new Throwable());
      return null;
    }
  };
}
