package com.love.interviewforge.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;
import com.love.interviewforge.service.ExamService;

/**
 * 模拟面试：组卷 / 作答 / 交卷 / 报告。
 */
@RestController
@RequestMapping("/api/exams")
public class ExamController {

    private final ExamService examService;

    public ExamController(ExamService examService) {
        this.examService = examService;
    }

    @PostMapping
    public ApiResponse<ExamService.ExamView> create() {
        return ApiResponse.ok(examService.create());
    }

    @GetMapping("/{id}")
    public ApiResponse<ExamService.ExamView> get(@PathVariable Long id) {
        return ApiResponse.ok(examService.get(id));
    }

    @PostMapping("/{id}/answer")
    public ApiResponse<Void> answer(@PathVariable Long id, @RequestBody ExamService.AnswerRequest request) {
        examService.answer(id, request.questionId(), request.answer());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ExamService.Report> submit(@PathVariable Long id) {
        return ApiResponse.ok(examService.submit(id));
    }

    @GetMapping("/{id}/result")
    public ApiResponse<ExamService.Report> result(@PathVariable Long id) {
        return ApiResponse.ok(examService.result(id));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> history() {
        return ApiResponse.ok(examService.history());
    }
}
