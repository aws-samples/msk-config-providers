package com.amazonaws.kafka.config.providers;

import org.mockito.stubbing.Answer;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

import static org.mockito.Mockito.*;

public class MockedSsmParamStoreConfigProvider extends SsmParamStoreConfigProvider {

    @Override
    protected SsmClient checkOrInitSsmClient() {
        SsmClient ssmClient = mock(SsmClient.class);
        when(ssmClient.getParameter(request("/test/stringParam"))).thenAnswer(
                (Answer<GetParameterResponse>) invocation -> response("stringParam", "test string value")
        );
        when(ssmClient.getParameter(request("/test/intParam"))).thenAnswer(
                (Answer<GetParameterResponse>) invocation -> response("intParam", "777")
        );
        when(ssmClient.getParameter(request("/test/listParam"))).thenAnswer(
                (Answer<GetParameterResponse>) invocation -> response("listParam", "el1,el2,el3")
        );
        when(ssmClient.getParameter(request("/test/secretParam"))).thenAnswer(
                (Answer<GetParameterResponse>) invocation -> response("secretParam", "secret password")
        );
        when(ssmClient.getParameter(request("/test/notFound"))).thenThrow(ParameterNotFoundException.class);
        
        return ssmClient;
    }

    private GetParameterRequest request(String param) {
        return GetParameterRequest.builder().name(param).withDecryption(true).build();
    }

    private GetParameterResponse response(String param, String value) {
        return GetParameterResponse.builder().parameter(Parameter.builder().name(param).value(value).build()).build();
    }
}
