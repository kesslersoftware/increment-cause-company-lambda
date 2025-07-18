package com.boycottpro.causecompanystats;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.boycottpro.causecompanystats.model.IncrementForm;
import com.boycottpro.models.ResponseMessage;
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
        try {
            Map<String, String> pathParams = event.getPathParameters();
            String causeId = pathParams != null ? pathParams.get("cause_id") : null;
            String companyId = pathParams != null ? pathParams.get("company_id") : null;

            if (causeId == null || companyId == null) {
                return response(400, "Missing cause_id or company_id", "Invalid path parameters.");
            }

            IncrementForm form = objectMapper.readValue(event.getBody(), IncrementForm.class);
            boolean updated = incrementOrCreateCauseCompanyStatsRecord(causeId, companyId, form.getCause_desc(),
                    form.getCompany_name(), form.isIncrement());
            String body = objectMapper.writeValueAsString(Map.of("recordUpdated", updated));
            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withHeaders(Map.of("Content-Type", "application/json"))
                    .withBody(body);

        } catch (Exception e) {
            e.printStackTrace();
            return response(500, "Internal error", e.getMessage());
        }
    }

    private boolean incrementOrCreateCauseCompanyStatsRecord(String causeId, String companyId, String causeDesc,
                                                             String companyName, boolean increment) {
        try {
            int adjustment = increment ? 1 : -1;

            Map<String, AttributeValue> key = Map.of(
                    "cause_id", AttributeValue.fromS(causeId),
                    "company_id", AttributeValue.fromS(companyId)
            );

            Map<String, AttributeValue> expressionAttributeValues = Map.of(
                    ":delta", AttributeValue.fromN(String.valueOf(adjustment)),
                    ":zero", AttributeValue.fromN("0"),
                    ":company_name", AttributeValue.fromS(companyName),
                    ":cause_desc", AttributeValue.fromS(causeDesc)
            );

            UpdateItemRequest request = UpdateItemRequest.builder()
                    .tableName("cause_company_stats")
                    .key(key)
                    .updateExpression(
                            "SET boycott_count = if_not_exists(boycott_count, :zero) + :delta, " +
                                    "company_name = if_not_exists(company_name, :company_name), " +
                                    "cause_desc = if_not_exists(cause_desc, :cause_desc)"
                    )
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
