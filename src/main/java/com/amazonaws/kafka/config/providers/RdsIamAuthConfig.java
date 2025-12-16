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

import java.util.Map;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import com.amazonaws.kafka.config.providers.common.CommonConfigUtils;

public class RdsIamAuthConfig extends AbstractConfig {

    public static final String TOKEN_EXPIRY_SECONDS = "TokenExpirySeconds";
    public static final int DEFAULT_TOKEN_EXPIRY_SECONDS = 900; // 15 minutes
    
    private static final String TOKEN_EXPIRY_SECONDS_DOC = 
            "The number of seconds for which the generated RDS IAM authentication token will be valid. "
            + "Default is 900 seconds (15 minutes). The token will be cached and reused until it expires.";

    public RdsIamAuthConfig(Map<?, ?> originals) {
        super(config(), originals);
    }

    private static ConfigDef config() {
        return new ConfigDef(CommonConfigUtils.COMMON_CONFIG)
                .define(
                        TOKEN_EXPIRY_SECONDS,
                        ConfigDef.Type.INT,
                        DEFAULT_TOKEN_EXPIRY_SECONDS,
                        ConfigDef.Range.between(1, 3600),
                        ConfigDef.Importance.LOW,
                        TOKEN_EXPIRY_SECONDS_DOC
                        )
                ;
    }
}