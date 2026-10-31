package com.love.interviewforge.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.love.interviewforge.common.BusinessException;

/**
 * 用量统计：只读 ai_usage，一次请求最多 2 条 SQL。
 *
 * <p>费用按三段式从 token 重算，不依赖「总 token × 单价」。</p>
 */
@Service
public class UsageStatService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;
    private final PricingService pricingService;

    public UsageStatService(JdbcTemplate jdbcTemplate, PricingService pricingService) {
        this.jdbcTemplate = jdbcTemplate;
        this.pricingService = pricingService;
    }

    public record PurposeStat(long calls, long tokens, double cost) {
    }

    public record Summary(String range, String from, String to, long totalCalls, long inputTokens,
                          long outputTokens, long cacheReadTokens, long cacheWriteTokens, long totalTokens,
                          double cacheHitRate, double totalCost, double storedCost, String currency,
                          Map<String, PurposeStat> byPurpose) {
    }

    public record RecentItem(long id, String ts, String purpose, String model, long inputTokens,
                             long outputTokens, long cacheReadTokens, long totalTokens, double cost,
                             long latencyMs, String status) {
    }

    public record DailyPoint(String day, long calls, long tokens, double cost) {
    }

    @Transactional(readOnly = true)
    public Summary summary(String range) {
        String normalized = normalizeRange(range);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = switch (normalized) {
            case "TODAY" -> today;
            case "7D" -> today.minusDays(6);
            case "30D" -> today.minusDays(29);
            default -> null;
        };

        String from = start == null ? null : toSecond(start.atStartOfDay(ZONE).toInstant());
        String to = toSecond(today.plusDays(1).atStartOfDay(ZONE).toInstant());

        StringBuilder sql = new StringBuilder("""
                SELECT purpose, model,
                       COUNT(*) AS calls,
                       IFNULL(SUM(input_tokens), 0) AS input_tokens,
                       IFNULL(SUM(output_tokens), 0) AS output_tokens,
                       IFNULL(SUM(cache_read_tokens), 0) AS cache_read_tokens,
                       IFNULL(SUM(cache_write_tokens), 0) AS cache_write_tokens,
                       IFNULL(SUM(total_tokens), 0) AS total_tokens,
                       IFNULL(SUM(est_cost), 0) AS stored_cost
                  FROM ai_usage
                """);
        List<Object> params = new ArrayList<>();
        if (from != null) {
            sql.append(" WHERE substr(ts, 1, 19) >= ? AND substr(ts, 1, 19) < ?");
            params.add(from);
            params.add(to);
        }
        sql.append(" GROUP BY purpose, model");

        long totalCalls = 0;
        long inputTokens = 0;
        long outputTokens = 0;
        long cacheReadTokens = 0;
        long cacheWriteTokens = 0;
        long totalTokens = 0;
        double storedCost = 0;
        double totalCost = 0;
        String currency = "CNY";

        Map<String, long[]> callsByPurpose = new LinkedHashMap<>();
        Map<String, long[]> tokensByPurpose = new LinkedHashMap<>();
        Map<String, Double> costByPurpose = new LinkedHashMap<>();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        for (Map<String, Object> row : rows) {
            String purpose = String.valueOf(row.get("purpose"));
            String model = String.valueOf(row.get("model"));
            long calls = number(row.get("calls"));
            long input = number(row.get("input_tokens"));
            long output = number(row.get("output_tokens"));
            long cacheRead = number(row.get("cache_read_tokens"));
            long cacheWrite = number(row.get("cache_write_tokens"));
            long tokens = number(row.get("total_tokens"));
            double stored = ((Number) row.get("stored_cost")).doubleValue();

            PricingService.Price price = pricingService.priceFor(model);
            currency = price.currency();
            double cost = cost(input, cacheRead, cacheWrite, output, price);

            totalCalls += calls;
            inputTokens += input;
            outputTokens += output;
            cacheReadTokens += cacheRead;
            cacheWriteTokens += cacheWrite;
            totalTokens += tokens;
            storedCost += stored;
            totalCost += cost;

            merge(callsByPurpose, purpose, calls);
            merge(tokensByPurpose, purpose, tokens);
            costByPurpose.merge(purpose, cost, Double::sum);
        }

        Map<String, PurposeStat> byPurpose = new LinkedHashMap<>();
        for (String purpose : callsByPurpose.keySet()) {
            byPurpose.put(purpose, new PurposeStat(
                    callsByPurpose.get(purpose)[0],
                    tokensByPurpose.getOrDefault(purpose, new long[] {0})[0],
                    round6(costByPurpose.getOrDefault(purpose, 0d))));
        }

        long promptTotal = inputTokens + cacheReadTokens + cacheWriteTokens;
        double hitRate = promptTotal <= 0 ? 0 : (double) cacheReadTokens / promptTotal;

        return new Summary(normalized, from, to, totalCalls, inputTokens, outputTokens,
                cacheReadTokens, cacheWriteTokens, totalTokens, round4(hitRate),
                round6(totalCost), round6(storedCost), currency, byPurpose);
    }

    @Transactional(readOnly = true)
    public List<RecentItem> recent(int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 50);
        return jdbcTemplate.query("""
                SELECT id, ts, purpose, model, input_tokens, output_tokens, cache_read_tokens,
                       total_tokens, est_cost, latency_ms, status
                  FROM ai_usage
                 ORDER BY id DESC
                 LIMIT ?
                """, (rs, rowNum) -> new RecentItem(
                rs.getLong("id"),
                rs.getString("ts"),
                rs.getString("purpose"),
                rs.getString("model"),
                rs.getLong("input_tokens"),
                rs.getLong("output_tokens"),
                rs.getLong("cache_read_tokens"),
                rs.getLong("total_tokens"),
                round6(rs.getDouble("est_cost")),
                rs.getLong("latency_ms"),
                rs.getString("status")), safeLimit);
    }

    /** 近 N 天按本地日界聚合，缺失的日期补 0。 */
    @Transactional(readOnly = true)
    public List<DailyPoint> daily(int days) {
        int safeDays = Math.min(Math.max(1, days), 60);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = today.minusDays(safeDays - 1L);
        String from = toSecond(start.atStartOfDay(ZONE).toInstant());

        List<DailyPoint> rows = jdbcTemplate.query("""
                SELECT date(ts, '+8 hours') AS day,
                       COUNT(*) AS calls,
                       IFNULL(SUM(total_tokens), 0) AS tokens,
                       IFNULL(SUM(est_cost), 0) AS cost
                  FROM ai_usage
                 WHERE substr(ts, 1, 19) >= ?
                 GROUP BY day
                 ORDER BY day
                """, (rs, rowNum) -> new DailyPoint(
                rs.getString("day"),
                rs.getLong("calls"),
                rs.getLong("tokens"),
                round6(rs.getDouble("cost"))), from);

        Map<String, DailyPoint> byDay = new LinkedHashMap<>();
        for (DailyPoint point : rows) {
            byDay.put(point.day(), point);
        }
        List<DailyPoint> result = new ArrayList<>();
        for (int i = 0; i < safeDays; i++) {
            String day = start.plusDays(i).toString();
            result.add(byDay.getOrDefault(day, new DailyPoint(day, 0, 0, 0)));
        }
        return result;
    }

    private void merge(Map<String, long[]> target, String key, long value) {
        target.computeIfAbsent(key, ignored -> new long[] {0})[0] += value;
    }

    private double cost(long input, long cacheRead, long cacheWrite, long output,
                        PricingService.Price price) {
        return (input * price.inputMiss()
                + cacheRead * price.inputHit()
                + cacheWrite * price.cacheWrite()
                + output * price.output()) / 1_000_000d;
    }

    private String normalizeRange(String range) {
        if (range == null || range.isBlank()) {
            return "TODAY";
        }
        String upper = range.trim().toUpperCase();
        if (!List.of("TODAY", "7D", "30D", "ALL").contains(upper)) {
            throw new BusinessException(1002, "range 只能是 TODAY / 7D / 30D / ALL");
        }
        return upper;
    }

    private long number(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private String toSecond(Instant instant) {
        String text = instant.toString();
        return text.length() >= 19 ? text.substring(0, 19) : text;
    }

    private double round6(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).doubleValue();
    }

    private double round4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
