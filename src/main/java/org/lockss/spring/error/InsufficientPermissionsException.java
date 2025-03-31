package org.lockss.spring.error;

public class InsufficientPermissionsException extends RuntimeException {
  public InsufficientPermissionsException(String message) {
    super(message);
  }
}
