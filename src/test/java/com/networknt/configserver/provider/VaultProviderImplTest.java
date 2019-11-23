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

import com.networknt.configserver.model.Service;
import com.networknt.configserver.model.ServiceConfigs;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.client.builder.HttpClientBuilder;
import com.networknt.config.Config;
import io.undertow.client.ClientResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Config.class, VaultProviderImpl.class, HttpClientBuilder.class, ClientResponse.class, ObjectMapper.class})
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "org.w3c.*", "javax.crypto.*"})
public class VaultProviderImplTest {
    private static IProvider vaultProvider = new VaultProviderImpl();
    private static ObjectMapper objectMapper = Mockito.mock(ObjectMapper.class);
    @Mock
    Config config;
    @Mock
    HttpClientBuilder httpClientBuilder;
    @Mock
    ClientResponse clientResponse;
    @Mock
    Future future;
    @Mock
    Service service;

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(Config.class);
        when(Config.getInstance()).thenReturn(config);
        when(config.getJsonMapConfig(anyString())).thenReturn(new HashMap<>());
        when(config.getMapper()).thenReturn(objectMapper);

        PowerMockito.whenNew(HttpClientBuilder.class).withNoArguments().thenReturn(httpClientBuilder);
        when(httpClientBuilder.setApiHost(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.setClientRequest(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.setRequestBody(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.setLatch(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.setConnectionRequestTimeout(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.setConnectionCacheTTLms(10000)).thenReturn(httpClientBuilder);
        when(httpClientBuilder.setHeaderValue(any(), anyString())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.send()).thenReturn(future);

        when(future.get()).thenReturn(clientResponse);
        when(clientResponse.getResponseCode()).thenReturn(200);
        when(clientResponse.getAttachment(any())).thenReturn("abc1234");
    }

    @Test
    public void testLogin() throws Exception {
        Map<String, Map<String, Object>> outerMap = new HashMap<>();
        Map<String, Object> innerMap = new HashMap<>();
        innerMap.put("client_token", "abc12345");
        outerMap.put("auth", innerMap);
        when(objectMapper.readValue(anyString(), Mockito.<TypeReference<Map<String, Map<String, Object>>>>any())).thenReturn(outerMap);
        String token = vaultProvider.login("Basic bmV0d29ya250Y29uZmlnc3VzZXI6bmV0d29ya250MTIz");
        verify(httpClientBuilder, times(1)).send();
        Assert.assertEquals("abc12345", token);
    }

    @Test
    public void testGetServiceConfigs() throws Exception {
        mockService();
        Map configsMap = new HashMap<>();
        configsMap.put("company", "networknt");
        configsMap.put("location", "canada");
        configsMap = getConfigsMap(configsMap);
        when(objectMapper.readValue(anyString(), Mockito.<TypeReference<Map<String, Object>>>any())).thenReturn(configsMap);
        ServiceConfigs serviceConfigs = vaultProvider.getServiceConfigs("xyz123", service);
        Map response = (HashMap) serviceConfigs.getConfigProperties();
        verify(httpClientBuilder, times(2)).send();
        Assert.assertEquals(2, response.size());
        Assert.assertEquals("networknt", response.get("company"));
    }

    @Test
    public void testGetServiceCertificates() throws Exception {
        mockService();
        Map certsMap = new HashMap<>();
        certsMap.put("primary.crt", "MIIDmzCCAoOgAwI");
        certsMap.put("secondary.crt", "SDFCDmzCCAoOgAwI");
        certsMap = getConfigsMap(certsMap);
        when(objectMapper.readValue(anyString(), Mockito.<TypeReference<Map<String, Object>>>any())).thenReturn(certsMap);
        ServiceConfigs serviceCerts = vaultProvider.getServiceCertificates("123xyx", service);
        Map response = (HashMap) serviceCerts.getConfigProperties();
        verify(httpClientBuilder, times(2)).send();
        Assert.assertEquals(2, response.size());
        Assert.assertEquals("MIIDmzCCAoOgAwI", response.get("primary.crt"));
    }

    @Test
    public void testGetServiceFiles() throws Exception {
        mockService();
        Map filesMap = new HashMap<>();
        filesMap.put("logback.xml", "logging info");
        filesMap.put("values.yml", "External values");
        filesMap = getConfigsMap(filesMap);
        when(objectMapper.readValue(anyString(), Mockito.<TypeReference<Map<String, Object>>>any())).thenReturn(filesMap);
        ServiceConfigs serviceFiles = vaultProvider.getServiceFiles("abc1234", service);
        verify(httpClientBuilder, times(2)).send();
        Map response = (HashMap) serviceFiles.getConfigProperties();
        Assert.assertEquals(2, response.size());
        Assert.assertEquals("logging info", response.get("logback.xml"));
    }

    @Test
    public void testSearchServices() throws Exception {
        Map map = new HashMap<>();
        Map outerMap = new HashMap<>();
        List list = new ArrayList();
        list.add("api-config-server/");
        outerMap.put("keys", list);
        map.put("data", outerMap);
        when(objectMapper.readValue(anyString(), Mockito.<TypeReference<Map<String, Object>>>any())).thenReturn(map);
        List<Service> serviceList = vaultProvider.searchServices("abc1234", "retail");
        verify(httpClientBuilder, times(1)).send();
        Assert.assertEquals("api-config-server/", serviceList.get(0).getServiceName());
    }

    private void mockService() {
        when(service.getProjectName()).thenReturn("retail");
        when(service.getProjectVersion()).thenReturn("v1");
        when(service.getServiceName()).thenReturn("retail-dev");
        when(service.getServiceVersion()).thenReturn("v1");
        when(service.getEnvironment()).thenReturn("dev");
    }

    private Map getConfigsMap(Map innerMap) {
        Map map = new HashMap<>();
        Map outerMap = new HashMap<>();
        outerMap.put("data", innerMap);
        map.put("data", outerMap);
        return map;
    }
}
