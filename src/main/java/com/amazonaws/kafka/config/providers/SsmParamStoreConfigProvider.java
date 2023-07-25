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

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.kafka.common.config.ConfigData;
import org.apache.kafka.common.config.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.kafka.config.providers.common.AwsServiceConfigProvider;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.SsmClientBuilder;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

/**
 * This class implements a ConfigProvider for AWS System Manager / Parameter Store.<br>
 * 
 * <p><b>Usage:</b><br>
 * In a configuration file (e.g. {@code client.properties}) define following properties:<br>
 * 
 * <pre>
 * #        Step1. Configure the System Manager Param Store as config provider:
 * config.providers=ssm
 * config.providers.ssm.class=com.amazonaws.kafka.config.providers.SsmParamStoreConfigProvider
 * # optional parameter for region:
 * config.providers.ssm.param.region=us-west-2
 * # optional parameter, see more details below
 * config.providers.ssm.param.NotFoundStrategy=fail
 * 
 * #        Step 2. Usage of AWS SSM Parameter Store as config provider:
 * db.username=${ssm::/msk/TestKafkaConfig/username}
 * db.password=${ssm::/msk/TestKafkaConfig/password}
 * </pre>
 * 
 * Note, this config provide implementation assumes path as a parameter name.
 * Nested values aren't supported at this point.<br>
 * 
 * SsmParamStoreConfigProvider can be configured using parameters.<br>
 * Format:<br>
 * {@code config.providers.ssm.param.<param_name> = <param_value>}<br>
 * 
 * @param region - defines a region to get a secret from.
 * @param NotFoundStrategy - defines an action in case requested secret or a key in a value cannot be resolved. <br>
 * <ul> Possible values are:
 *  <ul>{@code fail} - (Default) the code will throw an exception {@code ConfigNotFoundException}</ul>
 *  <ul>{@code ignore} - a value will remain with tokens without any change </ul>
 * </ul>
 * 
 * 
 * Expression usage:<br>
 * <code>property_name=${ssm::/Path/To/Parameter}</code>
 *
 */
public class SsmParamStoreConfigProvider extends AwsServiceConfigProvider {
	
    private final Logger log = LoggerFactory.getLogger(getClass());

	private String notFoundStrategy;

	private SsmParamStoreConfig config;
    private SsmClientBuilder cBuilder;

    @Override
	public void configure(Map<String, ?> configs) {
        this.config = new SsmParamStoreConfig(configs);
        setCommonConfig(config);

        this.notFoundStrategy = config.getString(SsmParamStoreConfig.NOT_FOUND_STRATEGY);
        
        // set up a builder:
        this.cBuilder = SsmClient.builder();
        setClientCommonConfig(this.cBuilder);
	}

    /**
     * Retrieves all parameters at the given path in SSM Parameters Store.
     *
     * @param path the path in Parameters Store
     * @return the configuration data
     */
    @Override
    public ConfigData get(String path) {
    	
        return get(path, Collections.emptySet());
    }

    
    /**
     * Retrieves all parameters at the given path in SSM Parameters Store with given key.
     *
     * @param path the path in Parameters Store
     * @return the configuration data
     */
    @Override
	public ConfigData get(String path, Set<String> keys) {
        Map<String, String> data = new HashMap<>();
		if (   (path == null || path.isEmpty()) 
			&& (keys== null || keys.isEmpty())   ) {
			return new ConfigData(data);
		}
		
		SsmClient ssmClient = checkOrInitSsmClient();
		Long ttl = null;

		for (String keyWithOptions: keys) {
		    String key = parseKey(keyWithOptions);
		    Map<String, String> options = parseKeyOptions(keyWithOptions);
		    ttl = getUpdatedTtl(ttl, options);
		    
			GetParameterRequest parameterRequest = GetParameterRequest.builder().name(key).withDecryption(true).build();
			try {
				GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
				String value = parameterResponse.parameter().value();
				data.put(keyWithOptions, value);
			} catch(ParameterNotFoundException e) {
				log.info("Parameter " + key + "not found. Value will be handled according to a strategy defined by 'NotFoundStrategy'");
				handleNotFoundByStrategy(data, path, key, e);
			}
		}
		return ttl == null ? new ConfigData(data) : new ConfigData(data, ttl);
	}

    protected SsmClient checkOrInitSsmClient() {
        return cBuilder.build();
    }
	
	@Override
	public void close() throws IOException {
	}
	
    private void handleNotFoundByStrategy(Map<String, String> data, String path, String key, RuntimeException e) {
        if (SsmParamStoreConfig.NOT_FOUND_IGNORE.equals(this.notFoundStrategy) 
                && key != null && !key.isBlank()) {
            data.put(key, "");
        } else if (SsmParamStoreConfig.NOT_FOUND_FAIL.equals(this.notFoundStrategy)) {
            if (e != null) {
                throw e;
            }else {
                throw new ConfigException(String.format("Secret undefined %s:%s", path, key));
            }
        }
    }
}
