package com.networknt.configserver.handler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.body.BodyHandler;
import com.networknt.config.Config;
import com.networknt.configserver.constants.ConfigServerConstants;
import com.networknt.configserver.model.Service;
import com.networknt.configserver.model.ServiceConfig;
import com.networknt.configserver.helper.AuthorizationHelper;
import com.networknt.configserver.provider.IProvider;
import com.networknt.exception.ApiException;
import com.networknt.handler.LightHttpHandler;
import com.networknt.httpstring.ContentType;
import com.networknt.status.Status;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Deque;
import java.util.List;
import java.util.Map;

public class PatchServiceConfigsHandler implements LightHttpHandler {
    static Logger logger = LoggerFactory.getLogger(PatchServiceConfigsHandler.class);

    private static final ObjectMapper mapper = Config.getInstance().getMapper();

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        IProvider provider = IProvider.getInstance();

        // Login to provider backend and get the token
        String clientToken = null;
        try {
            clientToken = provider.login(AuthorizationHelper.getAuthorization(exchange));
        } catch (ApiException e) {
            this.setExchangeStatus(exchange, e.getStatus());
        }

        Service service = getService(exchange);
        Object configStr = exchange.getAttachment(BodyHandler.REQUEST_BODY);
        List<ServiceConfig> serviceConfigs = Config.getInstance().getMapper().convertValue(configStr, new TypeReference<List<ServiceConfig>>() {
        });

        logger.debug("Service Files requested for:{}", service);
        List<String> fails = provider.saveServiceConfigs(clientToken, service, serviceConfigs);
        if (fails.size() == 0) {
            exchange.setStatusCode(200);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, ContentType.APPLICATION_JSON.value());
            exchange.getResponseSender().send(mapper.writeValueAsString(serviceConfigs));
        } else {
            logger.error("Could not save configs from the provider");
            exchange.getResponseSender().send(mapper.writeValueAsString(serviceConfigs));
            Status status = new Status("500", "failed with" + fails.toString());
            String errorResp = mapper.writeValueAsString(status);
            exchange.setStatusCode(status.getStatusCode());
            exchange.getResponseSender().send(errorResp);
        }
        
        exchange.endExchange();
    }

    private Service getService(HttpServerExchange exchange) {
        //Get the inputs from request object!.
        Map<String, Deque<String>> parameters = exchange.getQueryParameters();
        Service service = new Service();
        service.setProjectName(parameters.get(ConfigServerConstants.PROJECT_NAME).getFirst());
        service.setProjectVersion(parameters.get(ConfigServerConstants.PROJECT_VERSION).getFirst());
        service.setServiceName(parameters.get(ConfigServerConstants.SERVICE_NAME).getFirst());
        service.setServiceVersion(parameters.get(ConfigServerConstants.SERVICE_VERSION).getFirst());
        service.setEnvironment(parameters.get(ConfigServerConstants.ENVIRONMENT).getFirst());
        return service;
    }
}
