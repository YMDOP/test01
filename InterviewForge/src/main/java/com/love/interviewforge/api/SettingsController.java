package com.love.interviewforge.api;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.love.interviewforge.common.ApiResponse;
import com.love.interviewforge.service.BackgroundImageService;
import com.love.interviewforge.service.SettingService;

/**
 * 配置与外观。P1 只用背景图相关键，其余键位由 P2 的 AI 配置复用。
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingService settingService;
    private final BackgroundImageService backgroundImageService;

    public SettingsController(SettingService settingService, BackgroundImageService backgroundImageService) {
        this.settingService = settingService;
        this.backgroundImageService = backgroundImageService;
    }

    @GetMapping
    public ApiResponse<Map<String, String>> get() {
        return ApiResponse.ok(settingService.getAll());
    }

    @PutMapping
    public ApiResponse<Map<String, String>> update(@RequestBody Map<String, String> values) {
        settingService.putAll(values);
        return ApiResponse.ok(settingService.getAll());
    }

    @PostMapping(value = "/background", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        String version = backgroundImageService.save(file);
        return ApiResponse.ok(Map.of("version", version, "enabled", "true"));
    }

    @GetMapping("/background")
    public ResponseEntity<byte[]> readBackground(@RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        BackgroundImageService.BackgroundImage image = backgroundImageService.load();
        if (image == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String etag = "\"" + image.version() + "\"";
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .eTag(etag)
                .cacheControl(CacheControl.noCache())
                .body(image.bytes());
    }

    @DeleteMapping("/background")
    public ApiResponse<Void> deleteBackground() {
        backgroundImageService.delete();
        return ApiResponse.ok();
    }
}
