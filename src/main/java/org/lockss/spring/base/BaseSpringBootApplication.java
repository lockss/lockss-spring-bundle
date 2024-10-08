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
import org.lockss.spring.error.SpringControllerAdvice;
import org.lockss.util.rest.multipart.MultipartMessageHttpMessageConverter;
import org.lockss.util.time.TimeBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.HttpEntityMethodProcessor;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.util.UrlPathHelper;
import org.apache.catalina.webresources.TomcatURLStreamHandlerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Base class for a Spring-Boot application.
 */
public abstract class BaseSpringBootApplication {

  private static L4JLogger log = L4JLogger.getLogger();

  @Autowired
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

  @ControllerAdvice
  public static class BaseServiceControllerAdvice extends SpringControllerAdvice {
    // Intentionally left blank
  }

  @Configuration
  /** Add LOCKSS URLStreamHandlerFactory to Tomcat's list of user
   * factories,  This will also prevent UrlManager from calling
   * URL.setURLStreamHandlerFactory() */
  public static class SetupUrlStreamHandlerFactory {
    @Bean
    public java.net.URLStreamHandlerFactory doit() {
    TomcatURLStreamHandlerFactory.getInstance().addUserFactory(new org.lockss.daemon.UrlManager.LockssUrlFactory());
    return null;
    }
  }

  /**
   * Modifier of the behavior of standard Spring MVC.
   */
  // FIXME: This was a mistake; revert (and make sure to update our clients)
  @Configuration
  public static class SpringMvcCustomization implements WebMvcConfigurer {
    @Bean
    public DefaultErrorAttributes errorAttributes() {
      return new DefaultErrorAttributes() {
        @Override
        public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
          Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);
          attributes.remove("timestamp");
          attributes.put("timestamp", TimeBase.nowMs());
          return attributes;
        }
      };
    }

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
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
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

//    @Bean
//    public ExceptionHandlerExceptionResolver createLockssExceptionHandlerExceptionResolver() {
//      return new LockssExceptionHandlerExceptionResolver();
//    }

//    @Bean
//    public RequestMappingHandlerAdapter createLockssRequestMappingHandlerAdapter(
//        FormattingConversionService fcs,
//        @Qualifier("mvcValidator") Validator validator) {
//
//      RequestMappingHandlerAdapter adapter = new LockssRequestMappingHandlerAdapter();
//
//      ConfigurableWebBindingInitializer initializer = new ConfigurableWebBindingInitializer();
//      initializer.setConversionService(fcs);
//      initializer.setValidator(validator);
//      initializer.setMessageCodesResolver(getMessageCodesResolver());
//
//      adapter.setWebBindingInitializer(initializer);
//
//      return adapter;
//    }

//    @Bean
//    public RequestMappingHandlerAdapter modifyRequestMappingHandlerAdapter(RequestMappingHandlerAdapter adapter) {
//
//      adapter.setReturnValueHandlers(
//          substituteHttpEntityMethodProcessor(adapter.getReturnValueHandlers(), adapter.getMessageConverters())
//      );
//
//      return adapter;
//    }

    private class LockssExceptionHandlerExceptionResolver extends ExceptionHandlerExceptionResolver {
      @Autowired
      HttpMessageConverters msgConverters;

      @Override
      public void afterPropertiesSet() {
        super.afterPropertiesSet();
        HandlerMethodReturnValueHandlerComposite composite = getReturnValueHandlers();
        setReturnValueHandlers(
            substituteHttpEntityMethodProcessor(
                composite.getHandlers(), msgConverters.getConverters()));
      }
    }

    private class LockssRequestMappingHandlerAdapter extends RequestMappingHandlerAdapter {
      @Autowired
      HttpMessageConverters msgConverters;

      @Override
      public void afterPropertiesSet() {
        super.afterPropertiesSet();
        setReturnValueHandlers(
            substituteHttpEntityMethodProcessor(
                getReturnValueHandlers(), msgConverters.getConverters()));
      }
    }

    private static List<HttpMessageConverter<?>> injectMultipartMessageConverter(List<HttpMessageConverter<?>> messageConverters) {
      // List to contain new set of HTTP message converters
      List<HttpMessageConverter<?>> converters = new ArrayList<>();

      // Inject our MultipartMessageHttpMessageConverter
      for (HttpMessageConverter converter : messageConverters) {
        if (converter instanceof AllEncompassingFormHttpMessageConverter){
          converters.add(converter);
          converters.add(new MultipartMessageHttpMessageConverter());
        } else {
          // Pass-through message converter
          converters.add(converter);
        }
      }

      return converters;
    }

    private static List<HandlerMethodReturnValueHandler> substituteHttpEntityMethodProcessor(
        List<HandlerMethodReturnValueHandler> returnValueHandlers,
        List<HttpMessageConverter<?>> messageConverters) {

      // List to contain new set of return value handlers
      List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();

      // Create new LockssHttpEntityMethodProcessor with customized list of HTTP message converters
      LockssHttpEntityMethodProcessor lockssHandler =
          new LockssHttpEntityMethodProcessor(
              injectMultipartMessageConverter(messageConverters),
              new ContentNegotiationManager());

      // Replace HttpEntityMethodProcessor with LockssHttpEntityMethodProcessor
      for (HandlerMethodReturnValueHandler handler : returnValueHandlers) {
        if (handler instanceof HttpEntityMethodProcessor) {
          handlers.add(lockssHandler);
        } else {
          // Pass-through return value handler
          handlers.add(handler);
        }
      }

      // Return modified list of return value handlers
      return handlers;
    }

  }

}
