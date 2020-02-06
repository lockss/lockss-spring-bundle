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
package org.lockss.spring.status;

import org.lockss.util.rest.status.ApiStatus;
import org.lockss.log.L4JLogger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.lockss.app.*;
import org.lockss.util.time.*;

/**
 * LOCKSS base controller for Spring REST web services.
 */
public abstract class SpringLockssBaseApiController
    implements SpringLockssBaseApi {

  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * Provides the status.
   *
   * @return a {@code ResponseEntity<ApiStatus>} with the status.
   */
  @Override
  @RequestMapping(value = "/status",
      produces = {"application/json"},
      method = RequestMethod.GET)
  public ResponseEntity<ApiStatus> getStatus() {
    log.debug2("Invoked.");

    try {
      ApiStatus result = getApiStatus();
      log.trace("result = {}", result);

      return new ResponseEntity<ApiStatus>(result, HttpStatus.OK);
    } catch (Exception e) {
      String message = "Cannot getStatus()";
      log.error(message, e);
      throw new RuntimeException(message);
    }
  }

  /**
   * Provides the status object.
   *
   * @return an ApiStatus with the status.
   */
  @Override
  public abstract ApiStatus getApiStatus();

  protected ApiStatus getDefaultApiStatus() {
    LockssDaemon daemon = LockssDaemon.getLockssDaemon();
    return new ApiStatus("swagger/swagger.yaml")
      .setReady(daemon.isAppRunning())
      .setReadyTime(daemon.getReadyTime())
      .setPluginsReady(daemon.areLoadablePluginsReady());
  }
}
