/*
 * Copyright (c) 2018, Board of Trustees of Leland Stanford Jr. University,
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.lockss.spring.error;

import org.lockss.log.L4JLogger;
import org.lockss.util.rest.RestResponseErrorBody;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping(produces = "application/vnd.error+json")
public class SpringControllerAdvice {

  private static L4JLogger log = L4JLogger.getLogger();

  /**
   * Handles a custom LOCKSS REST service exception.
   *
   * @param lrse A LockssRestServiceException with the details of the problem.
   * @return a ResponseEntity<RestResponseErrorBody> with the error response in
   * JSON format with media type {@code application/vnd.error+json}.
   */
  @ExceptionHandler(LockssRestServiceException.class)
  public ResponseEntity<RestResponseErrorBody.RestResponseError> handler(final LockssRestServiceException lrse) {

    // Content-Type hint to LockssHttpEntityMethodProcessor
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    return new ResponseEntity<>(lrse.toRestResponseError(), headers, lrse.getHttpStatus());
  }

  /**
   * Handles UnsupportedOperationException
   *
   * @param e an UnsupportedOperationException
   * @return a ResponseEntity<RestResponseErrorBody> with the error response in
   * JSON format with media type {@code application/vnd.error+json}.
   */
  @ExceptionHandler(UnsupportedOperationException.class)
  public ResponseEntity<RestResponseErrorBody.RestResponseError> handler(final UnsupportedOperationException e) {

    // Content-Type hint to LockssHttpEntityMethodProcessor
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    RestResponseErrorBody.RestResponseError rre =
      new RestResponseErrorBody.RestResponseError(e.getMessage(),
                                                  e.getClass().toString());
    return new ResponseEntity<>(rre, headers, HttpStatus.NOT_IMPLEMENTED);
  }

  /**
   * Handles any other unhandled exception as a last resort.
   *
   * @param e An Exception with the exception not handled by other exception
   * handler methods.
   * @return a ResponseEntity<RestResponseErrorBody> with the error response in
   * JSON format with media type {@code application/vnd.error+json}.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<RestResponseErrorBody.RestResponseError> defaultHandler(Exception e) {
    log.error("Caught otherwise unhandled exception", e);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    RestResponseErrorBody.RestResponseError rre =
        new RestResponseErrorBody.RestResponseError(e.getMessage(), e.getClass().toString());

    return new ResponseEntity<>(rre, headers, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
