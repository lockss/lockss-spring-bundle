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
package org.lockss.laaws.base;

import org.lockss.app.LockssDaemon;
import org.lockss.config.ConfigManager;
import org.lockss.log.L4JLogger;
import org.lockss.util.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/**
 * Base class for LOCKSS Spring service implementations
 */
@Service
public class BaseSpringApiServiceImpl {
  private static L4JLogger log = L4JLogger.getLogger();

  /** Amount of time services will wait for the underlying daemon to become
   * ready before returning a 503 Unavailable response.  Can only be set in
   * a Spring config file. */
  public static String PARAM_READY_WAIT_TIME = "org.lockss.service.readyWait";
  public static long DEFAULT_READY_WAIT_TIME = Constants.MINUTE;

  /** Amount of time services will wait for the initial config load to
   * complete, before returning a 503 Unavailable response.  Can only be
   * set in a Spring config file. */
  public static String PARAM_CONFIG_WAIT_TIME = "org.lockss.service.configWait";
  public static long DEFAULT_CONFIG_WAIT_TIME = Constants.MINUTE;


  @Autowired
  Environment env;			// Spring Environment, access to
					// config props

  protected long getReadyWaitTime() {
    return getWaitTime(env.getProperty(PARAM_READY_WAIT_TIME),
		       DEFAULT_READY_WAIT_TIME);
  }

  protected long getConfigWaitTime() {
    return getWaitTime(env.getProperty(PARAM_CONFIG_WAIT_TIME),
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
   * Return the LockssDaemon instance.
   *
   * @return a ConfigManager with the configuration manager.
   */
  private LockssDaemon getLockssDaemon() {
    return LockssDaemon.getLockssDaemon();
  }

}
