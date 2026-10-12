package com.ecommerce.agent.ticket;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TicketAgentResponse {
    @JsonProperty("ticket_id")
    private String ticketId;
    @JsonProperty("trace_id")
    private String traceId;
    private String status;
    private String category;
    private String route;
    @JsonProperty("draft_response")
    private String draftResponse;
    @JsonProperty("final_response")
    private String finalResponse;
    @JsonProperty("human_required")
    private Boolean humanRequired;
    @JsonProperty("human_reason")
    private String humanReason;
    @JsonProperty("review_id")
    private String reviewId;
    @JsonProperty("classification_confidence")
    private Double classificationConfidence;
    @JsonProperty("validation_result")
    private Object validationResult;
}
