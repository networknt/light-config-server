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
import com.networknt.configserver.model.ServiceConfig;
import com.networknt.configserver.model.ServiceConfigs;
import com.networknt.configserver.model.VaultLoginRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.networknt.client.Http2Client;
import com.networknt.client.builder.HttpClientBuilder;
import com.networknt.client.model.TimeoutDef;
import com.networknt.config.Config;
import com.networknt.exception.ApiException;
import com.networknt.exception.ClientException;
import com.networknt.status.Status;
import io.undertow.client.ClientRequest;
import io.undertow.client.ClientResponse;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Config Server Provider implementation for Vault Server
 *
 * To use this provider:
 * Install and setup the Vault Server instance (Refer to Light Config Server docs)
 * Create 3 separate KV type Secret Engines for configs, certs and files; make sure you name path as 'configs', 'certs' and 'files' and select Version 2 for each of them.
 * Create secrets under those secret engines by following {project_name}/{service_name}/{service_version}/{environment} naming convention for path e.g.
 *  retail/globals/v1/dev  # globals will host all the project level configs
 *  retail/api-customers/v1/dev  # service level configs
 *  retail/globals/v1/test
 *  retail/api-customers/v1/test
 *
 * Add configserver.vaultServerUri property in Config Server's config properties and point it to Vault server url. e.g.
 *  configserver.vaultServerUri: http://localhost:8200
 *
 * @author santosh.aherkar@gmail.com
 */
public class VaultProviderImpl implements IProvider {

    private static final String VAULT_SERVER_URI = "vaultServerUri";
    private static final String VAULT_LOGIN_PATH = "/v1/auth/userpass/login/{username}";
    private static final String VAULT_SERVICES_PATH = "/v1/configs/metadata/{project_name}?list=true";

    private static final String VAULT_SERVICE_CONFIGS_PATH = "/v1/configs/data/{project_name}/{service_name}/{service_version}/{environment}";
    private static final String VAULT_GLOBALS_CONFIGS_PATH = "/v1/configs/data/{project_name}/globals/{project_version}/{environment}";

    private static final String VAULT_SERVICE_CERTS_PATH = "/v1/certs/data/{project_name}/{service_name}/{service_version}/{environment}";
    private static final String VAULT_GLOBALS_CERTS_PATH = "/v1/certs/data/{project_name}/globals/{project_version}/{environment}";

    private static final String VAULT_SERVICE_FILES_PATH = "/v1/files/data/{project_name}/{service_name}/{service_version}/{environment}";
    private static final String VAULT_GLOBALS_FILES_PATH = "/v1/files/data/{project_name}/globals/{project_version}/{environment}";


    /**
     * Login to Vault Server
     *
     * @param authorization: Basic authorization string (Base64 encoded username/password for Vault server)
     * @return client token
     * @throws ApiException when user is not authorized or can not login
     */
    @Override
    public String login(String authorization) throws ApiException {
        Map<String, Object> config = Config.getInstance().getJsonMapConfig(ConfigServerConstants.CONFIG_NAME);
        String username = null;

        //extract login credentials from authorization header
        final String[] values = extractCredentials(authorization);
        if (values == null) {
            logger.error("Not Authorized!");
            throw new ApiException(new Status("ERR10000"));
        }
        username = values[0];
        VaultLoginRequest request = new VaultLoginRequest();
        request.setPassword(values[1]);
        logger.debug("Credentials:{}/{}", username, "****");

        //call login endpoint and return token
        logger.info("Calling vault login endpoint:{}", VAULT_LOGIN_PATH);
        ClientResponse clientResponse = null;
        String token = null;
        try {
            //Calling the vault login api!
            Future<ClientResponse> clientRequest = new HttpClientBuilder()
                    .setApiHost((String)config.get(VAULT_SERVER_URI))
                    .setClientRequest(new ClientRequest().setMethod(Methods.POST).setPath(VAULT_LOGIN_PATH.replace("{username}", username)))
                    .setRequestBody(mapper.writeValueAsString(request))
                    .setLatch(new CountDownLatch(1))
                    .setConnectionRequestTimeout(new TimeoutDef(100, TimeUnit.SECONDS))
                    .setConnectionCacheTTLms(10000)
                    .setHeaderValue(Headers.TRANSFER_ENCODING, "chunked")//set header TRANSFER_ENCODING and pass the request body into the callback function.
                    .send();

            clientResponse = clientRequest.get();
            logger.debug("Received client response: {}", clientResponse);
            if (clientResponse != null) {
                int statusCode = clientResponse.getResponseCode();
                if (statusCode >= 300) {
                    logger.error("Failed to login to vault server: {}", statusCode);
                    throw new ApiException(new Status("ACS00003"));
                }

                String respBody = clientResponse.getAttachment(Http2Client.RESPONSE_BODY);
                Map<String, Object> response = (Map<String, Object>) mapper.readValue(respBody, new TypeReference<Map<String, Object>>() {
                });
                Map<String, Object> authMap = (Map<String, Object>) response.get("auth");
                token = (String) authMap.get("client_token");
            }
            logger.debug("Received client token: {}", token);
        } catch (IOException e) {
            logger.error("Exception while parsing Vault response: ", e);
            throw new ApiException(new Status("ACS00002"));
        } catch (URISyntaxException | ExecutionException | TimeoutException | InterruptedException | ClientException e) {
            logger.error("Exception while calling Vault Login API: ", e);
            throw new ApiException(new Status("ACS00001"));
        }
        return token;
    }

    /**
     * Get config properties from Vault server for given service details
     *
     * @param clientToken: client token to authenticate to Vault Server
     * @param service:     object with service details like projectName, projectVersion, serviceName, serviceVersion etc. to create vault configs path
     * @return serviceConfigs
     * @throws ApiException when fails to fetch configs from vault serve
     */
    @Override
    public ServiceConfigs getServiceConfigs(String clientToken, Service service) throws ApiException {

        ServiceConfigs serviceConfigs = new ServiceConfigs();
        serviceConfigs.setService(service);
        Map<String, Object> configsMap = new HashMap<String, Object>();

        String endpoint = VAULT_GLOBALS_CONFIGS_PATH.replace("{project_name}", service.getProjectName())
                .replace("{project_version}", service.getProjectVersion())
                .replace("{environment}", service.getEnvironment());
        logger.info("Calling vault globals configs endpoint:{}", endpoint);

        //Getting Global configs..
        configsMap = getConfigs(clientToken, endpoint);

        endpoint = VAULT_SERVICE_CONFIGS_PATH.replace("{project_name}", service.getProjectName())
                .replace("{service_name}", service.getServiceName())
                .replace("{service_version}", service.getServiceVersion())
                .replace("{environment}", service.getEnvironment());
        logger.info("Calling vault service configs endpoint:{}", endpoint);

        //Merging two configs
        configsMap.putAll(getConfigs(clientToken, endpoint));
        serviceConfigs.setConfigProperties(configsMap);
        return serviceConfigs;
    }


    private Map<String, Object> getConfigs(String clientToken, String endpoint) throws ApiException {
        Map<String, Object> config = Config.getInstance().getJsonMapConfig(ConfigServerConstants.CONFIG_NAME);
        ClientResponse clientResponse = null;
        Map<String, Object> innerDataMap = new HashMap<String, Object>();
        try {
            HttpString headerName = new HttpString("X-Vault-Token");
            //Calling the vault configs api!
            Future<ClientResponse> clientRequest = new HttpClientBuilder()
                    .setApiHost((String)config.get(VAULT_SERVER_URI))
                    .setClientRequest(new ClientRequest().setMethod(Methods.GET).setPath(endpoint))
                    .setLatch(new CountDownLatch(1))
                    .setHeaderValue(headerName, clientToken)
                    .setConnectionRequestTimeout(new TimeoutDef(100, TimeUnit.SECONDS))
                    .setConnectionCacheTTLms(10000)
                    .send();

            clientResponse = clientRequest.get();
            if (clientResponse != null) {
                int statusCode = clientResponse.getResponseCode();
                String respBody = clientResponse.getAttachment(Http2Client.RESPONSE_BODY);

                checkForErrors(endpoint, respBody, statusCode);

                if(statusCode == 200) {
                    Map<String, Object> response = (Map<String, Object>) mapper.readValue(respBody, new TypeReference<Map<String, Object>>() {
                    });
                    Map<String, Object> outerDataMap = (Map<String, Object>) response.get("data");
                    innerDataMap = (Map<String, Object>) outerDataMap.get("data");
                }
            }
            logger.debug("Received client response: " + clientResponse);
        } catch (IOException e) {
            logger.error("Exception while parsing Vault response: ", e);
            throw new ApiException(new Status("ACS00002"));
        } catch (URISyntaxException | ExecutionException | TimeoutException | InterruptedException | ClientException e) {
            logger.error("Exception while calling Vault Configs API: ", e);
            throw new ApiException(new Status("ACS00001"));
        }
        return innerDataMap;
    }

    private void checkForErrors(String endpoint, String respBody, int statusCode) throws IOException, ApiException {
        if (statusCode == 404) {
            logger.error("Path not found in vault: {}", endpoint);
            return;
        }

        if (statusCode >= 300) {
            Map<String, Object> response = (Map<String, Object>) mapper.readValue(respBody, new TypeReference<Map<String, Object>>() {});
            List<String> errors = (List<String>) response.get("errors");
            logger.error("Error while calling vault server: {}:{} ", statusCode, errors);
            throw new ApiException(new Status("ACS00001", statusCode, errors));
        }
    }

    private String[] extractCredentials(String authorization) {
        String[] values = null;
        if (authorization != null && authorization.toLowerCase().startsWith("basic")) {
            // Authorization: Basic base64credentials
            String base64Credentials = authorization.substring("Basic".length()).trim();
            byte[] credDecoded = Base64.getDecoder().decode(base64Credentials);
            String credentials = new String(credDecoded, StandardCharsets.UTF_8);
            // credentials = username:password
            values = credentials.split(":", 2);
        }

        return values;
    }

    /**
     * Get services from Vault server for given project Name
     *
     * @param clientToken: client token to authenticate to Vault Server
     * @param projectName: projectName to get the services from vault server.
     * @return servicesList
     * @throws ApiException when can not get list of services
     */
    @Override
    public List<Service> searchServices(String clientToken, String projectName) throws ApiException {
        String endpoint;
        List<Service> servicesList = new ArrayList<Service>();

        if (projectName != null) {
            endpoint = VAULT_SERVICES_PATH.replace("{project_name}", projectName);
            logger.info("Calling vault server services endpoint:{}", endpoint);
            List<String> list = getServices(clientToken, endpoint);
            for (String service : list) {
                Service serviceObj = createService(projectName, service);
                servicesList.add(serviceObj);
            }
            return servicesList;
        } else {
            endpoint = VAULT_SERVICES_PATH.replace("{project_Name}", "");
            logger.info("Calling vault server project endpoint:{}", endpoint);
            List<String> projectsList = getServices(clientToken, endpoint);

            for (String project_name : projectsList) {
                if (project_name.endsWith("/")) {
                    endpoint = VAULT_SERVICES_PATH.replace("{project_Name}", project_name);
                    logger.info("Calling vault server services endpoint:{}", endpoint);
                    List<String> list = getServices(clientToken, endpoint);
                    for (String service : list) {
                        Service serviceObj = createService(project_name, service);
                        servicesList.add(serviceObj);
                    }
                } else {
                    Service serviceObj = createService(project_name, null);
                    servicesList.add(serviceObj);
                }
            }
        }
        return servicesList;
    }

    @Override
    public List<String> saveServiceConfigs(String authToken, Service service, List<ServiceConfig> serviceConfigs) {
        return List.of();
    }

    private List<String> getServices(String clientToken, String endpoint) throws ApiException {
        Map<String, Object> config = Config.getInstance().getJsonMapConfig(ConfigServerConstants.CONFIG_NAME);
        logger.info("Calling vault server services endpoint:{}", VAULT_SERVICES_PATH);

        ClientResponse clientResponse = null;
        List<String> serviceList = new ArrayList();

        try {
            HttpString headerName = new HttpString("X-Vault-Token");
            Future<ClientResponse> clientRequest = new HttpClientBuilder()
                    .setApiHost((String)config.get(VAULT_SERVER_URI))
                    .setClientRequest(new ClientRequest().setMethod(Methods.GET).setPath(endpoint))
                    .setLatch(new CountDownLatch(1))
                    .setHeaderValue(headerName, clientToken)
                    .setConnectionRequestTimeout(new TimeoutDef(100, TimeUnit.SECONDS))
                    .setConnectionCacheTTLms(10000)
                    .send();

            clientResponse = clientRequest.get();
            if (clientResponse != null) {
                int statusCode = clientResponse.getResponseCode();
                String respBody = clientResponse.getAttachment(Http2Client.RESPONSE_BODY);

                checkForErrors(endpoint, respBody, statusCode);

                Map<String, Object> response = (Map<String, Object>) mapper.readValue(respBody, new TypeReference<Map<String, Object>>() {
                });
                Map<String, Object> outerDataMap = (Map<String, Object>) response.get("data");
                serviceList = (List) outerDataMap.get("keys");
            }
            logger.debug("Received client response: " + clientResponse);
        } catch (IOException e) {
            logger.error("Exception while parsing Vault response: ", e);
            throw new ApiException(new Status("ACS00002"));
        } catch (URISyntaxException | ExecutionException | TimeoutException | InterruptedException | ClientException e) {
            logger.error("Exception while calling Vault Services API: ", e);
            throw new ApiException(new Status("ACS00001"));
        }
        return serviceList;
    }

    private Service createService(String projectName, String serviceName) {
        Service services = new Service();
        services.setServiceName(serviceName);
        services.setProjectName(projectName);
        return services;
    }


    /**
     * Get Certificates from Vault server for given service details
     *
     * @param clientToken: client token to authenticate to Vault Server
     * @param service:     object with service details like projectName, projectVersion, serviceName, serviceVersion etc. to create vault Certificates path
     * @return service certs
     * @throws ApiException when can not get certs
     */
    @Override
    public ServiceConfigs getServiceCertificates(String clientToken, Service service) throws ApiException {

        ServiceConfigs serviceConfigs = new ServiceConfigs();
        serviceConfigs.setService(service);
        Map<String, Object> configsMap = new HashMap<String, Object>();

        String endpoint = VAULT_GLOBALS_CERTS_PATH.replace("{project_name}", service.getProjectName())
                .replace("{project_version}", service.getProjectVersion())
                .replace("{environment}", service.getEnvironment());
        logger.info("Calling vault globals certificates endpoint:{}", endpoint);

        //Getting Global Certificates..
        configsMap = getConfigs(clientToken, endpoint);

        endpoint = VAULT_SERVICE_CERTS_PATH.replace("{project_name}", service.getProjectName())
                .replace("{service_name}", service.getServiceName())
                .replace("{service_version}", service.getServiceVersion())
                .replace("{environment}", service.getEnvironment());
        logger.info("Calling vault service certificates endpoint:{}", endpoint);

        //Merging two Certificates
        configsMap.putAll(getConfigs(clientToken, endpoint));
        serviceConfigs.setConfigProperties(configsMap);
        return serviceConfigs;
    }


    /**
     * Get files from Vault server for given service details
     *
     * @param clientToken: client token to authenticate to Vault Server
     * @param service:     object with service details like projectName, projectVersion, serviceName, serviceVersion etc. to create vault files path
     * @return service files
     * @throws ApiException when can not get files
     */
    @Override
    public ServiceConfigs getServiceFiles(String clientToken, Service service) throws ApiException {
        ServiceConfigs serviceConfigs = new ServiceConfigs();
        serviceConfigs.setService(service);
        Map<String, Object> configsMap = new HashMap<String, Object>();

        String endpoint = VAULT_GLOBALS_FILES_PATH.replace("{project_name}", service.getProjectName())
                .replace("{project_version}", service.getProjectVersion())
                .replace("{environment}", service.getEnvironment());
        logger.info("Calling vault globals files endpoint:{}", endpoint);

        //Getting Global files..
        configsMap = getConfigs(clientToken, endpoint);

        endpoint = VAULT_SERVICE_FILES_PATH.replace("{project_name}", service.getProjectName())
                .replace("{service_name}", service.getServiceName())
                .replace("{service_version}", service.getServiceVersion())
                .replace("{environment}", service.getEnvironment());
        logger.info("Calling vault service files endpoint:{}", endpoint);

        //Merging two files
        configsMap.putAll(getConfigs(clientToken, endpoint));
        serviceConfigs.setConfigProperties(configsMap);
        return serviceConfigs;
    }
}
