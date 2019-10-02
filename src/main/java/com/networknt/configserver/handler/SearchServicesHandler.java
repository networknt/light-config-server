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

import java.util.*;

public class SearchServicesHandler implements LightHttpHandler {
    static Logger logger = LoggerFactory.getLogger(SearchServicesHandler.class);

    private static final ObjectMapper mapper = Config.getInstance().getMapper();

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        IProvider provider = IProvider.getInstance();

        // Login to provider backend and get the token
        final String authorization = exchange.getRequestHeaders().getFirst(Headers.AUTHORIZATION);
        String clientToken = provider.login(authorization);

        //Get the inputs from request object!.
        String projectName = null;
        Map<String, Deque<String>> parameters = exchange.getQueryParameters();
        if (parameters.size() > 0) {
            projectName = parameters.get(ConfigServerConstants.PROJECT_NAME).getFirst();
        }
        // Read services from vault
        List<Service> servicesList = provider.searchServices(clientToken, projectName);

        if (servicesList != null) {
            exchange.setStatusCode(200);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ContentType.APPLICATION_JSON.value());
            exchange.getResponseSender().send(mapper.writeValueAsString(servicesList));
        } else {
            logger.error("Could not read services from the vault");
            exchange.getResponseSender().send(mapper.writeValueAsString(servicesList));
            Status status = new Status("500");
            String errorResp = mapper.writeValueAsString(status);
            exchange.setStatusCode(status.getStatusCode());
            exchange.getResponseSender().send(errorResp);
        }
        exchange.endExchange();
    }
}
