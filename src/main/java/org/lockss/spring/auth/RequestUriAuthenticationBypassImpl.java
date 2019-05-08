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

/**
 * Default LOCKSS authentication bypass implementation.
 */
public class RequestUriAuthenticationBypassImpl
    implements RequestUriAuthenticationBypass {

  private final static L4JLogger log = L4JLogger.getLogger();

  /**
   * Provides an indication of whether access to a given URI with a given method
   * is available to everybody.
   *
   * @param httpMethodName A String with the request method.
   * @param requestUri A String with the request URI.
   * @return <code>true</code> if this request is available to everybody,
   * <code>false</code> otherwise.
   */
  public boolean isWorldReachable(String httpMethodName, String requestUri) {
    log.debug2("httpMethodName = {}", httpMethodName);
    log.debug2("requestUri = {}", requestUri);

    // Determine whether it is world-reachable.
    boolean result = ("GET".equals(httpMethodName)
        && ("/status".equals(requestUri)
        || "/v2/api-docs".equals(requestUri)
        || "/swagger-ui.html".equals(requestUri)
        || requestUri.startsWith("/swagger-resources")
        || requestUri.startsWith("/webjars/springfox-swagger-ui"))
    );

    log.debug2("result = {}", result);
    return result;
  }
}
