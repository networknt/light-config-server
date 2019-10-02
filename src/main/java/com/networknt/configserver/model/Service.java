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

public class Service {

    private String projectName;
    private String projectVersion;
    private String serviceVersion;
    private String serviceName;
    private String environment;


    public Service () {
    }

    @JsonProperty("serviceVersion")
    public String getServiceVersion() {
        return serviceVersion;
    }

    public void setServiceVersion(String serviceVersion) {
        this.serviceVersion = serviceVersion;
    }
    @JsonProperty("environment")
    public String getEnvironment() {
        return environment;
    }

    public void setEnvironment(String environment) {
        this.environment = environment;
    }


    @JsonProperty("serviceName")
    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
    @JsonProperty("projectName")
    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectId) {
        this.projectName = projectId;
    }
    @JsonProperty("projectVersion")
    public String getProjectVersion() {
        return projectVersion;
    }

    public void setProjectVersion(String projectVersion) {
        this.projectVersion = projectVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Service Service = (Service) o;

        return Objects.equals(serviceVersion, Service.serviceVersion) &&
        Objects.equals(environment, Service.environment) &&
        Objects.equals(serviceName, Service.serviceName) &&
        Objects.equals(projectName, Service.projectName) &&
        
        Objects.equals(projectVersion, Service.projectVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceVersion, environment, serviceName, projectName,  projectVersion);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("class Service {\n");
        sb.append("    serviceVersion: ").append(toIndentedString(serviceVersion)).append("\n");
        sb.append("    environment: ").append(toIndentedString(environment)).append("\n");
        sb.append("    serviceName: ").append(toIndentedString(serviceName)).append("\n");
        sb.append("    projectName: ").append(toIndentedString(projectName)).append("\n");
        sb.append("    projectVersion: ").append(toIndentedString(projectVersion)).append("\n");
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
