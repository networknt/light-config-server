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

package com.networknt.configserver.provider;

import com.networknt.configserver.constants.ConfigServerConstants;
import com.networknt.configserver.model.Service;
import com.networknt.configserver.model.ServiceConfigs;
import com.networknt.config.Config;
import com.networknt.exception.ApiException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Config Server Provider implementation for File System
 *
 * This provider can be used for simple local environment when testing with config server.
 * Not intended for production use, unless until the encryption and authentication is implemented.
 *
 * To use this provider:
 * create a folder for the externalized configurations with below structure and
 * set your configserver.serviceConfigsDir config property to point to this folder.
 *
 *  {light-service-configs-dir} e.g. /light-service-configs
 *  |-- configs
 *      |-- {projectName} e.g. project1
 *          |-- globals
 *              |-- {projectVersion} e.g. v1
 *                  |-- {environment} e.g. dev
 *                      |-- values.yml      // project's global configs go here
 *          |-- {serviceName} e.g. api-customers
 *              |-- {serviceVersion} e.g. v1
 *                  |-- {environment} e.g. dev
 *                      |-- values.yml     // service configs go here
 *          |-- ... add more services
 *  |-- certs
 *      |-- {projectName} e.g. project1
 *          |-- globals
 *              |-- {projectVersion} e.g. v1
 *                  |-- {environment} e.g. dev
 *                    |-- client.keystore   // project's global cert files go here
 *          |-- {serviceName} e.g. api-customers
 *              |-- {serviceVersion} e.g. v1
 *                  |-- {environment} e.g. dev
 *                    |-- client.keystore   // project's service cert files go here
 *          |-- ... add more services
 *  |-- files
 *      |-- {projectName} e.g. project1
 *          |-- globals
 *              |-- {projectVersion} e.g. v1
 *                  |-- {environment} e.g. dev
 *                        |-- logback.xml   // project's global config files go here
 *                        |-- hibernate.properties
 *          |-- {serviceName} e.g. api-customers
 *              |-- {serviceVersion} e.g. v1
 *                  |-- {environment} e.g. dev
 *                       |-- logback.xml   // project's service config files go here
 *                       |-- hibernate.properties
 *          |-- ... add more services
 */
public class FileSystemProviderImpl implements IProvider {

    private static final String SERVICE_CONFIGS_DIR = "serviceConfigsDir";
    private static final String CONFIGS_FILE_NAME = "values";


    @Override
    public String login(String authorization) throws ApiException {
        return null;
    }

    /**
     * Get config properties from config directory for given service details
     *
     * @param authToken can be ignored for file system provider
     * @param service:  object with service details like projectName, projectVersion, serviceName, serviceVersion etc. to create config directory configs path
     * @return serviceConfigs
     * @throws ApiException when fails to fetch configs from config directory
     */
    @Override
    public ServiceConfigs getServiceConfigs(String authToken, Service service) throws ApiException {
        ServiceConfigs serviceConfigs = new ServiceConfigs();
        serviceConfigs.setConfigProperties(new HashMap<String, Object>());
        serviceConfigs.setService(service);
        String configPath = null;
        Map<String, Object> configsMap = null;

        //Get Global configs
        configPath = buildConfigPath(service, ConfigServerConstants.CONFIGS, ConfigServerConstants.GLOBALS, service.getProjectVersion());
        configsMap = Config.getInstance().getJsonMapConfigNoCache(CONFIGS_FILE_NAME, configPath);
        if (configsMap != null) {
            ((Map<String, Object>) serviceConfigs.getConfigProperties()).putAll(configsMap);
        }
        //Get Service configs
        configPath = buildConfigPath(service, ConfigServerConstants.CONFIGS, service.getServiceName(), service.getServiceVersion());
        configsMap = Config.getInstance().getJsonMapConfigNoCache(CONFIGS_FILE_NAME, configPath);
        if (configsMap != null) {
            ((Map<String, Object>) serviceConfigs.getConfigProperties()).putAll(configsMap);
        }
        return serviceConfigs;
    }


    /**
     * Get certs from config directory for given service details
     *
     * @param authToken can be ignored for file system provider
     * @param service:  object with service details like projectName, projectVersion, serviceName, serviceVersion etc. to create config directory certs path
     * @return service certs
     * @throws ApiException when fails to fetch certs from config directory
     */
    @Override
    public ServiceConfigs getServiceCertificates(String authToken, Service service) throws ApiException {
        ServiceConfigs serviceConfigs = new ServiceConfigs();
        serviceConfigs.setService(service);
        serviceConfigs.setConfigProperties(new HashMap<String, Object>());
        String configPath = null;
        Map<String, Object> certsMap = null;
        //Get Global certs
        configPath = buildConfigPath(service, ConfigServerConstants.CERTS, ConfigServerConstants.GLOBALS, service.getProjectVersion());
        certsMap = getFiles(configPath);
        if (certsMap != null) {
            ((Map<String, Object>) serviceConfigs.getConfigProperties()).putAll(certsMap);
        }
        //Get Service certs
        configPath = buildConfigPath(service, ConfigServerConstants.CERTS, service.getServiceName(), service.getServiceVersion());
        certsMap = getFiles(configPath);
        if (certsMap != null) {
            ((Map<String, Object>) serviceConfigs.getConfigProperties()).putAll(certsMap);
        }
        return serviceConfigs;
    }

    /**
     * Get files from config directory for given service details
     *
     * @param authToken can be ignored for file system provider
     * @param service:  object with service details like projectName, projectVersion, serviceName, serviceVersion etc. to create config directory files path
     * @return service files
     * @throws ApiException when fails to fetch files from config directory
     */
    @Override
    public ServiceConfigs getServiceFiles(String authToken, Service service) throws ApiException {
        ServiceConfigs serviceConfigs = new ServiceConfigs();
        serviceConfigs.setService(service);
        serviceConfigs.setConfigProperties(new HashMap<String, Object>());
        String configPath = null;
        Map<String, Object> filesMap = null;

        //Get Global files
        configPath = buildConfigPath(service, ConfigServerConstants.FILES, ConfigServerConstants.GLOBALS, service.getProjectVersion());
        filesMap = getFiles(configPath);
        if (filesMap != null) {
            ((Map<String, Object>) serviceConfigs.getConfigProperties()).putAll(filesMap);
        }
        //Get Service files
        configPath = buildConfigPath(service, ConfigServerConstants.FILES, service.getServiceName(), service.getServiceVersion());
        filesMap = getFiles(configPath);
        if (filesMap != null) {
            ((Map<String, Object>) serviceConfigs.getConfigProperties()).putAll(filesMap);
        }
        return serviceConfigs;
    }

    @Override
    public List<Service> searchServices(String authToken, String projectName) throws ApiException {
        return null;
    }

    private String buildConfigPath(Service service, String configType, String name, String version) {
        Map<String, Object> config = Config.getInstance().getJsonMapConfig(ConfigServerConstants.CONFIG_NAME);
        String configsDir = (String) config.get(SERVICE_CONFIGS_DIR);
        StringBuffer configPath = new StringBuffer(configsDir);
        configPath.append(ConfigServerConstants.SLASH).append(configType)
                .append(ConfigServerConstants.SLASH).append(service.getProjectName())
                .append(ConfigServerConstants.SLASH).append(name)
                .append(ConfigServerConstants.SLASH).append(version)
                .append(ConfigServerConstants.SLASH).append(service.getEnvironment());
        return configPath.toString();
    }

    private Map<String, Object> getFiles(String filesPath) {
        Map<String, Object> configsMap = new HashMap<>();
        try {
            Files.list(Paths.get(filesPath)).filter(Files::isRegularFile).forEach(file -> {
                String fileName = file.getFileName().toString();
                byte[] content = new byte[0];
                try {
                    content = Files.readAllBytes(file);
                } catch (IOException e) {
                    logger.error("Exception while reading file: " + fileName);;
                }
                if (content != null) {
                    String encodedContent = Base64.getMimeEncoder().encodeToString(content);
                    configsMap.put(fileName, encodedContent);
                }
            });
        } catch (IOException e) {
            logger.error("Exception while reading files from configs directory: " + filesPath);
        }
        return configsMap;
    }
}
