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

import org.lockss.util.rest.RestResponseErrorBody;
import org.lockss.util.rest.exception.LockssRestHttpException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Unchecked exception to be thrown by REST service controllers and turned into
 * a meaningful error message by the standard exception handling.
 */
public class LockssRestServiceException extends RuntimeException {

  private static final long serialVersionUID = 4569911319063540372L;

  // The HTTP response status.
  private HttpStatus httpStatus;

  // The HTTP request parsed by the service. 
  private String parsedRequest;

  // The UTC date and time of the exception.
  private LocalDateTime utcTimestamp = LocalDateTime.now(ZoneOffset.UTC);

  // The servlet path whose controller method threw this exception
  private String servletPath;

  // The type of error experienced by the service
  private LockssRestHttpException.ServerErrorType serverErrorType =
      LockssRestHttpException.ServerErrorType.UNSPECIFIED_ERROR;

  /**
   * Default constructor.
   */
  public LockssRestServiceException() {
    super();
  }

  /**
   * Constructor.
   *
   * @param message A String with the detail message.
   */
  public LockssRestServiceException(String message) {
    super(message);
  }

  /**
   * Constructor.
   *
   * @param message A String with the detail message.
   * @param cause A Throwable with the cause.
   */
  public LockssRestServiceException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructor.
   *
   * @param cause A Throwable with the cause.
   */
  public LockssRestServiceException(Throwable cause) {
    super(cause);
  }

  /**
   * Constructor.
   *
   * @param message A String with the detail message.
   * @param cause A Throwable with the cause.
   * @param enableSuppression A boolean indicating whether suppression is enabled.
   * @param writableStackTrace A boolean indicating whether the stack trace should be writable.
   */
  protected LockssRestServiceException(String message, Throwable cause,
      boolean enableSuppression,
      boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
  }

  /**
   * Constructor.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param message A String with the detail message.
   */
  public LockssRestServiceException(HttpStatus httpStatus, String message) {
    super(message);
    this.httpStatus = httpStatus;
  }

  /**
   * Constructor.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param message A String with the detail message.
   * @param parsedRequest A String with a copy of the parsed HTTP request contents.
   */
  public LockssRestServiceException(HttpStatus httpStatus, String message,
      String parsedRequest) {
    super(message);
    this.httpStatus = httpStatus;
    this.parsedRequest = parsedRequest;
  }

  /**
   * Constructor.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param message A String with the detail message.
   * @param parsedRequest A String with a copy of the parsed HTTP request contents.
   * @param utcTimestamp A LocalDateTime with the exception date and time.
   */
  public LockssRestServiceException(HttpStatus httpStatus, String message,
      String parsedRequest, LocalDateTime utcTimestamp) {
    super(message);
    this.httpStatus = httpStatus;
    this.parsedRequest = parsedRequest;
    this.utcTimestamp = utcTimestamp;
  }

  /**
   * Constructor.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param message A String with the detail message.
   * @param cause A Throwable with the cause.
   */
  public LockssRestServiceException(HttpStatus httpStatus, String message,
      Throwable cause) {
    super(message, cause);
    this.httpStatus = httpStatus;
  }

  /**
   * Constructor.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param message A String with the detail message.
   * @param cause A Throwable with the cause.
   * @param parsedRequest A String with a copy of the parsed HTTP request contents.
   */
  public LockssRestServiceException(HttpStatus httpStatus, String message,
      Throwable cause, String parsedRequest) {
    super(message, cause);
    this.httpStatus = httpStatus;
    this.parsedRequest = parsedRequest;
  }

  /**
   * Constructor.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param message A String with the detail message.
   * @param cause A Throwable with the cause.
   * @param parsedRequest A String with a copy of the parsed HTTP request contents.
   * @param utcTimestamp A LocalDateTime with the exception date and time.
   */
  public LockssRestServiceException(HttpStatus httpStatus, String message,
      Throwable cause, String parsedRequest, LocalDateTime utcTimestamp) {
    super(message, cause);
    this.httpStatus = httpStatus;
    this.parsedRequest = parsedRequest;
    this.utcTimestamp = utcTimestamp;
  }

  /**
   * Constructor.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param cause A Throwable with the cause.
   */
  public LockssRestServiceException(HttpStatus httpStatus, Throwable cause) {
    super(cause);
    this.httpStatus = httpStatus;
  }

  /**
   * Constructor.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param cause A Throwable with the cause.
   * @param parsedRequest A String with a copy of the parsed HTTP request contents.
   */
  public LockssRestServiceException(HttpStatus httpStatus, Throwable cause,
      String parsedRequest) {
    super(cause);
    this.httpStatus = httpStatus;
    this.parsedRequest = parsedRequest;
  }

  /**
   * Constructor.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param cause A Throwable with the cause.
   * @param parsedRequest A String with a copy of the parsed HTTP request contents.
   * @param utcTimestamp A LocalDateTime with the exception date and time.
   */
  public LockssRestServiceException(HttpStatus httpStatus, Throwable cause,
      String parsedRequest, LocalDateTime utcTimestamp) {
    super(cause);
    this.httpStatus = httpStatus;
    this.parsedRequest = parsedRequest;
    this.utcTimestamp = utcTimestamp;
  }

  /**
   * Constructor.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param message A String with the detail message.
   * @param cause A Throwable with the cause.
   * @param enableSuppression A boolean indicating whether suppression is enabled.
   * @param writableStackTrace A boolean indicating whether the stack trace should be writable.
   */
  protected LockssRestServiceException(HttpStatus httpStatus, String message,
      Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    this.httpStatus = httpStatus;
  }

  /**
   * Constructor.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param message A String with the detail message.
   * @param cause A Throwable with the cause.
   * @param enableSuppression A boolean indicating whether suppression is enabled.
   * @param writableStackTrace A boolean indicating whether the stack trace should be writable.
   * @param parsedRequest A String with a copy of the parsed HTTP request contents.
   */
  protected LockssRestServiceException(HttpStatus httpStatus, String message,
      Throwable cause, boolean enableSuppression, boolean writableStackTrace,
      String parsedRequest) {
    super(message, cause, enableSuppression, writableStackTrace);
    this.httpStatus = httpStatus;
    this.parsedRequest = parsedRequest;
  }

  /**
   * Constructor.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param message A String with the detail message.
   * @param cause A Throwable with the cause.
   * @param enableSuppression A boolean indicating whether suppression is enabled.
   * @param writableStackTrace A boolean indicating whether the stack trace should be writable.
   * @param parsedRequest A String with a copy of the parsed HTTP request contents.
   * @param utcTimestamp A LocalDateTime with the exception date and time.
   */
  protected LockssRestServiceException(HttpStatus httpStatus, String message,
      Throwable cause, boolean enableSuppression, boolean writableStackTrace,
      String parsedRequest, LocalDateTime utcTimestamp) {
    super(message, cause, enableSuppression, writableStackTrace);
    this.httpStatus = httpStatus;
    this.parsedRequest = parsedRequest;
    this.utcTimestamp = utcTimestamp;
  }

  /**
   * Constructor.
   *
   * @param serverErrorType A {@link LockssRestHttpException.ServerErrorType} with the type of server error.
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param message A String with the detail message.
   * @param cause A Throwable with the cause.
   * @param parsedRequest A String with a copy of the parsed HTTP request contents.
   */
  public LockssRestServiceException(
      LockssRestHttpException.ServerErrorType serverErrorType,
      HttpStatus httpStatus, String message,
      Throwable cause, String parsedRequest) {

    this(httpStatus, message, cause, parsedRequest);
    this.setServerErrorType(serverErrorType);
  }

  /**
   * Constructor.
   *
   * @param serverErrorType A {@link LockssRestHttpException.ServerErrorType} with the type of server error.
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @param message A String with the detail message.
   * @param parsedRequest A String with a copy of the parsed HTTP request contents.
   */
  public LockssRestServiceException(LockssRestHttpException.ServerErrorType serverErrorType,
                                    HttpStatus httpStatus, String message, String parsedRequest) {

    this(httpStatus, message, parsedRequest);
    this.setServerErrorType(serverErrorType);
  }

  /**
   * Provides the HTTP response status.
   *
   * @return an HttpStatus with the HTTP response status.
   */
  public HttpStatus getHttpStatus() {
    return httpStatus;
  }

  /**
   * Sets the HTTP response status.
   *
   * @param httpStatus An HttpStatus with the HTTP response status.
   * @return a LockssRestServiceException with this object.
   */
  public LockssRestServiceException setHttpStatus(HttpStatus httpStatus) {
    this.httpStatus = httpStatus;
    return this;
  }

  /**
   * Provides a copy of the parsed HTTP request contents.
   *
   * @return a String with a copy of the parsed HTTP request contents.
   */
  public String getParsedRequest() {
    return parsedRequest;
  }

  /**
   * Sets the copy of the parsed HTTP request contents.
   *
   * @param parsedRequest A String with a copy of the parsed HTTP request contents.
   * @return a LockssRestServiceException with this object.
   */
  public LockssRestServiceException setParsedRequest(String parsedRequest) {
    this.parsedRequest = parsedRequest;
    return this;
  }

  /**
   * Provides the exception date and time.
   *
   * @return a LocalDateTime with the exception date and time.
   */
  public LocalDateTime getUtcTimestamp() {
    return utcTimestamp;
  }

  /**
   * Sets the exception date and time.
   *
   * @param utcTimestamp A LocalDateTime with the exception date and time.
   * @return a LockssRestServiceException with this object.
   */
  public LockssRestServiceException setUtcTimestamp(LocalDateTime utcTimestamp) {
    this.utcTimestamp = utcTimestamp;
    return this;
  }

  /**
   * Returns the type of server error experienced or {@code UNSPECIFIED_ERROR}, if it was not specified.
   * See {@link LockssRestHttpException.ServerErrorType} for details.
   *
   * @return
   */
  public LockssRestHttpException.ServerErrorType getServerErrorType() {
    return serverErrorType;
  }

  /**
   * Sets the type of server error this {@link LockssRestServiceException} represents.
   *
   * @param serverErrorType
   * @return This {@link LockssRestServiceException} for chaining.
   */
  public LockssRestServiceException setServerErrorType(LockssRestHttpException.ServerErrorType serverErrorType) {
    this.serverErrorType = serverErrorType;
    return this;
  }

  /**
   * Returns the servlet path of the controller method that threw this {@link LockssRestServiceException}.
   *
   * @return A {@link String} containing the servlet path.
   */
  public String getServletPath() {
    return servletPath;
  }

  /**
   * Set the servlet path.
   *
   * @param servletPath A {@link String} containing the servlet path.
   * @return This {@link LockssRestServiceException} for chaining.
   */
  public LockssRestServiceException setServletPath(String servletPath) {
    this.servletPath = servletPath;
    return this;
  }

  /**
   * Transforms this {@link LockssRestServiceException} into a {@link RestResponseErrorBody.RestResponseError} for
   * serialization.
   */
  public RestResponseErrorBody.RestResponseError toRestResponseError() {
    return toRestResponseError(this);
  }

  /**
   * Transforms a {@link LockssRestServiceException} into a {@link RestResponseErrorBody.RestResponseError} for
   * serialization.
   *
   * @param lrse The {@link LockssRestServiceException} to transform.
   * @return A {@link RestResponseErrorBody.RestResponseError} populated from the {@link LockssRestServiceException}.
   */
  public static RestResponseErrorBody.RestResponseError toRestResponseError(LockssRestServiceException lrse) {
    // Get cause of LockssRestServiceException
    Throwable cause = lrse.getCause();

    // Use LRSE (self) as cause if a cause was not specified
    if (cause == null) {
      cause = lrse;
    }

    // Create and poulate RestResponseError from LRSE
    return new RestResponseErrorBody.RestResponseError()
        .setTimestamp(lrse.getUtcTimestamp().toEpochSecond(ZoneOffset.UTC))
        .setStatus(lrse.getHttpStatus().value())
        .setError(cause.toString())
        .setException(cause.getClass().getName())
        .setMessage(cause.getMessage())
        .setPath(lrse.getServletPath())
        .setServerErrorType(lrse.getServerErrorType());
  }
}
