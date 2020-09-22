/*

Copyright (c) 2018 Board of Trustees of Leland Stanford Jr. University,
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
package org.lockss.spring.base;

import org.lockss.log.L4JLogger;
import org.lockss.spring.converter.LockssHttpEntityMethodProcessor;
import org.lockss.util.rest.multipart.MultipartMessageHttpMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.HttpEntityMethodProcessor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.util.UrlPathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for a Spring-Boot application.
 */
public abstract class BaseSpringBootApplication {

  private static L4JLogger log = L4JLogger.getLogger();

  @org.springframework.beans.factory.annotation.Autowired
  private ApplicationContext appCtx;

  /** make ApplicationContext available to subclasses */
  protected ApplicationContext getApplicationContext() {
    return appCtx;
  }

  /**
   * Sets configuration options common to all the LOCKSS Spring Boot
   * applications.
   */
  protected static void configure() {
    System.setProperty(
        "org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true");
  }

  /**
   * Modifier of the behavior of standard Spring MVC.
   */
  @Configuration
  public static class SpringMvcCustomization extends WebMvcConfigurationSupport {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
      // Prevent Spring from URL-decoding the context path and request URI,
      // as both are returned not URL-decoded by the Servlet API.
      UrlPathHelper urlPathHelper = new UrlPathHelper();
      urlPathHelper.setUrlDecode(false);
      configurer.setUrlPathHelper(urlPathHelper);

      // Prevent Spring from interpreting the end of a URL as a file suffix.
      configurer.setUseSuffixPatternMatch(false);
      configurer.setUseRegisteredSuffixPatternMatch(false);

      // Prevent Spring from thinking that a URL is the same as the same URL
      // with a slash appended to it.
      configurer.setUseTrailingSlashMatch(false);
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer
                                                    configurer) {
      // Prevent Spring from interpreting the end of a URL as a file suffix, or
      // from interpreting a "format=..." parameter for content type
      // specification and use only the Accept header for content type
      // negotiation.
      configurer.favorPathExtension(false)
          .favorParameter(false)
          .ignoreAcceptHeader(false)
          .useJaf(false)
          .ignoreUnknownPathExtensions(false);
    }

    /**
     * Creates a {@link LockssHttpEntityMethodProcessor} with some standard message converters.
     *
     * @return An instance of {@link LockssHttpEntityMethodProcessor}.
     */
    @Bean
    public LockssHttpEntityMethodProcessor createLockssHttpEntityMethodProcessor() {
      // Converters for HTTP entity types to be supported by LockssHttpEntityMethodProcessor
      List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
      messageConverters.add(new StringHttpMessageConverter());
      messageConverters.add(new MappingJackson2HttpMessageConverter());
      messageConverters.add(new AllEncompassingFormHttpMessageConverter());
      messageConverters.add(new MultipartMessageHttpMessageConverter());

      // Add new LockssHttpEntityMethodProcessor to list from WebMvcConfigurationSupport
      return new LockssHttpEntityMethodProcessor(messageConverters, new ContentNegotiationManager());
    }

    /**
     * Creates an {@link ReplacingRequestMappingHandlerAdapter} that replaces {@link HttpEntityMethodProcessor} with
     * {@link LockssHttpEntityMethodProcessor}.
     *
     * @return An instance of {@link ReplacingRequestMappingHandlerAdapter} having the an updated set of return value
     * handlers.
     */
    @Override
    public RequestMappingHandlerAdapter createRequestMappingHandlerAdapter() {
      return new ReplacingRequestMappingHandlerAdapter(lockssHttpEntityMethodProcessor);
    }

    // Used by the overridden methods here
    @Autowired
    LockssHttpEntityMethodProcessor lockssHttpEntityMethodProcessor;

    /**
     * Replaces {@link HttpEntityMethodProcessor} in the default list of return value handlers from
     * {@link RequestMappingHandlerAdapter#getDefaultReturnValueHandlers()} with the provided
     * {@link HandlerMethodReturnValueHandler}.
     * <p>
     * FIXME: Could be generalized
     */
    private static class ReplacingRequestMappingHandlerAdapter extends RequestMappingHandlerAdapter {

      // Handle to replacing instance
      private HandlerMethodReturnValueHandler replacingHandler;

      /**
       * Constructor.
       *
       * @param handler The instance of {@link HandlerMethodReturnValueHandler} to replace
       *                {@link HttpEntityMethodProcessor} with.
       */
      public ReplacingRequestMappingHandlerAdapter(HandlerMethodReturnValueHandler handler) {
        this.replacingHandler = handler;
      }

      /**
       * Calls {@code super.afterPropertiesSet()} then replaces {@link HttpEntityMethodProcessor} with the provided
       * {@link HandlerMethodReturnValueHandler}.
       */
      @Override
      public void afterPropertiesSet() {
        // Allow default return value handlers to be added
        super.afterPropertiesSet();

        // List to contain new set of handlers
        List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();

        for (HandlerMethodReturnValueHandler handler : getReturnValueHandlers()) {
          if (handler instanceof HttpEntityMethodProcessor) {
            // Replace HttpEntityMethodProcessor with LockssHttpEntityMethodProcessor
            handlers.add(replacingHandler);
          } else {
            // Pass-through handler
            handlers.add(handler);
          }
        }

        // Set return value handlers
        setReturnValueHandlers(handlers);
      }

    }

    @Override
    public ExceptionHandlerExceptionResolver createExceptionHandlerExceptionResolver() {
      return new ReplacingExceptionHandlerExceptionResolver(lockssHttpEntityMethodProcessor);
    }

    private static class ReplacingExceptionHandlerExceptionResolver extends ExceptionHandlerExceptionResolver {

      // Handle to replacing instance
      private HandlerMethodReturnValueHandler replacingHandler;

      /**
       * Constructor.
       *
       * @param handler The instance of {@link HandlerMethodReturnValueHandler} to replace
       *                {@link HttpEntityMethodProcessor} with.
       */
      public ReplacingExceptionHandlerExceptionResolver(HandlerMethodReturnValueHandler handler) {
        this.replacingHandler = handler;
      }

      @Override
      public void afterPropertiesSet() {
        // Allow default return value handlers to be added
        super.afterPropertiesSet();

        // List to contain new set of handlers
        List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();

        for (HandlerMethodReturnValueHandler handler : getReturnValueHandlers().getHandlers()) {
          if (handler instanceof HttpEntityMethodProcessor) {
            // Replace HttpEntityMethodProcessor with LockssHttpEntityMethodProcessor
            handlers.add(replacingHandler);
          } else {
            // Pass-through handler
            handlers.add(handler);
          }
        }

        // Set return value handlers
        setReturnValueHandlers(handlers);
      }
    }
  }
}
