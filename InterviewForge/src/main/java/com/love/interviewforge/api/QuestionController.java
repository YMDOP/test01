package com.love.interviewforge.api;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;
import com.love.interviewforge.service.QuestionService;

/**
 * 题库 CRUD。
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    public record BatchDeleteRequest(List<Long> ids) {
    }

    @GetMapping
    public ApiResponse<QuestionService.PageResult> list(
            @RequestParam(required = false) List<Long> categoryIds,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String mastery,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        return ApiResponse.ok(questionService.list(categoryIds, type, mastery, keyword, safePage, safeSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<QuestionService.QuestionView> get(@PathVariable Long id) {
        return ApiResponse.ok(questionService.get(id));
    }

    @PostMapping
    public ApiResponse<QuestionService.QuestionView> create(@RequestBody QuestionService.QuestionInput input) {
        return ApiResponse.ok(questionService.create(input));
    }

    @PutMapping("/{id}")
    public ApiResponse<QuestionService.QuestionView> update(@PathVariable Long id,
                                                            @RequestBody QuestionService.QuestionInput input) {
        return ApiResponse.ok(questionService.update(id, input));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        questionService.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/batch-delete")
    public ApiResponse<Integer> batchDelete(@RequestBody BatchDeleteRequest request) {
        return ApiResponse.ok(questionService.batchDelete(request == null ? List.of() : request.ids()));
    }
}
