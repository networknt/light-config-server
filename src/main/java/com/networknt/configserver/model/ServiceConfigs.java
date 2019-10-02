/*
 * Copyright (c) 2016 Network New Technologies Inc.
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

package com.networknt.configserver.model;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;

public class ServiceConfigs {

    private Object configProperties;
    private Service service;

    public ServiceConfigs () {
    }

    @JsonProperty("configProperties")
    public Object getConfigProperties() {
        return configProperties;
    }

    public void setConfigProperties(Object configProperties) {
        this.configProperties = configProperties;
    }
    @JsonProperty("service")
    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ServiceConfigs ServiceConfigs = (ServiceConfigs) o;

        return Objects.equals(configProperties, ServiceConfigs.configProperties) &&

        Objects.equals(service, ServiceConfigs.service);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configProperties,  service);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class ServiceConfigs {\n");
        sb.append("    configProperties: ").append(toIndentedString(configProperties)).append("\n");        sb.append("    service: ").append(toIndentedString(service)).append("\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * Convert the given object to string with each line indented by 4 spaces
     * (except the first line).
     */
    private String toIndentedString(Object o) {
        if (o == null) {
            return "null";
        }
        return o.toString().replace("\n", "\n    ");
    }
}
