package com.boycottpro.causecompanystats;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.causecompanystats.model.IncrementForm;
import com.boycottpro.models.ResponseMessage;
import com.boycottpro.utilities.JwtUtility;
import com.boycottpro.utilities.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

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
        String sub = null;
        int lineNum = 37;
        try {
            sub = JwtUtility.getSubFromRestEvent(event);
            if (sub == null) {
            Logger.error(41, sub, "user is Unauthorized");
            return response(401, Map.of("message", "Unauthorized"));
            }
            lineNum = 44;
            Map<String, String> pathParams = event.getPathParameters();
            String causeId = pathParams != null ? pathParams.get("cause_id") : null;
            String companyId = pathParams != null ? pathParams.get("company_id") : null;
            if (causeId == null || companyId == null) {
                String errorMessage = "";
                if(causeId == null && companyId == null) {
                    errorMessage = "cause_id and company_id not present";
                } else {
                    if(causeId == null) {
                        errorMessage = "cause_id not present";
                    } else {
                        errorMessage = "company_id not present";
                    }
                }
                Logger.error(59, sub, errorMessage);
                ResponseMessage message = new ResponseMessage(400,
                        "Missing cause_id or company_id", "Invalid path parameters.");
                lineNum = 62;
                return response(400,message);
            }
            lineNum = 65;
            IncrementForm form = objectMapper.readValue(event.getBody(), IncrementForm.class);
            boolean updated = incrementOrCreateCauseCompanyStatsRecord(causeId, companyId, form.getCause_desc(),
                    form.getCompany_name(), form.isIncrement());
            lineNum = 69;
            return response(200,Map.of("recordUpdated", updated));

        } catch (Exception e) {
            Logger.error(lineNum, sub, e.getMessage());
            return response(500,Map.of("error", "Unexpected server error: " + e.getMessage()) );
        }
    }
    private APIGatewayProxyResponseEvent response(int status, Object body) {
        String responseBody = null;
        try {
            responseBody = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(status)
                .withHeaders(Map.of("Content-Type", "application/json"))
                .withBody(responseBody);
    }
    private boolean incrementOrCreateCauseCompanyStatsRecord(String causeId, String companyId, String causeDesc,
                                                             String companyName, boolean increment) {
        try {
            int adjustment = increment ? 1 : -1;

            Map<String, AttributeValue> key = Map.of(
                    "cause_id", AttributeValue.fromS(causeId),
                    "company_id", AttributeValue.fromS(companyId)
            );

            Map<String, AttributeValue> expressionAttributeValues = new HashMap<>();
            expressionAttributeValues.put(":delta", AttributeValue.fromN(String.valueOf(adjustment)));
            expressionAttributeValues.put(":zero", AttributeValue.fromN("0"));

            StringBuilder updateExpression = new StringBuilder("SET boycott_count = if_not_exists(boycott_count, :zero) + :delta");

            if (increment) {
                if (companyName == null || causeDesc == null) {
                    throw new IllegalArgumentException("companyName and causeDesc are required when incrementing.");
                }
                expressionAttributeValues.put(":company_name", AttributeValue.fromS(companyName));
                expressionAttributeValues.put(":cause_desc", AttributeValue.fromS(causeDesc));
                updateExpression.append(", company_name = if_not_exists(company_name, :company_name)");
                updateExpression.append(", cause_desc = if_not_exists(cause_desc, :cause_desc)");
            }

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName("cause_company_stats")
                    .key(key)
                    .updateExpression(updateExpression.toString())
                    .expressionAttributeValues(expressionAttributeValues)
                    .build();

            dynamoDb.updateItem(request);
            return true;

        } catch (DynamoDbException e) {
            e.printStackTrace();
            throw e;
        }
    }

    private APIGatewayProxyResponseEvent response(int status, String message, String devMsg) {
        try {
            String body = objectMapper.writeValueAsString(new ResponseMessage(status, message, devMsg));
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(status)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize error response", e);
        }
    }
}
