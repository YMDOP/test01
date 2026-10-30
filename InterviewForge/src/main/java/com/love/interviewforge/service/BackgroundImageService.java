package com.love.interviewforge.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.love.interviewforge.common.BusinessException;
import com.love.interviewforge.config.AppPaths;

/**
 * 背景图存储：上传即压缩，只保留一个文件，避免 data 目录膨胀。
 */
@Service
public class BackgroundImageService {

    private static final Logger log = LoggerFactory.getLogger(BackgroundImageService.class);

    /** 长边上限：4K 原图也压到这个尺寸，保证滚动不卡。 */
    private static final int MAX_EDGE = 2560;
    private static final float JPEG_QUALITY = 0.85f;
    private static final String SETTING_VERSION = "ui.background.version";
    private static final String SETTING_ENABLED = "ui.background.enabled";

    private final SettingService settingService;

    public BackgroundImageService(SettingService settingService) {
        this.settingService = settingService;
    }

    private Path target() {
        return AppPaths.sub("assets").resolve("background.jpg");
    }

    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(1002, "请选择一张图片");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new BusinessException(1002, "只支持图片文件（jpg / png / webp）");
        }

        BufferedImage source;
        try (InputStream in = file.getInputStream()) {
            source = ImageIO.read(in);
        } catch (IOException e) {
            throw new BusinessException(1002, "图片读取失败: " + e.getMessage(), e);
        }
        if (source == null) {
            throw new BusinessException(1002, "无法解析该图片格式");
        }

        BufferedImage scaled = scaleDown(source);
        byte[] jpeg = toJpeg(scaled);

        try {
            Files.createDirectories(target().getParent());
            Files.write(target(), jpeg);
        } catch (IOException e) {
            throw new BusinessException(5000, "背景图保存失败: " + e.getMessage(), e);
        }

        String version = String.valueOf(System.currentTimeMillis());
        settingService.put(SETTING_VERSION, version);
        settingService.put(SETTING_ENABLED, "true");
        log.info("背景图已更新: {}x{} -> {}KB", scaled.getWidth(), scaled.getHeight(), jpeg.length / 1024);
        return version;
    }

    public BackgroundImage load() {
        Path path = target();
        if (!Files.exists(path)) {
            return null;
        }
        try {
            return new BackgroundImage(Files.readAllBytes(path), settingService.get(SETTING_VERSION, "0"));
        } catch (IOException e) {
            throw new BusinessException(5000, "背景图读取失败: " + e.getMessage(), e);
        }
    }

    public void delete() {
        try {
            Files.deleteIfExists(target());
        } catch (IOException e) {
            throw new BusinessException(5000, "背景图删除失败: " + e.getMessage(), e);
        }
        settingService.put(SETTING_ENABLED, "false");
        settingService.put(SETTING_VERSION, "0");
    }

    private BufferedImage scaleDown(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= MAX_EDGE) {
            return source;
        }
        double ratio = (double) MAX_EDGE / longest;
        int targetWidth = Math.max(1, (int) Math.round(width * ratio));
        int targetHeight = Math.max(1, (int) Math.round(height * ratio));

        BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = scaled.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return scaled;
    }

    private byte[] toJpeg(BufferedImage image) {
        BufferedImage rgb = image;
        if (image.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = rgb.createGraphics();
            try {
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new BusinessException(5000, "当前环境缺少 JPEG 编码器");
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(buffer)) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(rgb, null, null), param);
        } catch (IOException e) {
            throw new BusinessException(5000, "图片编码失败: " + e.getMessage(), e);
        } finally {
            writer.dispose();
        }
        return buffer.toByteArray();
    }

    /** 背景图字节 + 版本号（用作 ETag）。 */
    public record BackgroundImage(byte[] bytes, String version) {
    }
}
