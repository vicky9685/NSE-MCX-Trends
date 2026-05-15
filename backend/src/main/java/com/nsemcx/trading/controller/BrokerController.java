package com.nsemcx.trading.controller;

import com.nsemcx.trading.config.BrokerProperties;
import com.nsemcx.trading.event.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST endpoints for broker connectivity.
 * Base path: /api/broker
 *
 * This controller manages simulated broker sessions (Zerodha, AliceBlue, Bonanza).
 * Actual order execution is not implemented in this version; the service layer
 * is designed so real broker API calls can be plugged in later.
 *
 * Session tokens are stored in-memory (not suitable for multi-node deployments
 * without an external store like Redis).
 */
@Slf4j
@RestController
@RequestMapping("/api/broker")
@RequiredArgsConstructor
public class BrokerController {

    private final BrokerProperties brokerProperties;

    /** In-memory session token store. Key = broker name, Value = session metadata. */
    private final Map<String, BrokerSession> sessions = new ConcurrentHashMap<>();

    // ── Request / Response records ─────────────────────────────────────────────

    public record ConnectRequest(String apiKey, String apiSecret, String userId) {}

    public record BrokerSession(
            String brokerName,
            String userId,
            boolean connected,
            Instant connectedAt,
            String sessionToken
    ) {}

    public record BrokerPosition(
            String symbol,
            String brokerName,
            String direction,       // LONG / SHORT
            int quantity,
            double averagePrice,
            double currentPrice,
            double unrealizedPnl,
            double realizedPnl
    ) {}

    public record BrokerOrder(
            String orderId,
            String brokerName,
            String symbol,
            String orderType,       // MARKET / LIMIT / SL / SL-M
            String direction,       // BUY / SELL
            int quantity,
            double price,
            String status,          // OPEN / COMPLETE / REJECTED / CANCELLED
            Instant placedAt
    ) {}

    // ── Connect endpoints ──────────────────────────────────────────────────────

    /**
     * POST /api/broker/zerodha/connect
     * Validates Zerodha API credentials and establishes a session.
     */
    @PostMapping("/zerodha/connect")
    public ResponseEntity<ApiResponse<BrokerSession>> connectZerodha(
            @RequestBody ConnectRequest req) {
        return connectBroker("ZERODHA", req, brokerProperties.getZerodha().isEnabled());
    }

    /**
     * POST /api/broker/aliceblue/connect
     * Validates AliceBlue API credentials and establishes a session.
     */
    @PostMapping("/aliceblue/connect")
    public ResponseEntity<ApiResponse<BrokerSession>> connectAliceBlue(
            @RequestBody ConnectRequest req) {
        return connectBroker("ALICEBLUE", req, brokerProperties.getAliceblue().isEnabled());
    }

    /**
     * POST /api/broker/bonanza/connect
     * Validates Bonanza API credentials and establishes a session.
     */
    @PostMapping("/bonanza/connect")
    public ResponseEntity<ApiResponse<BrokerSession>> connectBonanza(
            @RequestBody ConnectRequest req) {
        return connectBroker("BONANZA", req, brokerProperties.getBonanza().isEnabled());
    }

    // ── Status & data ──────────────────────────────────────────────────────────

    /**
     * GET /api/broker/status
     * Returns connection status for all configured brokers.
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getBrokerStatus() {
        Map<String, Object> status = new HashMap<>();

        status.put("ZERODHA", Map.of(
                "enabled", brokerProperties.getZerodha().isEnabled(),
                "connected", sessions.containsKey("ZERODHA"),
                "session", sessions.getOrDefault("ZERODHA", null)
        ));
        status.put("ALICEBLUE", Map.of(
                "enabled", brokerProperties.getAliceblue().isEnabled(),
                "connected", sessions.containsKey("ALICEBLUE"),
                "session", sessions.getOrDefault("ALICEBLUE", null)
        ));
        status.put("BONANZA", Map.of(
                "enabled", brokerProperties.getBonanza().isEnabled(),
                "connected", sessions.containsKey("BONANZA"),
                "session", sessions.getOrDefault("BONANZA", null)
        ));
        status.put("anyConnected", !sessions.isEmpty());
        status.put("timestamp", Instant.now());

        return ResponseEntity.ok(ApiResponse.ok(status, "Broker connection status"));
    }

    /**
     * GET /api/broker/positions
     * Returns live positions from all connected brokers.
     * Currently returns empty list — real implementations would call broker APIs.
     */
    @GetMapping("/positions")
    public ResponseEntity<ApiResponse<List<BrokerPosition>>> getLivePositions() {
        if (sessions.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(
                    "No broker connected. Please connect a broker first via /api/broker/{name}/connect"));
        }

        // In a real implementation, each connected broker's REST API would be called here
        // to fetch live positions. For now we return an empty list.
        List<BrokerPosition> positions = List.of();
        return ResponseEntity.ok(ApiResponse.ok(positions,
                "Live positions from " + sessions.size() + " connected broker(s). "
                + "Real broker API integration required for live data."));
    }

    /**
     * GET /api/broker/orders
     * Returns today's orders from all connected brokers.
     */
    @GetMapping("/orders")
    public ResponseEntity<ApiResponse<List<BrokerOrder>>> getTodaysOrders() {
        if (sessions.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error(
                    "No broker connected. Please connect a broker first via /api/broker/{name}/connect"));
        }

        // In a real implementation each broker's order API would be called.
        List<BrokerOrder> orders = List.of();
        return ResponseEntity.ok(ApiResponse.ok(orders,
                "Orders from " + sessions.size() + " connected broker(s). "
                + "Real broker API integration required for live data."));
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private ResponseEntity<ApiResponse<BrokerSession>> connectBroker(
            String brokerName, ConnectRequest req, boolean brokerEnabled) {

        if (!brokerEnabled) {
            return ResponseEntity.ok(ApiResponse.error(
                    brokerName + " broker is not enabled in configuration. "
                    + "Set brokers." + brokerName.toLowerCase() + ".enabled=true to enable it."));
        }

        if (req.apiKey() == null || req.apiKey().isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("API key is required"));
        }
        if (req.apiSecret() == null || req.apiSecret().isBlank()) {
            return ResponseEntity.ok(ApiResponse.error("API secret is required"));
        }

        // Simulate session creation — replace with real broker OAuth / token flow
        String sessionToken = "SIM-" + brokerName + "-" + System.currentTimeMillis();
        BrokerSession session = new BrokerSession(
                brokerName,
                req.userId() != null ? req.userId() : "unknown",
                true,
                Instant.now(),
                sessionToken
        );

        sessions.put(brokerName, session);
        log.info("Broker {} connected for user {}", brokerName, session.userId());

        return ResponseEntity.ok(ApiResponse.ok(session,
                brokerName + " connected successfully (simulated session)"));
    }
}
