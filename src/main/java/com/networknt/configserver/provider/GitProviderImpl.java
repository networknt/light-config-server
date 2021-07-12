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
import com.networknt.configserver.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.config.Config;
import com.networknt.exception.ApiException;
import com.networknt.status.Status;
import io.undertow.util.Headers;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jose4j.json.internal.json_simple.JSONValue;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


/**
 * Config Server Provider implementation for Git
 *
 * The Git Provider enables storing the service configs in git repository.
 * To use this provider:
 * - create a separate git repo following naming convention "light-service-configs-{projectName}-{environment}" for each project/env pair e.g. light-service-configs-retail-dev for 'retail' project in 'dev' env
 * - populate configserver.gitApiHost, configserver.gitApiContextRoot, configserver.gitRepoOwner, configserver.gitRepoName config properties with proper values.
 * Keeping repo name with placeholders {projectName} and {environment} in config properties enables using same config server instance for multiple projects and environments.
 *
 * If repo name is mentioned in config properties, it will be used for all the requests after replacing projectName and environment placeholder with values from the request.
 * If repo name is missing, then it will be constructed using projectName and environment from the request path as light-service-configs-{projectName}-{environment} for each request.
 *
 * This provider assumes that caller has git authorization token already.
 *
 * Git repo should follow below folder structure:
 *
 *  {git-repo-name} // recommended naming conventions is "light-service-configs-{projectName}-{environment}" e.g. light-service-configs-retail-dev
 *  |-- configs
 *      |-- globals
 *          |-- {projectVersion} e.g. v1
 *              |-- values.yml    // project's global configs go here
 *      |-- {serviceName} e.g. api-customers
 *          |-- {serviceVersion} e.g. v1
 *              |-- values.yml    // service configs go here
 *      |-- ... add more services
 *  |-- certs
 *      |-- globals
 *          |-- {projectVersion} e.g. v1
 *              |-- client.keystore   // project's global cert files go here
 *              |-- client.truststore
 *      |-- {serviceName} e.g. api-customers
 *          |-- {serviceVersion} e.g. v1
 *              |-- client.keystore    // service cert files go here
 *              |-- server.truststore
 *      |-- ... add more services
 *  |-- files
 *      |-- globals
 *          |-- {projectVersion} e.g. v1
 *              |-- logback.xml   // project's global config files go here
 *              |-- hibernate.properties
 *      |-- {serviceName} e.g. api-customers
 *          |-- {serviceVersion} e.g. v1
 *              |-- logback.xml    // service config files go here
 *              |-- hibernate.properties
 *      |-- ... add more services
 *
 * @author santosh.aherkar@gmail.com
 */
public class GitProviderImpl implements IProvider {
    public static final String CONTENTS = "contents";
    public static final String CONTENT = "content";
    public static final String MESSAGE = "message";
    public static final String NAME = "name";
    public static final String CONFIG_PROXY = "proxy";

    private static final String CONFIGS_FILE_NAME = "values.yml";

    private static final String GIT_API_HOST = "gitApiHost";
    private static final String GIT_API_CONTEXT_ROOT = "gitApiContextRoot";

    private static final String GIT_REPO_OWNER = "gitRepoOwner";
    private static final String GIT_REPO_NAME = "gitRepoName";
    private static final String GIT_REPO_NAME_PREFIX = "light-service-configs-";


    /**
     * Login to Git repo
     *
     * @param auth: Authorization (Git personal access tokens)
     * @return client token
     * @throws ApiException when user is not authorized or can not login
     */
    @Override
    public String login(Authorization auth) throws ApiException {
        final String authorization = auth.getAuthorization();
        if (authorization != null && authorization.toLowerCase().startsWith("bearer")) {
            return authorization;
        } else {
            logger.error("Not Authorized!");
            throw new ApiException(new Status("ERR10000"));
        }
    }

    /**
     * Get config properties from Git repo for given service details
     *
     * @param authToken: auth token to authenticate to git repo
     * @param service:   object with service details like projectName, projectVersion, serviceName, serviceVersion etc. to create git repo configs path
     * @return serviceConfigs
     * @throws ApiException when fails to fetch configs from git repo
     */
    @Override
    public ServiceConfigs getServiceConfigs(String authToken, Service service) throws ApiException {
        ServiceConfigs serviceConfigs = new ServiceConfigs();
        serviceConfigs.setService(service);
        Map<String, Object> configsMap = new HashMap<String, Object>();
        Base64.Decoder decoder = Base64.getMimeDecoder();

        String contextRoot = buildContextRoot(service);
        //Get global configs first
        String globalConfigsEndpoint = buildEndpoint(contextRoot, ConfigServerConstants.CONFIGS, ConfigServerConstants.GLOBALS, service.getProjectVersion());
        String encodedContent = getFileContent(authToken, globalConfigsEndpoint);
        if (encodedContent != null) {
            String fileContent = new String(decoder.decode(encodedContent));
            configsMap = getJsonFromYaml(fileContent);
        }

        //Get service configs
        String serviceConfigsEndpoint = buildEndpoint(contextRoot, ConfigServerConstants.CONFIGS, service.getServiceName(), service.getServiceVersion());
        encodedContent = getFileContent(authToken, serviceConfigsEndpoint);
        if (encodedContent != null) {
            String fileContent = new String(decoder.decode(encodedContent));
            //Merging two configs
            configsMap.putAll(getJsonFromYaml(fileContent));
        }
        serviceConfigs.setConfigProperties(configsMap);

        return serviceConfigs;
    }

    /**
     * Get Certificates from git repo for given service details
     *
     * @param authToken: auth token to authenticate to git repo
     * @param service:   object with service details like projectName, projectVersion, serviceName, serviceVersion etc. to create git repo certs path
     * @return service certs
     * @throws ApiException when fails to fetch certs from git repo
     */
    @Override
    public ServiceConfigs getServiceCertificates(String authToken, Service service) throws ApiException {
        Map<String, Object> configsMap = new HashMap<String, Object>();
        ServiceConfigs serviceConfigs = new ServiceConfigs();
        serviceConfigs.setService(service);
        List<String> certFileNames = null;

        String contextRoot = buildContextRoot(service);
        //Get global certs first
        String globalCertsEndpoint = buildEndpoint(contextRoot, ConfigServerConstants.CERTS, ConfigServerConstants.GLOBALS, service.getProjectVersion());
        certFileNames = getFileNames(authToken, globalCertsEndpoint);
        configsMap = getFolderContent(authToken, globalCertsEndpoint, certFileNames);

        //Get service certs
        String serviceCertsEndpoint = buildEndpoint(contextRoot, ConfigServerConstants.CERTS, service.getServiceName(), service.getServiceVersion());
        certFileNames = getFileNames(authToken, serviceCertsEndpoint);

        //Merging two certs
        configsMap.putAll(getFolderContent(authToken, serviceCertsEndpoint, certFileNames));
        serviceConfigs.setConfigProperties(configsMap);

        return serviceConfigs;
    }


    /**
     * Get files from git repo for given service details
     *
     * @param authToken: auth token to authenticate to git repo
     * @param service:   object with service details like projectName, projectVersion, serviceName, serviceVersion etc. to create git repo files path
     * @return service files
     * @throws ApiException when fails to fetch files from git repo
     */
    @Override
    public ServiceConfigs getServiceFiles(String authToken, Service service) throws ApiException {
        Map<String, Object> configsMap = new HashMap<String, Object>();
        ServiceConfigs serviceConfigs = new ServiceConfigs();
        serviceConfigs.setService(service);
        List<String> fileNames = null;

        String contextRoot = buildContextRoot(service);
        //Get global certs first
        String globalFilesEndpoint = buildEndpoint(contextRoot, ConfigServerConstants.FILES, ConfigServerConstants.GLOBALS, service.getProjectVersion());
        fileNames = getFileNames(authToken, globalFilesEndpoint);
        configsMap = getFolderContent(authToken, globalFilesEndpoint, fileNames);

        //Get service files
        String serviceFilesEndpoint = buildEndpoint(contextRoot, ConfigServerConstants.FILES, service.getServiceName(), service.getServiceVersion());
        fileNames = getFileNames(authToken, serviceFilesEndpoint);

        //Merging two files
        configsMap.putAll(getFolderContent(authToken, serviceFilesEndpoint, fileNames));
        serviceConfigs.setConfigProperties(configsMap);

        return serviceConfigs;
    }

    @Override
    public List<String> saveServiceConfigs(String authToken, Service service, List<ServiceConfig> serviceConfigs) {
        return List.of();
    }

    private String getFileContent(String authToken, String filePath) throws ApiException {
        String encodedContent = null;

        String respBody = executeRequest(authToken, filePath);
        if (respBody != null) {
            try {
                Map<String, Object> responseMap = (Map<String, Object>) mapper.readValue(respBody, new TypeReference<Map<String, Object>>(){});
                encodedContent = (String) responseMap.get(CONTENT);
                logger.debug("encodedContent: {}", encodedContent);
            } catch (IOException e) {
                logger.error("Error while parsing response from Git API:", e);
                throw new ApiException(new Status("ACS00002"));
            }
        }

        return encodedContent;
    }

    private List<String> getFileNames(String authToken, String filePath) throws ApiException {
        List<String> fileNames = null;

        String respBody = executeRequest(authToken, filePath);
        if (respBody != null) {
            try {
                List<Map<String, Object>> fileNamesList = (List<Map<String, Object>>) mapper.readValue(respBody, new TypeReference<List<Map<String, Object>>>() {});
                fileNames = fileNamesList.stream()  // stream over the list
                        .map(m -> m.get(NAME))      // try to get the key "name"
                        .filter(Objects::nonNull)         // filter any null values in case it wasn't present
                        .map(Object::toString).collect(Collectors.toList()); //finally create List of String objects
                logger.debug("fileNames: {}", fileNames);
            } catch (IOException e) {
                logger.error("Error while parsing response from Git API:", e);
                throw new ApiException(new Status("ACS00002"));
            }
        }

        return fileNames;
    }

   /**
     * Execute REST request using Apache client
     * @param authToken authorization token
     * @param endpoint the endpoint path that needs to be called
     * @return
     * @throws ApiException
     */
    private String executeRequest(String authToken, String endpoint) throws ApiException{
        String respBody = null;
        Map<String, Object> config = Config.getInstance().getJsonMapConfig(ConfigServerConstants.CONFIG_NAME);
        String url = config.get(GIT_API_HOST) + endpoint;
        logger.debug("Creating request for URL: {}", url);

        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet request = new HttpGet(url);
        request.addHeader(Headers.AUTHORIZATION.toString(), authToken);

        ProxyConfig proxyConfig = (ProxyConfig) Config.getInstance().getJsonObjectConfig(CONFIG_PROXY,
                ProxyConfig.class);
        if (proxyConfig.getEnableProxy()) {
            logger.debug("Connecting to proxy at: {}://{}:{}", proxyConfig.getScheme() , proxyConfig.getHostname() , proxyConfig.getPort());
            HttpHost proxy = new HttpHost(proxyConfig.getHostname(), proxyConfig.getPort(), proxyConfig.getScheme());
            RequestConfig requestConfig = RequestConfig.custom().setProxy(proxy).build();
            request.setConfig(requestConfig);
        }

        try {
            //Calling the git contents api
            HttpResponse response = client.execute(request);
            if (response != null) {
                int statusCode = response.getStatusLine().getStatusCode();
                respBody = EntityUtils.toString(response.getEntity());
                logger.debug("Received Git API response: {}", respBody);

                checkForErrors(endpoint, respBody, statusCode);
            }
        } catch (IOException e) {
            logger.error("Exception while calling Git API: ", e);
            throw new ApiException(new Status("ACS00001", 500, "Could not connect to Git server"));
        }
        return respBody;
    }

    private void checkForErrors(String endpoint, String respBody, int statusCode) throws IOException, ApiException {
        if (statusCode == 404) {
            logger.error("Path not found in git: {}", endpoint);
            return;
        }

        if (statusCode >= 300) {
            Map<String, Object> responseMap = (Map<String, Object>) mapper.readValue(respBody, new TypeReference<Map<String, Object>>() {});
            String errorMessage = (String) responseMap.get(MESSAGE);
            logger.error("Failed to fetch content from git repo: {}:{}", statusCode, errorMessage);
            throw new ApiException(new Status("ACS00001", statusCode, errorMessage));
        }
    }

    private Map<String, Object> getJsonFromYaml(String yamlString) throws ApiException {
        Map<String, Object> jsonMap = new HashMap<String, Object>();
        if (yamlString != null) {
            String jsonString = JSONValue.toJSONString(new Yaml().load(yamlString));
            try {
                jsonMap = (Map<String, Object>) mapper.readValue(jsonString, new TypeReference<Map<String, Object>>() {
                });
            } catch (IOException e) {
                logger.error("Error while parsing yaml string:", e);
                throw new ApiException(new Status("ACS00002"));
            }
        }
        return jsonMap;
    }

    private Map<String, Object> getFolderContent(String authToken, String folderPath, List<String> fileNames) throws ApiException {
        Map<String, Object> folderContent = new HashMap<String, Object>();
        if (fileNames != null) {
            for (String fileName : fileNames) {
                StringBuilder filePath = new StringBuilder(folderPath).append(ConfigServerConstants.SLASH).append(fileName);
                String encodedFileContent = getFileContent(authToken, filePath.toString());
                if (encodedFileContent != null) {
                    folderContent.put(fileName, encodedFileContent);
                }
            }
        }
        return folderContent;
    }

    @Override
    public List<Service> searchServices(String authToken, String projectName) throws ApiException {
        return null;
    }

    private String buildEndpoint(String contextRoot, String configType, String name, String version){
        StringBuilder endpoint = new StringBuilder(contextRoot)
                .append(ConfigServerConstants.SLASH).append(configType)
                .append(ConfigServerConstants.SLASH).append(name)
                .append(ConfigServerConstants.SLASH).append(version);
        if(ConfigServerConstants.CONFIGS.equalsIgnoreCase(configType)){
            endpoint.append(ConfigServerConstants.SLASH).append(CONFIGS_FILE_NAME);
        }
        logger.debug("endpoint:{}", endpoint);

        return endpoint.toString();
    }

    private String buildContextRoot(Service service) {
        Map<String, Object> config = Config.getInstance().getJsonMapConfig(ConfigServerConstants.CONFIG_NAME);
        String gitRepoName = (String) config.get(GIT_REPO_NAME);
        if (StringUtils.isBlank(gitRepoName)) {
            gitRepoName = new StringBuilder(GIT_REPO_NAME_PREFIX)
                    .append(service.getProjectName())
                    .append(ConfigServerConstants.DASH)
                    .append(service.getEnvironment()).toString();
        }else{
            gitRepoName = gitRepoName.replace(ConfigServerConstants.PROJECT_NAME_PLACEHOLDER, service.getProjectName())
                    .replace(ConfigServerConstants.ENVIRONMENT_PLACEHOLDER, service.getEnvironment());
        }

        StringBuilder contextRoot = new StringBuilder()
                .append(ConfigServerConstants.SLASH).append(config.get(GIT_API_CONTEXT_ROOT))
                .append(ConfigServerConstants.SLASH).append(config.get(GIT_REPO_OWNER))
                .append(ConfigServerConstants.SLASH).append(gitRepoName)
                .append(ConfigServerConstants.SLASH).append(CONTENTS);
        return contextRoot.toString();
    }
}
