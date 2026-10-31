package com.love.interviewforge.api;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.love.interviewforge.common.ApiResponse;
import com.love.interviewforge.service.PickService;

/**
 * 统一抽题入口：练习与模拟共用。
 */
@RestController
@RequestMapping("/api/pick")
public class PickController {

    private final PickService pickService;

    public PickController(PickService pickService) {
        this.pickService = pickService;
    }

    @PostMapping
    public ApiResponse<PickService.PickResult> pick(@RequestBody PickService.PickRequest request) {
        return ApiResponse.ok(pickService.pick(request));
    }
}
