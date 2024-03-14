/*
 * Copyright (c) 2019-2020, Board of Trustees of Leland Stanford Jr. University,
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
 *
 * Additionally, portions of this code are copyright the Spring Framework:
 *
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lockss.spring.converter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

import org.lockss.util.SetUtil;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.http.*;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.accept.PathExtensionContentNegotiationStrategy;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.*;
import org.springframework.web.util.UrlPathHelper;

import jakarta.servlet.http.HttpServletRequest;

/**
 * This class was created with code from {@link HttpEntityMethodProcessor} and the portions of
 * {@link AbstractMessageConverterMethodProcessor} required to customize the behavior of
 * {@link AbstractMessageConverterMethodProcessor#writeWithMessageConverters(Object, MethodParameter, NativeWebRequest)}
 * so that it honors the Content-Type provided by a Spring controller or controller advice.
 */
public class LockssHttpEntityMethodProcessor extends AbstractMessageConverterMethodProcessor {

	// The following code is from HttpEntityMethodProcessor

	private static final Set<HttpMethod> SAFE_METHODS =
			SetUtil.set(HttpMethod.GET, HttpMethod.HEAD);

	private ContentNegotiationManager contentNegotiationManager = null;
	private PathExtensionContentNegotiationStrategy pathStrategy;
	private Set<String> safeExtensions = new HashSet<String>();

	/* Extensions associated with the built-in message converters */
	private static final Set<String> WHITELISTED_EXTENSIONS = new HashSet<String>(Arrays.asList(
			"txt", "text", "yml", "properties", "csv",
			"json", "xml", "atom", "rss",
			"png", "jpe", "jpeg", "jpg", "gif", "wbmp", "bmp"));

	/**
	 * Basic constructor with converters only. Suitable for resolving
	 * {@code HttpEntity}. For handling {@code ResponseEntity} consider also
	 * providing a {@code ContentNegotiationManager}.
	 */
	public LockssHttpEntityMethodProcessor(List<HttpMessageConverter<?>> converters) {
		super(converters);
	}

	/**
	 * Basic constructor with converters and {@code ContentNegotiationManager}.
	 * Suitable for resolving {@code HttpEntity} and handling {@code ResponseEntity}
	 * without {@code Request~} or {@code ResponseBodyAdvice}.
	 */
	public LockssHttpEntityMethodProcessor(List<HttpMessageConverter<?>> converters,
			ContentNegotiationManager manager) {

		super(converters, manager);

		this.contentNegotiationManager = manager;

		this.pathStrategy = initPathStrategy(this.contentNegotiationManager);
		this.safeExtensions.addAll(this.contentNegotiationManager.getAllFileExtensions());
		this.safeExtensions.addAll(WHITELISTED_EXTENSIONS);
	}

	/**
	 * Complete constructor for resolving {@code HttpEntity} method arguments.
	 * For handling {@code ResponseEntity} consider also providing a
	 * {@code ContentNegotiationManager}.
	 * @since 4.2
	 */
	public LockssHttpEntityMethodProcessor(List<HttpMessageConverter<?>> converters,
			List<Object> requestResponseBodyAdvice) {

		super(converters, null, requestResponseBodyAdvice);
	}

	/**
	 * Complete constructor for resolving {@code HttpEntity} and handling
	 * {@code ResponseEntity}.
	 */
	public LockssHttpEntityMethodProcessor(List<HttpMessageConverter<?>> converters,
			ContentNegotiationManager manager, List<Object> requestResponseBodyAdvice) {

		super(converters, manager, requestResponseBodyAdvice);

		this.contentNegotiationManager = manager;

		this.pathStrategy = initPathStrategy(this.contentNegotiationManager);
		this.safeExtensions.addAll(this.contentNegotiationManager.getAllFileExtensions());
		this.safeExtensions.addAll(WHITELISTED_EXTENSIONS);
	}


	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return (HttpEntity.class == parameter.getParameterType() ||
				RequestEntity.class == parameter.getParameterType());
	}

	@Override
	public boolean supportsReturnType(MethodParameter returnType) {
		return (HttpEntity.class.isAssignableFrom(returnType.getParameterType()) &&
				!RequestEntity.class.isAssignableFrom(returnType.getParameterType()));
	}

	@Override
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory)
			throws IOException, HttpMediaTypeNotSupportedException {

		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		Type paramType = getHttpEntityType(parameter);
		if (paramType == null) {
			throw new IllegalArgumentException("HttpEntity parameter '" + parameter.getParameterName() +
					"' in method " + parameter.getMethod() + " is not parameterized");
		}

		Object body = readWithMessageConverters(webRequest, parameter, paramType);
		if (RequestEntity.class == parameter.getParameterType()) {
			return new RequestEntity<Object>(body, inputMessage.getHeaders(),
					inputMessage.getMethod(), inputMessage.getURI());
		}
		else {
			return new HttpEntity<Object>(body, inputMessage.getHeaders());
		}
	}

	private Type getHttpEntityType(MethodParameter parameter) {
		Assert.isAssignable(HttpEntity.class, parameter.getParameterType());
		Type parameterType = parameter.getGenericParameterType();
		if (parameterType instanceof ParameterizedType) {
			ParameterizedType type = (ParameterizedType) parameterType;
			if (type.getActualTypeArguments().length != 1) {
				throw new IllegalArgumentException("Expected single generic parameter on '" +
						parameter.getParameterName() + "' in method " + parameter.getMethod());
			}
			return type.getActualTypeArguments()[0];
		}
		else if (parameterType instanceof Class) {
			return Object.class;
		}
		else {
			return null;
		}
	}

	@Override
	public void handleReturnValue(Object returnValue, MethodParameter returnType,
			ModelAndViewContainer mavContainer, NativeWebRequest webRequest) throws Exception {

		mavContainer.setRequestHandled(true);
		if (returnValue == null) {
			return;
		}

		ServletServerHttpRequest inputMessage = createInputMessage(webRequest);
		ServletServerHttpResponse outputMessage = createOutputMessage(webRequest);

		Assert.isInstanceOf(HttpEntity.class, returnValue);
		HttpEntity<?> responseEntity = (HttpEntity<?>) returnValue;

		HttpHeaders outputHeaders = outputMessage.getHeaders();
		HttpHeaders entityHeaders = responseEntity.getHeaders();
		if (!entityHeaders.isEmpty()) {
			for (Map.Entry<String, List<String>> entry : entityHeaders.entrySet()) {
				if (HttpHeaders.VARY.equals(entry.getKey()) && outputHeaders.containsKey(HttpHeaders.VARY)) {
					List<String> values = getVaryRequestHeadersToAdd(outputHeaders, entityHeaders);
					if (!values.isEmpty()) {
						outputHeaders.setVary(values);
					}
				}
				else {
					outputHeaders.put(entry.getKey(), entry.getValue());
				}
			}
		}

		if (responseEntity instanceof ResponseEntity) {
			int returnStatus = ((ResponseEntity<?>) responseEntity).getStatusCodeValue();
			outputMessage.getServletResponse().setStatus(returnStatus);
			if (returnStatus == 200) {
				if (SAFE_METHODS.contains(inputMessage.getMethod())
						&& isResourceNotModified(inputMessage, outputMessage)) {
					// Ensure headers are flushed, no body should be written.
					outputMessage.flush();
					// Skip call to converters, as they may update the body.
					return;
				}
			}
		}

		// Try even with null body. ResponseBodyAdvice could get involved.
		writeWithMessageConverters(responseEntity.getBody(), returnType, inputMessage, outputMessage);

		// Ensure headers are flushed even if no body was written.
		outputMessage.flush();
	}

	private List<String> getVaryRequestHeadersToAdd(HttpHeaders responseHeaders, HttpHeaders entityHeaders) {
		List<String> entityHeadersVary = entityHeaders.getVary();
		List<String> vary = responseHeaders.get(HttpHeaders.VARY);
		if (vary != null) {
			List<String> result = new ArrayList<String>(entityHeadersVary);
			for (String header : vary) {
				for (String existing : StringUtils.tokenizeToStringArray(header, ",")) {
					if ("*".equals(existing)) {
						return Collections.emptyList();
					}
					for (String value : entityHeadersVary) {
						if (value.equalsIgnoreCase(existing)) {
							result.remove(value);
						}
					}
				}
			}
			return result;
		}
		return entityHeadersVary;
	}

	private boolean isResourceNotModified(ServletServerHttpRequest inputMessage, ServletServerHttpResponse outputMessage) {
		ServletWebRequest servletWebRequest =
				new ServletWebRequest(inputMessage.getServletRequest(), outputMessage.getServletResponse());
		HttpHeaders responseHeaders = outputMessage.getHeaders();
		String etag = responseHeaders.getETag();
		long lastModifiedTimestamp = responseHeaders.getLastModified();
		if (inputMessage.getMethod() == HttpMethod.GET || inputMessage.getMethod() == HttpMethod.HEAD) {
			responseHeaders.remove(HttpHeaders.ETAG);
			responseHeaders.remove(HttpHeaders.LAST_MODIFIED);
		}

		return servletWebRequest.checkNotModified(etag, lastModifiedTimestamp);
	}

	@Override
	protected Class<?> getReturnValueType(Object returnValue, MethodParameter returnType) {
		if (returnValue != null) {
			return returnValue.getClass();
		}
		else {
			Type type = getHttpEntityType(returnType);
			type = (type != null ? type : Object.class);
			return ResolvableType.forMethodParameter(returnType, type).resolve(Object.class);
		}
	}

	// The following code is from AbstractMessageConverterMethodProcessor:

	/**
	 * Return the generic type of the {@code returnType} (or of the nested type
	 * if it is an {@link HttpEntity}).
	 */
	private Type getGenericType(MethodParameter returnType) {
		if (HttpEntity.class.isAssignableFrom(returnType.getParameterType())) {
			return ResolvableType.forType(returnType.getGenericParameterType()).getGeneric(0).getType();
		}
		else {
			return returnType.getGenericParameterType();
		}
	}

	private List<MediaType> getAcceptableMediaTypes(HttpServletRequest request) throws HttpMediaTypeNotAcceptableException {
		List<MediaType> mediaTypes = this.contentNegotiationManager.resolveMediaTypes(new ServletWebRequest(request));
		return (mediaTypes.isEmpty() ? Collections.singletonList(MediaType.ALL) : mediaTypes);
	}

	/**
	 * Return the more specific of the acceptable and the producible media types
	 * with the q-value of the former.
	 */
	private MediaType getMostSpecificMediaType(MediaType acceptType, MediaType produceType) {
		MediaType produceTypeToUse = produceType.copyQualityValue(acceptType);
		return (MediaType.SPECIFICITY_COMPARATOR.compare(acceptType, produceTypeToUse) <= 0 ? acceptType : produceTypeToUse);
	}

	private static final MediaType MEDIA_TYPE_APPLICATION = new MediaType("application");
	private static final UrlPathHelper RAW_URL_PATH_HELPER = new UrlPathHelper();
	private static final UrlPathHelper DECODING_URL_PATH_HELPER = new UrlPathHelper();

	private static final Set<String> WHITELISTED_MEDIA_BASE_TYPES = new HashSet<String>(
			Arrays.asList("audio", "image", "video"));

	/**
	 * Check if the path has a file extension and whether the extension is
	 * either {@link #WHITELISTED_EXTENSIONS whitelisted} or explicitly
	 * {@link ContentNegotiationManager#getAllFileExtensions() registered}.
	 * If not, and the status is in the 2xx range, a 'Content-Disposition'
	 * header with a safe attachment file name ("f.txt") is added to prevent
	 * RFD exploits.
	 */
	private void addContentDispositionHeader(ServletServerHttpRequest request, ServletServerHttpResponse response) {
		HttpHeaders headers = response.getHeaders();
		if (headers.containsKey(HttpHeaders.CONTENT_DISPOSITION)) {
			return;
		}

		try {
			int status = response.getServletResponse().getStatus();
			if (status < 200 || status > 299) {
				return;
			}
		}
		catch (Throwable ex) {
			// ignore
		}

		HttpServletRequest servletRequest = request.getServletRequest();
		String requestUri = RAW_URL_PATH_HELPER.getOriginatingRequestUri(servletRequest);

		int index = requestUri.lastIndexOf('/') + 1;
		String filename = requestUri.substring(index);
		String pathParams = "";

		index = filename.indexOf(';');
		if (index != -1) {
			pathParams = filename.substring(index);
			filename = filename.substring(0, index);
		}

		filename = DECODING_URL_PATH_HELPER.decodeRequestString(servletRequest, filename);
		String ext = StringUtils.getFilenameExtension(filename);

		pathParams = DECODING_URL_PATH_HELPER.decodeRequestString(servletRequest, pathParams);
		String extInPathParams = StringUtils.getFilenameExtension(pathParams);

		if (!safeExtension(servletRequest, ext) || !safeExtension(servletRequest, extInPathParams)) {
			headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline;filename=f.txt");
		}
	}

	private boolean safeExtension(HttpServletRequest request, String extension) {
		if (!StringUtils.hasText(extension)) {
			return true;
		}
		extension = extension.toLowerCase(Locale.ENGLISH);
		if (this.safeExtensions.contains(extension)) {
			return true;
		}
		String pattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
		if (pattern != null && pattern.endsWith("." + extension)) {
			return true;
		}
		if (extension.equals("html")) {
			String name = HandlerMapping.PRODUCIBLE_MEDIA_TYPES_ATTRIBUTE;
			Set<MediaType> mediaTypes = (Set<MediaType>) request.getAttribute(name);
			if (!CollectionUtils.isEmpty(mediaTypes) && mediaTypes.contains(MediaType.TEXT_HTML)) {
				return true;
			}
		}
		return safeMediaTypesForExtension(extension);
	}

	private boolean safeMediaTypesForExtension(String extension) {
		List<MediaType> mediaTypes = null;
		try {
			mediaTypes = this.pathStrategy.resolveMediaTypeKey(null, extension);
		}
		catch (HttpMediaTypeNotAcceptableException ex) {
			// Ignore
		}
		if (CollectionUtils.isEmpty(mediaTypes)) {
			return false;
		}
		for (MediaType mediaType : mediaTypes) {
			if (!safeMediaType(mediaType)) {
				return false;
			}
		}
		return true;
	}

	private static PathExtensionContentNegotiationStrategy initPathStrategy(ContentNegotiationManager manager) {
		Class<PathExtensionContentNegotiationStrategy> clazz = PathExtensionContentNegotiationStrategy.class;
		PathExtensionContentNegotiationStrategy strategy = manager.getStrategy(clazz);
		return (strategy != null ? strategy : new PathExtensionContentNegotiationStrategy());
	}

	private boolean safeMediaType(MediaType mediaType) {
		return (WHITELISTED_MEDIA_BASE_TYPES.contains(mediaType.getType()) ||
				mediaType.getSubtype().endsWith("+xml"));
	}

	/**
	 * Writes the given return type to the given output message.
	 * @param value the value to write to the output message
	 * @param returnType the type of the value
	 * @param inputMessage the input messages. Used to inspect the {@code Accept} header.
	 * @param outputMessage the output message to write to
	 * @throws IOException thrown in case of I/O errors
	 * @throws HttpMediaTypeNotAcceptableException thrown when the conditions indicated
	 * by the {@code Accept} header on the request cannot be met by the message converters
	 */
	@SuppressWarnings("unchecked")
	protected <T> void writeWithMessageConverters(T value, MethodParameter returnType,
																								ServletServerHttpRequest inputMessage, ServletServerHttpResponse outputMessage)
			throws IOException, HttpMediaTypeNotAcceptableException, HttpMessageNotWritableException {

		Object outputValue;
		Class<?> valueType;
		Type declaredType;

		if (value instanceof CharSequence) {
			outputValue = value.toString();
			valueType = String.class;
			declaredType = String.class;
		}
		else {
			outputValue = value;
			valueType = getReturnValueType(outputValue, returnType);
			declaredType = getGenericType(returnType);
		}

		HttpServletRequest request = inputMessage.getServletRequest();
		List<MediaType> requestedMediaTypes = getAcceptableMediaTypes(request);
		List<MediaType> producibleMediaTypes = getProducibleMediaTypes(request, valueType, declaredType);

		if (outputValue != null && producibleMediaTypes.isEmpty()) {
			throw new IllegalArgumentException("No converter found for return value of type: " + valueType);
		}

		Set<MediaType> compatibleMediaTypes = new LinkedHashSet<MediaType>();
		for (MediaType requestedType : requestedMediaTypes) {
			for (MediaType producibleType : producibleMediaTypes) {
				if (requestedType.isCompatibleWith(producibleType)) {
					compatibleMediaTypes.add(getMostSpecificMediaType(requestedType, producibleType));
				}
			}
		}
		if (compatibleMediaTypes.isEmpty()) {
			if (outputValue != null) {
				throw new HttpMediaTypeNotAcceptableException(producibleMediaTypes);
			}
			return;
		}

		List<MediaType> mediaTypes = new ArrayList<MediaType>(compatibleMediaTypes);
		MediaType.sortBySpecificityAndQuality(mediaTypes);

		// LOCKSS: Get and use the Content-Type from the OutputMessage if set explicitly
		MediaType selectedMediaType = outputMessage.getHeaders().getContentType();

		// LOCKSS: Try to determine which MediaType to use otherwise
		if (selectedMediaType == null) {
			for (MediaType mediaType : mediaTypes) {
				if (mediaType.isConcrete()) {
					selectedMediaType = mediaType;
					break;
				} else if (mediaType.equals(MediaType.ALL) || mediaType.equals(MEDIA_TYPE_APPLICATION)) {
					selectedMediaType = MediaType.APPLICATION_OCTET_STREAM;
					break;
				}
			}
		}

		if (selectedMediaType != null) {
			selectedMediaType = selectedMediaType.removeQualityValue();
			for (HttpMessageConverter<?> messageConverter : this.messageConverters) {
				if (messageConverter instanceof GenericHttpMessageConverter) {
					if (((GenericHttpMessageConverter) messageConverter).canWrite(
							declaredType, valueType, selectedMediaType)) {

					  // LOCKSS
//						outputValue = (T) getAdvice().beforeBodyWrite(outputValue, returnType, selectedMediaType,
//								(Class<? extends HttpMessageConverter<?>>) messageConverter.getClass(),
//								inputMessage, outputMessage);

						if (outputValue != null) {
							addContentDispositionHeader(inputMessage, outputMessage);
							((GenericHttpMessageConverter) messageConverter).write(
									outputValue, declaredType, selectedMediaType, outputMessage);
							if (logger.isDebugEnabled()) {
								logger.debug("Written [" + outputValue + "] as \"" + selectedMediaType +
										"\" using [" + messageConverter + "]");
							}
						}
						return;
					}
				}
				else if (messageConverter.canWrite(valueType, selectedMediaType)) {

					// LOCKSS
//					outputValue = (T) getAdvice().beforeBodyWrite(outputValue, returnType, selectedMediaType,
//							(Class<? extends HttpMessageConverter<?>>) messageConverter.getClass(),
//							inputMessage, outputMessage);

					if (outputValue != null) {
						addContentDispositionHeader(inputMessage, outputMessage);
						((HttpMessageConverter) messageConverter).write(outputValue, selectedMediaType, outputMessage);
						if (logger.isDebugEnabled()) {
							logger.debug("Written [" + outputValue + "] as \"" + selectedMediaType +
									"\" using [" + messageConverter + "]");
						}
					}
					return;
				}
			}
		}

		if (outputValue != null) {
			// FIXME: Was throw new HttpMediaTypeNotAcceptableException(this.allSupportedMediaTypes);
			throw new HttpMediaTypeNotAcceptableException("FIXME: Could not write message");
		}
	}
}
