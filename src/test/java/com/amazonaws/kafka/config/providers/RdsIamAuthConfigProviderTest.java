/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify,
 * merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.amazonaws.kafka.config.providers;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.common.config.ConfigData;
import org.apache.kafka.common.config.ConfigException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RdsIamAuthConfigProviderTest {

    Map<String, Object> props;
    RdsIamAuthConfigProvider provider;

    @BeforeEach
    public void setup() {
        props = new HashMap<>();
        props.put("config.providers", "rdsiam");
        props.put("config.providers.rdsiam.class", "com.amazonaws.kafka.config.providers.RdsIamAuthConfigProvider");
        props.put("config.providers.rdsiam.param.region", "us-west-2");
        props.put("config.providers.rdsiam.param.TokenExpirySeconds", "900");
        
        provider = new RdsIamAuthConfigProvider();
        provider.configure(props);
    }

    @Test
    public void testConfigurationDefaults() {
        RdsIamAuthConfig config = new RdsIamAuthConfig(props);
        assertEquals(900, config.getInt(RdsIamAuthConfig.TOKEN_EXPIRY_SECONDS));
    }

    @Test
    public void testConfigurationCustomTokenExpiry() {
        Map<String, Object> customProps = new HashMap<>(props);
        customProps.put("config.providers.rdsiam.param.TokenExpirySeconds", "1800");
        
        RdsIamAuthConfig config = new RdsIamAuthConfig(customProps);
        assertEquals(1800, config.getInt(RdsIamAuthConfig.TOKEN_EXPIRY_SECONDS));
    }

    @Test
    public void testInvalidConnectionStringFormat() {
        Set<String> keys = Set.of("invalid-format");
        
        ConfigException exception = assertThrows(ConfigException.class, () -> {
            provider.get("path", keys);
        });
        
        assertTrue(exception.getMessage().contains("Invalid RDS connection string format"));
    }

    @Test
    public void testInvalidPortNumber() {
        Set<String> keys = Set.of("hostname:invalid-port:username");
        
        ConfigException exception = assertThrows(ConfigException.class, () -> {
            provider.get("path", keys);
        });
        
        assertTrue(exception.getMessage().contains("Invalid port number"));
    }

    @Test
    public void testEmptyHostnameOrUsername() {
        Set<String> keys1 = Set.of(":3306:username");
        Set<String> keys2 = Set.of("hostname:3306:");
        
        assertThrows(ConfigException.class, () -> provider.get("path", keys1));
        assertThrows(ConfigException.class, () -> provider.get("path", keys2));
    }

    @Test
    public void testEmptyKeysReturnsEmptyData() {
        ConfigData result = provider.get("path", Set.of());
        assertNotNull(result);
        assertTrue(result.data().isEmpty());
    }

    @Test
    public void testNullKeysReturnsEmptyData() {
        ConfigData result = provider.get("path", null);
        assertNotNull(result);
        assertTrue(result.data().isEmpty());
    }
}