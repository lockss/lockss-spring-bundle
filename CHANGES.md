# `lockss-spring-bundle` Release Notes

## 2.16.0 (LOCKSS 2.0.91-beta2)

### Features

* Merged MDQ and MDX services into a single Metadata service.
* Removed container-only access restriction to `/users` and `/usernames` endpoints.
* Added role-based authorization checking in REST services.
* Unauthenticated read access is now restricted to local addresses (loopback or Kubernetes subnet).
* Unauthenticated-allowed paths are checked only when no credentials are supplied.

### Fixes

* Added `SpringBugFixStringToEnumConverterFactory` to work around Spring's treatment of enum values, 
  registered in `BaseSpringBootApplication`.
* Improved error reporting for invalid credentials.
* Improved error handling: revisited exception handlers and included root cause in error responses.
* Avoided NPEs when a Spring Application Context is not provided.
* Used MockServer to mock Config Service behavior in tests; introduced related test infrastructure.


## Changes Since 2.0.10.0

*   Switched to a 3-part version numbering scheme.

## 2.0.10.0

### Features

*   ...

### Fixes

*   ...

## 2.0.9.0

### Features

*   REST services authenticate, clients provide credentials
*   Improved startup coordination and ready waiting of all services and databases
*   Improved coordination of initial plugin registry crawls
