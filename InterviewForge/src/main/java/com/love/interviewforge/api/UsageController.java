package com.love.interviewforge.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;
import com.love.interviewforge.service.UsageStatService;

/**
 * 用量与费用统计。只读 ai_usage。
 */
@RestController
@RequestMapping("/api/usage")
public class UsageController {

    private final UsageStatService usageStatService;

    public UsageController(UsageStatService usageStatService) {
        this.usageStatService = usageStatService;
    }

    @GetMapping("/summary")
    public ApiResponse<UsageStatService.Summary> summary(
            @RequestParam(defaultValue = "TODAY") String range) {
        return ApiResponse.ok(usageStatService.summary(range));
    }

    @GetMapping("/recent")
    public ApiResponse<List<UsageStatService.RecentItem>> recent(
            @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(usageStatService.recent(limit));
    }

    @GetMapping("/daily")
    public ApiResponse<List<UsageStatService.DailyPoint>> daily(
            @RequestParam(defaultValue = "14") int days) {
        return ApiResponse.ok(usageStatService.daily(days));
    }
}
