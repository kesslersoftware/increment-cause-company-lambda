package com.boycottpro.causecompanystats;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.models.ResponseMessage;

public class IncrementCauseCompanyStatsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String TABLE_NAME = "cause_company_stats";
    private final DynamoDbClient dynamoDb;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IncrementCauseCompanyStatsHandler() {
        this.dynamoDb = DynamoDbClient.create();
    }

    public IncrementCauseCompanyStatsHandler(DynamoDbClient dynamoDb) {
        this.dynamoDb = dynamoDb;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        try {
            Map<String, String> pathParams = event.getPathParameters();
            String companyId = (pathParams != null) ? pathParams.get("company_id") : null;
            String causeId = (pathParams != null) ? pathParams.get("cause_id") : null;
            String incrementStr = (pathParams != null) ? pathParams.get("increment") : null;

            if (companyId == null || causeId == null || incrementStr == null ||
                    companyId.isEmpty() || causeId.isEmpty() || incrementStr.isEmpty()) {
                return response(400, "Missing required path parameters", "Validation failed");
            }

            if (!(incrementStr.equals("true") || incrementStr.equals("false"))) {
                return response(400, "Invalid value for increment", "increment must be 'true' or 'false'");
            }

            boolean increment = Boolean.parseBoolean(incrementStr);
            boolean success = incrementOrCreateCauseCompany(companyId, causeId, increment);
            return response(200, objectMapper.writeValueAsString("update = " + success), null);
        } catch (Exception e) {
            e.printStackTrace();
            return response(500, "Server error", e.getMessage());
        }
    }

    private boolean incrementOrCreateCauseCompany(String companyId, String causeId, boolean increment) {
        String key = companyId + "#" + causeId;
        int adjustment = increment ? 1 : -1;

        Map<String, AttributeValue> itemKey = Map.of("company_cause_id", AttributeValue.fromS(key));

        try {
            Map<String, AttributeValue> values = new HashMap<>();
            values.put(":delta", AttributeValue.fromN(Integer.toString(adjustment)));
            values.put(":zero", AttributeValue.fromN("0"));

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(itemKey)
                    .updateExpression("SET boycott_count = if_not_exists(boycott_count, :zero) + :delta")
                    .expressionAttributeValues(values)
                    .build();

            dynamoDb.updateItem(request);
            return true;
        } catch (DynamoDbException e) {
            e.printStackTrace();
            throw e;
        }
    }

    private APIGatewayProxyResponseEvent response(int status, String body, String devMessage) {
        ResponseMessage msg = new ResponseMessage(status, body, devMessage);
        try {
            String responseBody = objectMapper.writeValueAsString(msg);
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(responseBody);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}