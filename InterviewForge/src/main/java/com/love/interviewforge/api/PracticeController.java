package com.love.interviewforge.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;
import com.love.interviewforge.service.PickService;
import com.love.interviewforge.service.PracticeService;

/**
 * 练习闭环。全程本地，不调用任何 AI。
 */
@RestController
@RequestMapping("/api/practice/sessions")
public class PracticeController {

    private final PracticeService practiceService;

    public PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    public record RateRequest(Long questionId, String rating) {
    }

    @PostMapping
    public ApiResponse<PracticeService.PracticeView> start(
            @RequestBody(required = false) PickService.PickRequest request) {
        PickService.PickRequest safe = request == null
                ? new PickService.PickRequest(null, null, null, 10, "RANDOM", null, true)
                : request;
        return ApiResponse.ok(practiceService.start(safe));
    }

    @PostMapping("/{id}/rate")
    public ApiResponse<PracticeService.RateResult> rate(@PathVariable Long id,
                                                        @RequestBody RateRequest request) {
        return ApiResponse.ok(practiceService.rate(id, request.questionId(), request.rating()));
    }

    @PostMapping("/{id}/finish")
    public ApiResponse<PracticeService.Summary> finish(@PathVariable Long id) {
        return ApiResponse.ok(practiceService.finish(id));
    }

    @GetMapping("/{id}")
    public ApiResponse<PracticeService.PracticeView> get(@PathVariable Long id) {
        return ApiResponse.ok(practiceService.get(id));
    }
}
