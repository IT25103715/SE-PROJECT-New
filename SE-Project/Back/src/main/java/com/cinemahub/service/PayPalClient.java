package com.cinemahub.service;

import com.cinemahub.config.PayPalConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Talks to PayPal's real Sandbox REST API (https://api-m.sandbox.paypal.com) with plain HTTP
 * calls - no extra SDK dependency. Only three calls are used:
 * <ul>
 *   <li>{@link #createOrder} - POST /v2/checkout/orders (intent CAPTURE)</li>
 *   <li>{@link #captureOrder} - POST /v2/checkout/orders/{id}/capture, after the buyer
 *       approved on PayPal's own Sandbox checkout screen</li>
 *   <li>{@link #refundCapture} - POST /v2/payments/captures/{id}/refund, only used if PayPal
 *       took the money but our booking could not be created (e.g. the seat was taken meanwhile)</li>
 * </ul>
 * Credentials and access tokens are never logged.
 */
@Service
public class PayPalClient {

    private static final Logger log = LoggerFactory.getLogger(PayPalClient.class);

    private final PayPalConfig config;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    private String cachedToken;
    private Instant cachedTokenExpiry = Instant.EPOCH;

    public PayPalClient(PayPalConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(30_000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** Result of a capture call - only {@link #isCompleted()} captures may confirm a booking. */
    public record CaptureResult(String orderStatus, String captureId, String captureStatus,
                                String currency, BigDecimal amount) {
        public boolean isCompleted() {
            return "COMPLETED".equals(orderStatus) && "COMPLETED".equals(captureStatus) && captureId != null;
        }
    }

    /** Thrown for any PayPal API failure; the message is safe to show to the customer. */
    public static class PayPalException extends RuntimeException {
        public PayPalException(String message) {
            super(message);
        }
    }

    /** Creates a Sandbox order for {@code amount} and returns PayPal's order ID. */
    public String createOrder(BigDecimal amount, String currency, String referenceId, String description) {
        Map<String, Object> body = Map.of(
                "intent", "CAPTURE",
                "purchase_units", List.of(Map.of(
                        "reference_id", referenceId,
                        "description", truncate(description, 127),
                        "amount", Map.of(
                                "currency_code", currency,
                                "value", amount.toPlainString()))),
                "application_context", Map.of(
                        "brand_name", "CinemaHub",
                        "shipping_preference", "NO_SHIPPING",
                        "user_action", "PAY_NOW"));
        JsonNode response = post("/v2/checkout/orders", body, "create-" + referenceId);
        String orderId = response.path("id").asText(null);
        if (orderId == null) {
            throw new PayPalException("PayPal did not return an order ID.");
        }
        log.info("PayPal Sandbox order {} created ({} {})", orderId, amount, currency);
        return orderId;
    }

    /** Captures an approved order. Never throws for a declined payment - check {@link CaptureResult#isCompleted()}. */
    public CaptureResult captureOrder(String orderId) {
        JsonNode response = post("/v2/checkout/orders/" + orderId + "/capture", Map.of(), "capture-" + orderId);
        JsonNode capture = response.path("purchase_units").path(0).path("payments").path("captures").path(0);
        BigDecimal value = capture.path("amount").hasNonNull("value")
                ? new BigDecimal(capture.path("amount").path("value").asText()) : null;
        CaptureResult result = new CaptureResult(
                response.path("status").asText(null),
                capture.path("id").asText(null),
                capture.path("status").asText(null),
                capture.path("amount").path("currency_code").asText(null),
                value);
        log.info("PayPal Sandbox order {} capture: order={}, capture={}", orderId, result.orderStatus(), result.captureStatus());
        return result;
    }

    /** Full refund of a capture (used only when our own booking step fails after PayPal took the money). */
    public void refundCapture(String captureId) {
        post("/v2/payments/captures/" + captureId + "/refund", Map.of(), "refund-" + captureId);
        log.info("PayPal Sandbox capture {} refunded", captureId);
    }

    private JsonNode post(String path, Object body, String requestId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken());
        // Idempotency key: retrying the same create/capture/refund never charges twice.
        headers.set("PayPal-Request-Id", requestId);
        headers.set("Prefer", "return=representation");
        try {
            ResponseEntity<String> response = restTemplate.exchange(config.getApiBaseUrl() + path, HttpMethod.POST,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
            return objectMapper.readTree(response.getBody() == null ? "{}" : response.getBody());
        } catch (HttpStatusCodeException ex) {
            throw new PayPalException(describeError(ex));
        } catch (RestClientException ex) {
            log.warn("PayPal Sandbox call {} failed: {}", path, ex.getClass().getSimpleName());
            throw new PayPalException("Could not reach PayPal. Please check your internet connection and try again.");
        } catch (java.io.IOException ex) {
            throw new PayPalException("Unexpected response from PayPal.");
        }
    }

    private synchronized String accessToken() {
        if (!config.isEnabled()) {
            throw new PayPalException("PayPal Sandbox is not configured on this server.");
        }
        if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiry)) {
            return cachedToken;
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(config.getClientId(), config.getClientSecret(), StandardCharsets.UTF_8);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        try {
            ResponseEntity<String> response = restTemplate.exchange(config.getApiBaseUrl() + "/v1/oauth2/token",
                    HttpMethod.POST, new HttpEntity<>(form, headers), String.class);
            JsonNode json = objectMapper.readTree(response.getBody());
            cachedToken = json.path("access_token").asText(null);
            long expiresIn = json.path("expires_in").asLong(300);
            cachedTokenExpiry = Instant.now().plusSeconds(Math.max(60, expiresIn - 60));
            if (cachedToken == null) {
                throw new PayPalException("PayPal did not return an access token.");
            }
            return cachedToken;
        } catch (HttpStatusCodeException ex) {
            log.warn("PayPal Sandbox authentication failed with HTTP {}", ex.getStatusCode().value());
            throw new PayPalException("PayPal rejected the Sandbox credentials - check paypal.client-id / paypal.client-secret.");
        } catch (RestClientException ex) {
            throw new PayPalException("Could not reach PayPal. Please check your internet connection and try again.");
        } catch (java.io.IOException ex) {
            throw new PayPalException("Unexpected response from PayPal.");
        }
    }

    /** Turns a PayPal error body into a short message (logs PayPal's debug id, never credentials). */
    private String describeError(HttpStatusCodeException ex) {
        String issue = null;
        String debugId = null;
        try {
            JsonNode json = objectMapper.readTree(ex.getResponseBodyAsString());
            issue = json.path("details").path(0).path("issue").asText(null);
            if (issue == null) {
                issue = json.path("name").asText(null);
            }
            debugId = json.path("debug_id").asText(null);
        } catch (Exception ignored) {
            // non-JSON error body - fall through to the generic message
        }
        log.warn("PayPal Sandbox API error: HTTP {} issue={} debug_id={}", ex.getStatusCode().value(), issue, debugId);
        if ("INSTRUMENT_DECLINED".equals(issue)) {
            return "PayPal declined this payment method. Please try again with a different one.";
        }
        if ("ORDER_NOT_APPROVED".equals(issue)) {
            return "The PayPal payment was not approved.";
        }
        if ("ORDER_ALREADY_CAPTURED".equals(issue)) {
            return "This PayPal payment was already processed.";
        }
        return "PayPal could not process the payment" + (issue != null ? " (" + issue + ")" : "") + ".";
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
