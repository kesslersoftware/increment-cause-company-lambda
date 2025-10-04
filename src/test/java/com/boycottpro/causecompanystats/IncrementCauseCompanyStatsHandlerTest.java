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

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.lang.reflect.Field;
import com.fasterxml.jackson.core.JsonProcessingException;

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
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);
        event.setPathParameters(Map.of("cause_id", "c123", "company_id", "co456"));

        IncrementForm form = new IncrementForm("Some Company", "Some Cause", true);
        event.setBody(objectMapper.writeValueAsString(form));

        // Mock DynamoDB behavior
        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(UpdateItemResponse.builder().build());

        // Execute
        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        // Validate
        assertEquals(200, response.getStatusCode());
        assertTrue(response.getBody().contains("\"recordUpdated\":true"));
    }

    @Test
    public void testMissingPathParams() {
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);
        event.setPathParameters(null);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid path parameters"));
    }

    @Test
    public void testDynamoDbException() throws Exception {
        // Setup input
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);
        event.setPathParameters(Map.of("cause_id", "c789", "company_id", "co789"));

        IncrementForm form = new IncrementForm("Company", "Cause", false);
        event.setBody(objectMapper.writeValueAsString(form));

        when(dynamoDb.updateItem(any(UpdateItemRequest.class)))
                .thenThrow(DynamoDbException.builder().message("DB error").build());

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Unexpected server error"));
    }

    @Test
    public void testDefaultConstructor() {
        // Test the default constructor coverage
        // Note: This may fail in environments without AWS credentials/region configured
        try {
            IncrementCauseCompanyStatsHandler handler = new IncrementCauseCompanyStatsHandler();
            assertNotNull(handler);

            // Verify DynamoDbClient was created (using reflection to access private field)
            try {
                Field dynamoDbField = IncrementCauseCompanyStatsHandler.class.getDeclaredField("dynamoDb");
                dynamoDbField.setAccessible(true);
                DynamoDbClient dynamoDb = (DynamoDbClient) dynamoDbField.get(handler);
                assertNotNull(dynamoDb);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                fail("Failed to access DynamoDbClient field: " + e.getMessage());
            }
        } catch (software.amazon.awssdk.core.exception.SdkClientException e) {
            // AWS SDK can't initialize due to missing region configuration
            // This is expected in Jenkins without AWS credentials - test passes
            System.out.println("Skipping DynamoDbClient verification due to AWS SDK configuration: " + e.getMessage());
        }
    }

    @Test
    public void testUnauthorizedUser() {
        // Test the unauthorized block coverage
        handler = new IncrementCauseCompanyStatsHandler(dynamoDb);

        // Create event without JWT token (or invalid token that returns null sub)
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        // No authorizer context, so JwtUtility.getSubFromRestEvent will return null

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, null);

        assertEquals(401, response.getStatusCode());
        assertTrue(response.getBody().contains("Unauthorized"));
    }

    @Test
    public void testJsonProcessingExceptionInResponse() throws Exception {
        // Test JsonProcessingException coverage in response method by using reflection
        handler = new IncrementCauseCompanyStatsHandler(dynamoDb);

        // Use reflection to access the private response method
        java.lang.reflect.Method responseMethod = IncrementCauseCompanyStatsHandler.class.getDeclaredMethod("response", int.class, Object.class);
        responseMethod.setAccessible(true);

        // Create an object that will cause JsonProcessingException
        Object problematicObject = new Object() {
            public Object writeReplace() throws java.io.ObjectStreamException {
                throw new java.io.NotSerializableException("Not serializable");
            }
        };

        // Create a circular reference object that will cause JsonProcessingException
        Map<String, Object> circularMap = new HashMap<>();
        circularMap.put("self", circularMap);

        // This should trigger the JsonProcessingException -> RuntimeException path
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            try {
                responseMethod.invoke(handler, 500, circularMap);
            } catch (java.lang.reflect.InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
                throw new RuntimeException(e.getCause());
            }
        });

        // Verify it's ultimately caused by JsonProcessingException
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof JsonProcessingException,
                "Expected JsonProcessingException, got: " + cause.getClass().getSimpleName());
    }

    @Test
    public void testOnlyCauseIdMissing() {
        // Test lines 48, 50, 53-56: causeId null, companyId present
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("company_id", "co456");
        event.setPathParameters(pathParams);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid path parameters"));
        assertTrue(response.getBody().contains("Missing cause_id or company_id"));
    }

    @Test
    public void testOnlyCompanyIdMissing() {
        // Test lines 48, 50, 53-56: companyId null, causeId present
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        Map<String, String> pathParams = new HashMap<>();
        pathParams.put("cause_id", "c123");
        event.setPathParameters(pathParams);

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid path parameters"));
        assertTrue(response.getBody().contains("Missing cause_id or company_id"));
    }

    @Test
    public void testBothCauseIdAndCompanyIdMissing() {
        // Test lines 48, 50, 53-56: both null
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);

        event.setPathParameters(new HashMap<>());

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Invalid path parameters"));
        assertTrue(response.getBody().contains("Missing cause_id or company_id"));
    }

    @Test
    public void testIncrementWithMissingCompanyName() throws Exception {
        // Test lines 106-107: increment=true, companyName null
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);
        event.setPathParameters(Map.of("cause_id", "c123", "company_id", "co456"));

        IncrementForm form = new IncrementForm(null, "Some Cause", true);
        event.setBody(objectMapper.writeValueAsString(form));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Unexpected server error"));
        assertTrue(response.getBody().contains("companyName and causeDesc are required when incrementing"));
    }

    @Test
    public void testIncrementWithMissingCauseDesc() throws Exception {
        // Test lines 106-107: increment=true, causeDesc null
        APIGatewayProxyRequestEvent event = new APIGatewayProxyRequestEvent();
        Map<String, String> claims = Map.of("sub", "11111111-2222-3333-4444-555555555555");
        Map<String, Object> authorizer = new HashMap<>();
        authorizer.put("claims", claims);

        APIGatewayProxyRequestEvent.ProxyRequestContext rc = new APIGatewayProxyRequestEvent.ProxyRequestContext();
        rc.setAuthorizer(authorizer);
        event.setRequestContext(rc);
        event.setPathParameters(Map.of("cause_id", "c123", "company_id", "co456"));

        IncrementForm form = new IncrementForm("Some Company", null, true);
        event.setBody(objectMapper.writeValueAsString(form));

        APIGatewayProxyResponseEvent response = handler.handleRequest(event, context);

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Unexpected server error"));
        assertTrue(response.getBody().contains("companyName and causeDesc are required when incrementing"));
    }

    @Test
    public void testUnusedThreeParamResponseMethod() throws Exception {
        // Test lines 131-141: Unused response(int, String, String) method
        handler = new IncrementCauseCompanyStatsHandler(dynamoDb);

        // Use reflection to access the private response method with 3 parameters
        java.lang.reflect.Method responseMethod = IncrementCauseCompanyStatsHandler.class.getDeclaredMethod(
                "response", int.class, String.class, String.class);
        responseMethod.setAccessible(true);

        APIGatewayProxyResponseEvent response = (APIGatewayProxyResponseEvent) responseMethod.invoke(
                handler, 400, "Error message", "Development details");

        assertEquals(400, response.getStatusCode());
        assertTrue(response.getBody().contains("Error message"));
        assertTrue(response.getBody().contains("Development details"));
    }

    @Test
    public void testUnusedThreeParamResponseMethodException() throws Exception {
        // Test lines 131-141: Exception handling in response(int, String, String) method
        handler = new IncrementCauseCompanyStatsHandler(dynamoDb);

        // Use reflection to access the private response method
        java.lang.reflect.Method responseMethod = IncrementCauseCompanyStatsHandler.class.getDeclaredMethod(
                "response", int.class, String.class, String.class);
        responseMethod.setAccessible(true);

        // We need to cause an exception in the response method
        // Since it catches all exceptions, we need to test if it can handle them
        // The method creates ResponseMessage which should serialize fine, but let's verify it doesn't fail
        APIGatewayProxyResponseEvent response = (APIGatewayProxyResponseEvent) responseMethod.invoke(
                handler, 500, "Server error", "Stack trace details");

        assertEquals(500, response.getStatusCode());
        assertTrue(response.getBody().contains("Server error"));
    }

}
