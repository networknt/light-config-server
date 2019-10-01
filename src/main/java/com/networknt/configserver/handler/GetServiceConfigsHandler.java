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

package com.networknt.configserver.handler;

import com.networknt.configserver.constants.ConfigServerConstants;
import com.networknt.configserver.model.Service;
import com.networknt.configserver.model.ServiceConfigs;
import com.networknt.configserver.provider.IProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.config.Config;
import com.networknt.handler.LightHttpHandler;
import com.networknt.httpstring.ContentType;
import com.networknt.status.Status;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.Map;

public class GetServiceConfigsHandler implements LightHttpHandler {
    static Logger logger = LoggerFactory.getLogger(GetServiceConfigsHandler.class);

    private static final ObjectMapper mapper = Config.getInstance().getMapper();

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        IProvider provider = IProvider.getInstance();

        // Login to provider backend and get the token
        final String authorization = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
        String clientToken = provider.login(authorization);

        //Get the inputs from request object!.
        Map<String, Deque<String>> parameters = exchange.getQueryParameters();
        Service service = new Service();
        service.setProjectName(parameters.get(ConfigServerConstants.PROJECT_NAME).getFirst());
        service.setProjectVersion(parameters.get(ConfigServerConstants.PROJECT_VERSION).getFirst());
        service.setServiceName(parameters.get(ConfigServerConstants.SERVICE_NAME).getFirst());
        service.setServiceVersion(parameters.get(ConfigServerConstants.SERVICE_VERSION).getFirst());
        service.setEnvironment(parameters.get(ConfigServerConstants.ENVIRONMENT).getFirst());

        logger.debug("Service Configs requested for:{}", service);
        // Read config properties from provider
        ServiceConfigs serviceConfigs = provider.getServiceConfigs(clientToken,service);

        if (serviceConfigs.getConfigProperties()!= null) {
            exchange.setStatusCode(200);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ContentType.APPLICATION_JSON.value());
            exchange.getResponseSender().send(mapper.writeValueAsString(serviceConfigs));
        } else {
            logger.error("Could not read configs from the provider");
            exchange.getResponseSender().send(mapper.writeValueAsString(serviceConfigs));
            Status status = new Status("500");
            String errorResp = mapper.writeValueAsString(status);
            exchange.setStatusCode(status.getStatusCode());
            exchange.getResponseSender().send(errorResp);
        }
        exchange.endExchange();
    }
}