package com.boycottpro.causecompanystats;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IncrementCauseCompanyStatsHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @InjectMocks
    private IncrementCauseCompanyStatsHandler handler;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testIncrementSuccess() throws Exception {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setPathParameters(Map.of(
                "company_id", "test_company",
                "cause_id", "test_cause",
                "increment", "true"
        ));

        when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenReturn(UpdateItemResponse.builder().build());

        var response = handler.handleRequest(event, context);

        assertEquals(200, response.getStatusCode());
    }

    @Test
    void testMissingPathParams() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setPathParameters(null);

        var response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void testInvalidIncrementValue() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setPathParameters(Map.of(
                "company_id", "test_company",
                "cause_id", "test_cause",
                "increment", "maybe"
        ));

        var response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
    }

    @Test
    void testExceptionHandling() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        event.setPathParameters(Map.of(
                "company_id", "test_company",
                "cause_id", "test_cause",
                "increment", "false"
        ));

        when(dynamoDb.updateItem(any(UpdateItemRequest.class))).thenThrow(RuntimeException.class);

        var response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
    }
}
