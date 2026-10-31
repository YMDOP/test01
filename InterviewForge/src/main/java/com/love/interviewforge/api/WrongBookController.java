package com.love.interviewforge.api;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;
import com.love.interviewforge.service.WrongBookService;

/**
 * 错题本。默认只返回未掌握的题。
 */
@RestController
@RequestMapping("/api/wrongbook")
public class WrongBookController {

    private final WrongBookService wrongBookService;

    public WrongBookController(WrongBookService wrongBookService) {
        this.wrongBookService = wrongBookService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @RequestParam(defaultValue = "false") boolean includeResolved,
            @RequestParam(required = false) String type) {
        List<WrongBookService.WrongItem> items = wrongBookService.list(includeResolved, type);
        return ApiResponse.ok(Map.of(
                "items", items,
                "count", items.size(),
                "unresolved", wrongBookService.unresolvedCount()));
    }

    @PostMapping("/{id}/resolve")
    public ApiResponse<Void> resolve(@PathVariable Long id) {
        wrongBookService.resolve(id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        wrongBookService.delete(id);
        return ApiResponse.ok();
    }
}
