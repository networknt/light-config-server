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

import com.networknt.configserver.model.ProxyConfig;
import com.networknt.configserver.model.Service;
import com.networknt.configserver.model.ServiceConfigs;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
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

import static org.mockito.Mockito.*;

import com.networknt.config.Config;

import java.util.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({GitProviderImpl.class, Config.class, ObjectMapper.class, HttpClients.class, EntityUtils.class})
@PowerMockIgnore({"javax.*", "org.xml.sax.*", "org.apache.log4j.*"})
public class GitProviderImplTest {
    private static IProvider gitProvider = new GitProviderImpl();
    private static ObjectMapper objectMapper = Mockito.mock(ObjectMapper.class);
    private static String configsContent = "c2VydmVyLmh0dHBzUG9ydDogOTQ0NAphY1ByZWZpeDogTmV0d29ya250IHNlcnZpY2UKZGVmYXVsdC5hY1R5cGU6IE5ldHdvcmtudCBzZXJ2aWNlIEFkdmFudGFnZQ==";

    @Mock
    Config config;
    @Mock
    HttpClients httpClients;
    @Mock
    HttpGet httpGet;
    @Mock
    ProxyConfig proxyConfig;
    @Mock
    CloseableHttpClient httpClient;
    @Mock
    CloseableHttpResponse httpResponse;
    @Mock
    StatusLine statusLine;
    @Mock
    EntityUtils entityUtils;
    @Mock
    Service service;

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(Config.class);
        PowerMockito.mockStatic(EntityUtils.class);
        PowerMockito.mockStatic(HttpClients.class);
        PowerMockito.whenNew(HttpGet.class).withArguments(anyString()).thenReturn(httpGet);

        when(Config.getInstance()).thenReturn(config);
        when(config.getJsonMapConfig(anyString())).thenReturn(new HashMap<>());
        when(config.getJsonObjectConfig("proxy", ProxyConfig.class)).thenReturn(proxyConfig);
        when(config.getMapper()).thenReturn(objectMapper);

        when(httpClients.createDefault()).thenReturn(httpClient);
        when(proxyConfig.getEnableProxy()).thenReturn(false);
        when(httpClient.execute(httpGet)).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(statusLine.getStatusCode()).thenReturn(200);
        when(entityUtils.toString(any())).thenReturn("respBody");
    }

    @Test
    public void testLogin() throws Exception {
        String token = gitProvider.login("Bearer b4285895f0e67");
        Assert.assertEquals("Bearer b4285895f0e67", token);
    }

    @Test
    public void testGetServiceConfigs() throws Exception {
        mockService();
        Map configsMap = new HashMap<>();
        configsMap.put("content", configsContent);
        when(objectMapper.readValue(anyString(), Mockito.<TypeReference<Map<String, Object>>>any())).thenReturn(configsMap, getConfigsMap());
        ServiceConfigs serviceConfigs = gitProvider.getServiceConfigs("Bearer b4285895f0e67", service);
        Map response = (HashMap) serviceConfigs.getConfigProperties();
        verify(httpClient, times(2)).execute(httpGet);
        Assert.assertEquals(3, response.size());
        Assert.assertEquals(9444, response.get("server.httpsPort"));
    }

    @Test
    public void testGetServiceCertificates() throws Exception {
        mockService();
        mockFileNamesAndFileContent();
        ServiceConfigs serviceConfigs = gitProvider.getServiceCertificates("Bearer b4285895f0e67", service);
        Map certsMap = (HashMap) serviceConfigs.getConfigProperties();
        Assert.assertEquals(1, certsMap.size());
        verify(httpClient, times(4)).execute(httpGet);
    }

    @Test
    public void testGetServiceFiles() throws Exception {
        mockService();
        mockFileNamesAndFileContent();
        ServiceConfigs serviceConfigs = gitProvider.getServiceFiles("Bearer b4285895f0e67", service);
        Map filesMap = (HashMap) serviceConfigs.getConfigProperties();
        Assert.assertEquals(1, filesMap.size());
        verify(httpClient, times(4)).execute(httpGet);
    }

    private void mockService() {
        when(service.getProjectName()).thenReturn("retail");
        when(service.getProjectVersion()).thenReturn("v1");
        when(service.getServiceName()).thenReturn("retail-dev");
        when(service.getServiceVersion()).thenReturn("v1");
        when(service.getEnvironment()).thenReturn("dev");
    }

    private void mockFileNamesAndFileContent() throws Exception {
        List<Map<String, Object>> fileNamesList = new ArrayList<>();
        Map map = new HashMap();
        map.put("name", "fileName.txt");
        fileNamesList.add(map);

        map = new HashMap<>();
        map.put("content", "file content");
        when(objectMapper.readValue(anyString(), Mockito.<TypeReference<Map<String, Object>>>any())).
                thenReturn(fileNamesList, map, fileNamesList, map);

    }

    private Map getConfigsMap() {
        Map map = new HashMap<>();
        map.put("server.httpsPort", 9444);
        map.put("acPrefix", "RET");
        map.put("default.acType", "RETAIL");
        return map;
    }
}