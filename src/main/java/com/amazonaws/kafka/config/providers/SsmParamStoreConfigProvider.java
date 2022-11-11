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
import org.apache.kafka.common.config.provider.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.regions.Region;
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
 * #        Step1. Configure the secrets manager as config provider:
 * config.providers=ssm
 * config.providers.secretsmanager.class=com.amazonaws.kafka.connect.config.providers.SsmParamStoreConfigProvider
 * # optional parameter for region:
 * config.providers.secretsmanager.param.region=us-west-2
 * # optional parameter, see more details below
 * config.providers.secretsmanager.param.NotFoundStrategy=fail
 * 
 * #        Step 2. Usage of AWS SSM Parameter Store as config provider:
 * db.username=${ssm:/msk/TestKafkaConfig/username}
 * db.password=${ssm:/msk/TestKafkaConfig/password}
 * </pre>
 * 
 * Note, this config provide implementation assumes path as a parameter name.
 * Nested values aren't supported at this point.<br>
 * 
 * SsmParamStoreConfigProvider can be configured using parameters.<br>
 * Format:<br>
 * {@code config.providers.secretsmanager.param.<param_name> = <param_value>}<br>
 * 
 * @param region - defines a region to get a secret from.
 * @param NotFoundStrategy - defines an action in case requested secret or a key in a value cannot be resolved. <br>
 * <ul>Passible values are:
 *  <ul>{@code fail} - (Default) the code will throw an exception {@code ConfigNotFoundException}</ul>
 *  <ul>{@code ignore} - a value will remain with tokens without any change </ul>
 *  <ul>{@code empty} - empty string will be assigned to a config</ul>
 *  <ul>any other value is equivalent to {@code ignore}</ul>
 * </ul>
 * 
 * 
 * Expression usage:<br>
 * <code>property_name=${ssm:/Path/To/Parameter}</code>
 *
 */
public class SsmParamStoreConfigProvider implements ConfigProvider {
	
    private final Logger log = LoggerFactory.getLogger(getClass());

	
	public static final String REGION_PARAM_NAME = "region";
	public static final String PATH_DELIMITER_PARAM_NAME = "delimiter";
	public static final String PATH_DELIMITER_DEFAULT = "/";
	public static final String NOT_FOUND_STRATEGY_PARAM_NAME = "NotFoundStrategy";
	
	private String region;
	private String pathDelimiter = PATH_DELIMITER_DEFAULT;
	private ParamNotFoundStrategy notFoundStrategy = ParamNotFoundStrategy.FAIL;

	protected SsmClient ssmClient;

	public void configure(Map<String, ?> configs) {
		Object value;
		if ((value = configs.get(REGION_PARAM_NAME)) != null) {
			this.region = value.toString();
		}
		if ((value = configs.get(PATH_DELIMITER_PARAM_NAME)) != null) {
			this.pathDelimiter = value.toString();
		}
		
		if ((value = configs.get(NOT_FOUND_STRATEGY_PARAM_NAME)) != null) {
			this.notFoundStrategy = ParamNotFoundStrategy.of((String)value);
		}
	}

    /**
     * Retrieves all parameters at the given path in SSM Parameters Store.
     *
     * @param path the path in Parameters Store
     * @return the configuration data
     */
    public ConfigData get(String path) {
    	
        return get(path, Collections.emptySet());
    }

    
    /**
     * Retrieves all parameters at the given path in SSM Parameters Store with given key.
     *
     * @param path the path in Parameters Store
     * @return the configuration data
     */
	public ConfigData get(String path, Set<String> keys) {
        Map<String, String> data = new HashMap<>();
		if (   (path == null || path.isEmpty()) 
			&& (keys== null || keys.isEmpty())   ) {
			return new ConfigData(data);
		}

		checkOrInitSsmClient();

		for (String key: keys) {
			String paramName = getStoreParamName(path, key);
			GetParameterRequest parameterRequest = GetParameterRequest.builder().name(paramName).withDecryption(true).build();
			try {
				GetParameterResponse parameterResponse = ssmClient.getParameter(parameterRequest);
				String value = parameterResponse.parameter().value();
				data.put(key, value);
			} catch(ParameterNotFoundException e) {
				if (this.notFoundStrategy == ParamNotFoundStrategy.FAIL) {
					throw e;
				}
				log.info("Parameter " + paramName + "not found. Value will be handled according to a strategy defined by 'config.providers.ssm.param.ParameterNotFoundStrategy'");
				handleNotFoundByStrategy(data, key);
			}
		}
		return new ConfigData(data);
	}

	/*
	 * Concatenates path and key for a single value of a parameter name in Store
	 */
	private String getStoreParamName(String path, String key) {
		if (path == null || (path = path.trim()).isEmpty()) {
			return key;
		}
		return path + (path.endsWith(pathDelimiter)? "" : pathDelimiter) + key;
	}

	protected void checkOrInitSsmClient() {
		if (this.ssmClient == null) {
			SsmClientBuilder ssmClientBuilder = SsmClient.builder();
			// if region is provided in config, use it, otherwise let the env provide it.
			if (region != null) {
				try {
					ssmClientBuilder = ssmClientBuilder.region(Region.of(region));
				} catch(Exception e) {
					log.error("Failed to set a region '" + region + "'. Using default region from the chain of environment settings... Exception: ", e);
				}
			}
			this.ssmClient = ssmClientBuilder.build();
		}
	}

	public void close() throws IOException {
	}
	
	private void handleNotFoundByStrategy(Map<String, String> data, String key) {
		if (this.notFoundStrategy == ParamNotFoundStrategy.IGNORE) {
			//do nothing, we just ignore
		} else if (this.notFoundStrategy == ParamNotFoundStrategy.EMPTY) {
			data.put(key, "");
		}
	}

	private static enum ParamNotFoundStrategy{
		FAIL, NULL, EMPTY, IGNORE;
		
		static ParamNotFoundStrategy of(String strValue) {
			if (strValue.equalsIgnoreCase("fail")) {
				return FAIL;
			// }else if (strValue.equalsIgnoreCase("null")) {
			//	return NULL;
			}else if (strValue.equalsIgnoreCase("empty")) {
				return EMPTY;
			}else if (strValue.equalsIgnoreCase("ignore")) {
				return IGNORE;
			}
			// Default
			return IGNORE;
		}
	}
}
