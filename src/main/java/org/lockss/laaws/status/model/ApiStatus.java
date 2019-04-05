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
package org.lockss.laaws.status.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import org.lockss.log.L4JLogger;

/**
 * Representation of the status of a REST web service.
 */
public class ApiStatus {
  private static final L4JLogger log = L4JLogger.getLogger();

  /**
   * The version of the REST web service.
   */
  private String version = null;

  /**
   * An indication of whether the REST web service is ready to process requests.
   */
  private Boolean ready = Boolean.FALSE;

  /**
   * No-argument constructor.
   */
  public ApiStatus() {
  }

  /**
   * Constructor with Swagger YAML resource location.
   *
   * @param swaggerYamlFileResource
   *          A String with the Swagger YAML resource location.
   */
  public ApiStatus(String swaggerYamlFileResource) {
    // Use an input stream to the Swagger YAML resource.
    try (InputStream is = Thread.currentThread().getContextClassLoader()
	  .getResourceAsStream(swaggerYamlFileResource)) {
      // Get the version from the Swagger YAML resource.
      version = new ObjectMapper(new YAMLFactory())
	  .readValue(is, SwaggerYaml.class).getInfo().getVersion();
      log.trace("version = {}", version);
    } catch (Exception e) {
      log.error("Exception caught getting the API version: ", e);
    }
  }

  /**
   * Provides the version of the REST web service.
   * 
   * @return a String with the version of the REST web service.
   */
  public String getVersion() {
    return version;
  }

  /**
   * Saves the version of the REST web service.
   * 
   * @param version
   *          A String with the version of the REST web service.
   * @return an ApiStatus with this object.
   */
  public ApiStatus setVersion(String version) {
    this.version = version;
    return this;
  }

  /**
   * Provides an indication of whether the REST web service is available.
   * 
   * @return a Boolean with the indication of whether the REST web service is
   *         available.
   */
  public Boolean isReady() {
    return ready;
  }

  /**
   * Saves the indication of whether the REST web service is available.
   * 
   * @param ready
   *          A Boolean with the indication of whether the REST web service is
   *          available.
   * @return an ApiStatus with this object.
   */
  public ApiStatus setReady(Boolean ready) {
    this.ready = ready;
    return this;
  }

  @Override
  public String toString() {
    return "[ApiStatus version=" + version + ", ready=" + ready + "]";
  }

  /**
   * A partial representation of a Swagger YAML file.
   */
  // Ignore uninteresting Swagger YAML file top entries.
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class SwaggerYaml {
    // The info entry in the Swagger YAML file.
    @JsonProperty
    private SwaggerInfo info;

    /**
     * Provides the info entry in the Swagger YAML file.
     * 
     * @return a SwaggerInfo with the info entry in the Swagger YAML file.
     */
    public SwaggerInfo getInfo() {
      return info;
    }

    /**
     * Saves the info entry in the Swagger YAML file.
     * 
     * @param info
     *          A SwaggerInfo with the info entry in the Swagger YAML file.
     */
    public void setInfo(SwaggerInfo info) {
      this.info = info;
    }
  }

  /**
   * A partial representation of an "info" top entry of a Swagger YAML file.
   */
  // Ignore uninteresting entries in the Swagger YAML file top "info" entry.
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class SwaggerInfo {
    // The API version.
    @JsonProperty
    private String version;

    /**
     * Provides the API version.
     * 
     * @return a String with the API version.
     */
    public String getVersion() {
      return version;
    }

    /**
     * Saves the API version.
     * 
     * @param version
     *          A String with the API version.
     */
    public void setVersion(String version) {
      this.version = version;
    }
  }
}
