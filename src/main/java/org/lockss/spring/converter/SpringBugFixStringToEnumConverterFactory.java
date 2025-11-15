/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.lockss.spring.converter;

import java.lang.reflect.InvocationTargetException;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

/**
 * Converts from a String to a {@link java.lang.Enum} by calling {@link Enum#valueOf(Class, String)}.
 *
 * @author Keith Donald
 * @author Stephane Nicoll
 * @since 3.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public final class SpringBugFixStringToEnumConverterFactory implements ConverterFactory<String, Enum> {

        // Can't use ConversionUtil#getEnumType (ConversionUtils is package-private); simply copy here
        public static Class<?> getEnumType(Class<?> targetType) {
          Class<?> enumType = targetType;
          while (enumType != null && !enumType.isEnum()) {
                  enumType = enumType.getSuperclass();
          }
          if (enumType == null) {
            throw new NullPointerException("The target type " + targetType.getName() + " does not refer to an enum");
          }
          return enumType;
        }
  
        @Override
	public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
		return new SpringBugFixStringToEnum(/*ConversionUtils.*/getEnumType(targetType));
	}


	private static class SpringBugFixStringToEnum<T extends Enum> implements Converter<String, T> {

		private final Class<T> enumType;

		SpringBugFixStringToEnum(Class<T> enumType) {
			this.enumType = enumType;
		}

		@Override
		public T convert(String source) {
			if (source.isEmpty()) {
				// It's an empty enum identifier: reset the enum value to null.
				return null;
			}
			try {
                          return (T)MethodUtils.invokeStaticMethod(enumType, "fromValue", source);
                        }
			catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException exc) {
			  throw new RuntimeException("No fromValue method in the enum " + enumType.getName(), exc); // what to do here?
			}
		}
	}

}