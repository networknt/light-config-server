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

import com.networknt.configserver.model.Authorization;
import com.networknt.configserver.model.Service;
import com.networknt.configserver.model.ServiceConfig;
import com.networknt.configserver.model.ServiceConfigs;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.config.Config;
import com.networknt.exception.ApiException;
import com.networknt.service.SingletonServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Interface class to provide the contract for the different config server provider implementations
 */

public interface IProvider {
    Logger logger = LoggerFactory.getLogger(IProvider.class);

    /**
     * Default method to provide the IProvider instance from Singleton Service Factory.
     * If no instance found in factory, it creates one from VaultProviderImpl, puts it in factory and returns it.
     * @return instance of the provider
     */
    static IProvider getInstance(){
        IProvider provider = SingletonServiceFactory.getBean(IProvider.class);
        if(provider == null){
            logger.warn("No config server provider configured in service.yml; defaulting to VaultProviderImpl");
            provider = new VaultProviderImpl();
            SingletonServiceFactory.setBean(IProvider.class.getName(),provider);
        }
        return provider;
    }

    // Get a Jackson JSON Object Mapper, usable for object serialization
    public static final ObjectMapper mapper = Config.getInstance().getMapper();

    // login to provider backend
    String login(Authorization auth) throws ApiException;

    // get configs from provider backend
    ServiceConfigs getServiceConfigs(String authToken, Service service) throws ApiException;

    // get configs from provider backend
    ServiceConfigs getServiceCertificates(String authToken, Service service) throws ApiException;

    // get configs from provider backend
    ServiceConfigs getServiceFiles(String authToken, Service service) throws ApiException;


    // get services from provider backend
    List<Service> searchServices(String authToken, String projectName) throws ApiException;

    List<String> saveServiceConfigs(String authToken, Service service, List<ServiceConfig> serviceConfigs) throws ApiException;

}
