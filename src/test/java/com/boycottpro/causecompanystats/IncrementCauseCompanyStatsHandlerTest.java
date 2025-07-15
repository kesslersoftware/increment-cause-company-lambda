package com.boycottpro.causecompanystats;

import com.boycottpro.causecompanystats.model.IncrementForm;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IncrementCauseCompanyStatsHandlerTest {

    @Mock
    private DynamoDbClient dynamoDb;

    @Mock
    private Context context;

    @InjectMocks
    private IncrementCauseCompanyStatsHandler handler;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testSuccessfulIncrement() throws Exception {
        // Setup input
        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent();
        requestEvent.setPathParameters(Map.of("cause_id", "c123", "company_id", "co456"));

        IncrementForm form = new IncrementForm("Some Company", "Some Cause", true);
        requestEvent.setBody(objectMapper.writeValueAsString(form));

        // Mock DynamoDB behavior
        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        // Validate
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"recordUpdated\":true"));
    }

    @Test
    public void testMissingPathParams() {
        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent();
        requestEvent.setPathParameters(null);

        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid path parameters"));
    }

    @Test
    public void testDynamoDbException() throws Exception {
        // Setup input
        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent();
        requestEvent.setPathParameters(Map.of("cause_id", "c789", "company_id", "co789"));

        IncrementForm form = new IncrementForm("Company", "Cause", false);
        requestEvent.setBody(objectMapper.writeValueAsString(form));

        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DB error").build());

        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("DB error"));
    }
}
