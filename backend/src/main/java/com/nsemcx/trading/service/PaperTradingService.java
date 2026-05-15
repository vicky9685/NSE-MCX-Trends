package com.nsemcx.trading.service;

import com.nsemcx.trading.config.TradingProperties;
import com.nsemcx.trading.event.PaperTradeEvent;
import com.nsemcx.trading.model.Instrument;
import com.nsemcx.trading.model.MarketData;
import com.nsemcx.trading.model.PaperTrade;
import com.nsemcx.trading.model.PaperTrade.PaperExitReason;
import com.nsemcx.trading.model.PaperTrade.PaperTradeDirection;
import com.nsemcx.trading.model.PaperTrade.PaperTradeStatus;
import com.nsemcx.trading.model.TradeSignal;
import com.nsemcx.trading.repository.MarketDataRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Simulated paper-trading engine.
 *
 * <p>Manages a paper portfolio including:
 * <ul>
 *   <li>Opening positions from {@link TradeSignal} entries</li>
 *   <li>Updating open positions against the latest market price</li>
 *   <li>Closing positions when target or stop-loss is hit</li>
 *   <li>Simulating commission (0.03 % of trade value by default)</li>
 * </ul>
 *
 * <p>All state is persisted in the {@code paper_trades} table.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaperTradingService {

    private static final String DEFAULT_SESSION = "default";
    private static final BigDecimal COMMISSION_RATE = BigDecimal.valueOf(0.0003); // 0.03%

    @PersistenceContext
    private EntityManager entityManager;

    private final TradingProperties tradingProperties;
    private final MarketDataRepository marketDataRepository;

    // ── Signal evaluation ─────────────────────────────────────────────────────

    /**
     * Evaluates whether a signal should be turned into a paper trade.
     *
     * <p>Constraints enforced:
     * <ul>
     *   <li>Paper trading must be enabled in config.</li>
     *   <li>Maximum number of open positions must not be exceeded.</li>
     *   <li>Signal confidence must be ≥ 60.</li>
     *   <li>Signal must be directional (BUY family or SELL family).</li>
     * </ul>
     */
    @Transactional
    public void evaluateSignalForExecution(TradeSignal signal) {
        TradingProperties.PaperTrading cfg = tradingProperties.paperTrading();
        if (!cfg.enabled()) {
            return;
        }

        // Confidence gate
        if (signal.getConfidence() == null || signal.getConfidence().doubleValue() < 60) {
            log.debug("Signal id={} below confidence threshold, skipping paper trade", signal.getId());
            return;
        }

        // Skip non-directional signals
        if (!signal.getSignalType().isBullish() && !signal.getSignalType().isBearish()) {
            log.debug("Signal id={} is neutral/options-only, skipping paper trade", signal.getId());
            return;
        }

        // Open-position cap
        long openCount = countOpenPositions(DEFAULT_SESSION);
        if (openCount >= cfg.maxPositions()) {
            log.info("Max paper positions ({}) reached, skipping signal id={}", cfg.maxPositions(), signal.getId());
            return;
        }

        openPosition(signal, DEFAULT_SESSION, cfg);
    }

    /**
     * Processes a {@link PaperTradeEvent} published by the Kafka consumer.
     * Logs the event; additional dispatch logic (partial fills, slippage) can be added here.
     */
    @Transactional
    public void executeTrade(PaperTradeEvent event) {
        log.info("Paper trade event: type={} symbol={} qty={} entry={}",
                event.eventType(), event.symbol(),
                event.quantity(), event.entryPrice());
        // No-op: the PaperTradeEvent is a read-only projection of already-persisted state.
        // Use openPosition() / closePosition() for actual mutations.
    }

    // ── Position management ───────────────────────────────────────────────────

    /**
     * Opens a new paper position based on the provided signal.
     */
    @Transactional
    public PaperTrade openPosition(TradeSignal signal, String sessionId,
                                   TradingProperties.PaperTrading cfg) {
        Instrument instrument = signal.getInstrument();
        BigDecimal entryPrice = signal.getEntryPrice();
        PaperTradeDirection direction = signal.getSignalType().isBullish()
                ? PaperTradeDirection.LONG : PaperTradeDirection.SHORT;

        // Determine quantity from max capital per trade
        int quantity = entryPrice.compareTo(BigDecimal.ZERO) > 0
                ? (int) Math.floor(cfg.maxCapitalPerTrade() / entryPrice.doubleValue())
                : 0;

        if (quantity <= 0) {
            log.warn("Cannot open paper trade for {}: quantity=0 (entry={}, maxCapital={})",
                    instrument.getSymbol(), entryPrice, cfg.maxCapitalPerTrade());
            return null;
        }

        BigDecimal commission = entryPrice.multiply(BigDecimal.valueOf(quantity))
                .multiply(COMMISSION_RATE)
                .setScale(4, RoundingMode.HALF_UP);

        PaperTrade trade = PaperTrade.builder()
                .sessionId(sessionId)
                .instrument(instrument)
                .signal(signal)
                .direction(direction)
                .quantity(quantity)
                .entryPrice(entryPrice)
                .commission(commission)
                .status(PaperTradeStatus.OPEN)
                .notes("Signal " + signal.getSignalType().name()
                        + " confidence=" + signal.getConfidence() + "%")
                .build();

        entityManager.persist(trade);
        log.info("Opened paper {} {} x {} @ {} (session={})",
                direction, quantity, instrument.getSymbol(), entryPrice, sessionId);
        return trade;
    }

    /**
     * Updates all open paper positions for {@code DEFAULT_SESSION} against the
     * latest market price.  Closes positions where target or stop-loss is hit.
     *
     * <p>Called by the scheduler every minute during market hours.
     */
    @Transactional
    public void updatePositions() {
        List<PaperTrade> openTrades = openPositions(DEFAULT_SESSION);
        log.debug("Updating {} open paper positions", openTrades.size());

        for (PaperTrade trade : openTrades) {
            try {
                updatePosition(trade);
            } catch (Exception ex) {
                log.error("Failed to update paper trade id={}: {}", trade.getId(), ex.getMessage(), ex);
            }
        }
    }

    /**
     * Evaluates and potentially closes a single open paper trade.
     */
    @Transactional
    public void updatePosition(PaperTrade trade) {
        MarketData latest = marketDataRepository
                .findLatestByInstrumentId(trade.getInstrument().getId())
                .orElse(null);

        if (latest == null) return;

        BigDecimal currentPrice = latest.getClose();
        TradeSignal signal = trade.getSignal();

        if (signal == null) {
            // No signal reference – update unrealised P&L only
            updatePnl(trade, currentPrice);
            return;
        }

        boolean isLong = trade.isLong();
        BigDecimal target = signal.getTarget1();
        BigDecimal stoploss = signal.getStoploss();

        // Check target hit
        if (target != null) {
            boolean targetHit = isLong
                    ? currentPrice.compareTo(target) >= 0
                    : currentPrice.compareTo(target) <= 0;
            if (targetHit) {
                closePosition(trade, currentPrice, PaperExitReason.TARGET_HIT);
                return;
            }
        }

        // Check stop-loss hit
        if (stoploss != null) {
            boolean slHit = isLong
                    ? currentPrice.compareTo(stoploss) <= 0
                    : currentPrice.compareTo(stoploss) >= 0;
            if (slHit) {
                closePosition(trade, currentPrice, PaperExitReason.SL_HIT);
                return;
            }
        }

        // Just update unrealised P&L
        updatePnl(trade, currentPrice);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /**
     * Returns all open paper trades for the given session.
     */
    @Transactional(readOnly = true)
    public List<PaperTrade> openPositions(String sessionId) {
        return entityManager
                .createQuery("SELECT p FROM PaperTrade p WHERE p.sessionId = :sid AND p.status = 'OPEN'",
                        PaperTrade.class)
                .setParameter("sid", sessionId)
                .getResultList();
    }

    /**
     * Returns the full trade history for the given session (open + closed).
     */
    @Transactional(readOnly = true)
    public List<PaperTrade> tradeHistory(String sessionId) {
        return entityManager
                .createQuery("SELECT p FROM PaperTrade p WHERE p.sessionId = :sid ORDER BY p.entryTime DESC",
                        PaperTrade.class)
                .setParameter("sid", sessionId)
                .getResultList();
    }

    /**
     * Returns a {@link PortfolioSummary} for the given session.
     */
    @Transactional(readOnly = true)
    public PortfolioSummary getSummary(String sessionId) {
        List<PaperTrade> all = tradeHistory(sessionId);
        double realised = all.stream()
                .filter(PaperTrade::isClosed)
                .mapToDouble(t -> t.getPnl().doubleValue())
                .sum();
        double unrealised = all.stream()
                .filter(PaperTrade::isOpen)
                .mapToDouble(t -> t.getPnl().doubleValue())
                .sum();
        long winners = all.stream().filter(t -> t.isClosed() && t.isWinner()).count();
        long losers  = all.stream().filter(t -> t.isClosed() && !t.isWinner()).count();
        double totalCommission = all.stream()
                .mapToDouble(t -> t.getCommission().doubleValue())
                .sum();

        double winRate = (winners + losers) > 0
                ? winners * 100.0 / (winners + losers) : 0;

        double initialCapital = tradingProperties.paperTrading().initialCapital();
        double equity = initialCapital + realised + unrealised;

        return new PortfolioSummary(sessionId, initialCapital, equity, realised,
                unrealised, (int)(winners + losers), (int) winners, winRate, totalCommission);
    }

    /** Portfolio-level statistics record. */
    public record PortfolioSummary(
            String sessionId,
            double initialCapital,
            double currentEquity,
            double realisedPnl,
            double unrealisedPnl,
            int totalTrades,
            int winningTrades,
            double winRatePct,
            double totalCommission
    ) {}

    // ── Private helpers ───────────────────────────────────────────────────────

    private void closePosition(PaperTrade trade, BigDecimal exitPrice, PaperExitReason reason) {
        updatePnl(trade, exitPrice);
        trade.setStatus(PaperTradeStatus.CLOSED);
        trade.setExitPrice(exitPrice);
        trade.setExitTime(OffsetDateTime.now());
        trade.setExitReason(reason);

        // Add exit commission
        BigDecimal exitCommission = exitPrice.multiply(BigDecimal.valueOf(trade.getQuantity()))
                .multiply(COMMISSION_RATE)
                .setScale(4, RoundingMode.HALF_UP);
        trade.setCommission(trade.getCommission().add(exitCommission));
        // Deduct commission from P&L
        trade.setPnl(trade.getPnl().subtract(exitCommission));

        log.info("Closed paper trade id={} {} @ {} reason={} pnl={}",
                trade.getId(), trade.getInstrument().getSymbol(),
                exitPrice, reason, trade.getPnl());
    }

    private void updatePnl(PaperTrade trade, BigDecimal currentPrice) {
        BigDecimal qty = BigDecimal.valueOf(trade.getQuantity());
        BigDecimal pnl = trade.isLong()
                ? currentPrice.subtract(trade.getEntryPrice()).multiply(qty)
                : trade.getEntryPrice().subtract(currentPrice).multiply(qty);
        trade.setPnl(pnl.setScale(4, RoundingMode.HALF_UP));

        BigDecimal invested = trade.getEntryPrice().multiply(qty);
        BigDecimal pnlPct = invested.compareTo(BigDecimal.ZERO) != 0
                ? pnl.divide(invested, 6, RoundingMode.HALF_UP)
                      .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
        trade.setPnlPercent(pnlPct.setScale(4, RoundingMode.HALF_UP));
    }

    private long countOpenPositions(String sessionId) {
        return entityManager
                .createQuery("SELECT COUNT(p) FROM PaperTrade p WHERE p.sessionId = :sid AND p.status = 'OPEN'",
                        Long.class)
                .setParameter("sid", sessionId)
                .getSingleResult();
    }
}
