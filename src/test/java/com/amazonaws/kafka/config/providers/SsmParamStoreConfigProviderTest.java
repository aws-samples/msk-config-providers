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


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

public class SsmParamStoreConfigProviderTest {

	static Map<String, Object> props;

    @BeforeEach
    public void setup() {
		props  = new HashMap<>();
		props.put("config.providers", "ssm");
		props.put("config.providers.ssm.class", "com.amazonaws.kafka.config.providers.MockedSsmParamStoreConfigProvider");
		props.put("config.providers.ssm.param.region", "us-west-2");
		props.put("config.providers.ssm.param.ParameterNotFoundStrategy", "fail");
	}
    
    @Test
    public void testExistingKeys() {
		// props.put("client.id", "${bootstrap.servers}"); // this doesn't work
		props.put("stringKey", "${ssm:/test/stringParam}");
		props.put("intKey", "${ssm:/test/intParam}");
		props.put("listKey", "${ssm:/test/listParam}");
		props.put("secretKey", "${ssm:/test/secretParam}");
		
    	CustomConfig testConfig = new CustomConfig(props);
    	
        assertEquals("test string value", testConfig.getString("stringKey"));
        assertEquals(777L, testConfig.getLong("intKey").longValue());
        assertEquals(Arrays.asList("el1,el2,el3".split(",")), testConfig.getList("listKey"));
        assertEquals("secret password", testConfig.getString("secretKey"));
    }

	@Test
	public void testNonExistingKeys() {
		props.put("notFound", "${ssm:/test/notFound}");
		assertThrows(ParameterNotFoundException.class, () -> new CustomConfig(props));
	}

    
    static class CustomConfig extends AbstractConfig {
    	final static String DEFAULT_DOC = "Default Doc";
    	final static ConfigDef CONFIG = new ConfigDef()
    			.define("stringKey", Type.STRING, "defaultValue", Importance.HIGH, DEFAULT_DOC)
    			.define("intKey", Type.LONG, 0, Importance.MEDIUM, DEFAULT_DOC)
    			.define("listKey", Type.LIST, Collections.emptyList(), Importance.LOW, DEFAULT_DOC)
    			.define("secretKey", Type.STRING, "", Importance.LOW, DEFAULT_DOC)
				.define("notFound", Type.STRING, "", Importance.LOW, DEFAULT_DOC)
    			;
		public CustomConfig(Map<?, ?> originals) {
			super(CONFIG, originals);
		}
    }
	
}
