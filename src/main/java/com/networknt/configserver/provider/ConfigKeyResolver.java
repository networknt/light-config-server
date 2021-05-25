package com.networknt.configserver.provider;

import com.networknt.configserver.constants.ConfigServerConstants;
import com.networknt.configserver.model.Service;
import com.networknt.configserver.model.ServiceConfig;

import java.util.List;

public class ConfigKeyResolver {
    List<String> configExtension = List.of(".yml", ".yaml");
    List<String> certExtension = List.of(".truststore", ".keystore");
    String resolve(Service service, ServiceConfig serviceConfig){
        StringBuffer configPath = new StringBuffer();
        String filename = serviceConfig.getName();
        String suffix = filename.substring(filename.lastIndexOf('.'));
        if (configExtension.contains(suffix)) {
            configPath.append(ConfigServerConstants.CONFIGS);
        } else if (certExtension.contains(suffix)) {
            configPath.append(ConfigServerConstants.CERTS);
        } else {
            configPath.append(ConfigServerConstants.FILES);
        }

        configPath
                .append(ConfigServerConstants.SLASH).append(service.getProjectName())
                .append(ConfigServerConstants.SLASH).append(service.getServiceName())
                .append(ConfigServerConstants.SLASH).append(service.getServiceVersion())
                .append(ConfigServerConstants.SLASH).append(service.getEnvironment());
        return configPath.toString();
    };

}
