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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.config.Config;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.FileInputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Config.class, FileSystemProviderImpl.class, ObjectMapper.class, Paths.class})
public class FileSystemProviderImplTest {
    private static IProvider fileSystemProvider = new FileSystemProviderImpl();
    @Mock
    Config config;
    @Mock
    Service service;
    @Mock
    ObjectMapper objectMapper;

    @Before
    public void setUp() {
        PowerMockito.mockStatic(Config.class);
        when(Config.getInstance()).thenReturn(config);
        when(config.getMapper()).thenReturn(objectMapper);
        Map configDirMap = new HashMap();
        configDirMap.put("serviceConfigsDir", "/users/userName");
        when(config.getJsonMapConfig(anyString())).thenReturn(configDirMap);
    }

    @Test
    public void testGetServiceConfigs() throws Exception {
        mockService();
        Map<String, Object> globalConfigsMap = new HashMap();
        globalConfigsMap.put("name", "global networknt");
        globalConfigsMap.put("global", "global configs");
        Map<String, Object> serviceConfigsMap = new HashMap();
        serviceConfigsMap.put("name", "service networknt");
        serviceConfigsMap.put("service", "service configs");
        when(config.getJsonMapConfigNoCache(anyString(), anyString())).thenReturn(globalConfigsMap, serviceConfigsMap);

        ServiceConfigs serviceConfigs = fileSystemProvider.getServiceConfigs("xyz123", service);
        verify(config, times(2)).getJsonMapConfigNoCache(anyString(), anyString());
        Map response = (HashMap) serviceConfigs.getConfigProperties();
        Assert.assertEquals(3, response.size());
        Assert.assertEquals("service networknt", response.get("name"));
    }

    @Test
    public void testGetServiceCertificates() throws Exception {
        mockService();
        mockConfigDir();
        ServiceConfigs serviceConfigs = fileSystemProvider.getServiceCertificates("xyz123", service);
        Map response = (HashMap) serviceConfigs.getConfigProperties();
        Assert.assertEquals(1, response.size());
        PowerMockito.verifyStatic(VerificationModeFactory.times(2));
        Paths.get(anyString());
    }

    @Test
    public void testGetServiceFiles() throws Exception {
        mockService();
        mockConfigDir();
        ServiceConfigs serviceConfigs = fileSystemProvider.getServiceFiles("xyz123", service);
        Map response = (HashMap) serviceConfigs.getConfigProperties();
        Assert.assertEquals(1, response.size());
        PowerMockito.verifyStatic(VerificationModeFactory.times(2));
        Paths.get(anyString());
    }

    private void mockConfigDir() throws Exception {
        Path path = FileSystems.getDefault().getPath("src/test/resources/config");
        PowerMockito.mockStatic(Paths.class);
        Mockito.when(Paths.get(Mockito.anyString())).thenReturn(path);
        FileInputStream fileInputStream = new FileInputStream("src/test/resources/config/server.yml");
        PowerMockito.whenNew(FileInputStream.class).withArguments(anyString()).thenReturn(fileInputStream);
    }

    private void mockService() {
        when(service.getProjectName()).thenReturn("retail");
        when(service.getProjectVersion()).thenReturn("v1");
        when(service.getServiceName()).thenReturn("retail-dev");
        when(service.getServiceVersion()).thenReturn("v1");
        when(service.getEnvironment()).thenReturn("dev");
    }

}
