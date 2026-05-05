package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BizException;
import com.blog.common.ErrorCode;
import com.blog.common.PageResponse;
import com.blog.config.AppProperties;
import com.blog.domain.MediaAsset;
import com.blog.domain.MediaUsageType;
import com.blog.domain.SystemConfig;
import com.blog.mapper.MediaAssetMapper;
import com.blog.mapper.SystemConfigMapper;
import com.blog.security.AuthContext;
import com.blog.vo.Views;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class MediaService {
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "image/gif", "gif"
    );

    private final MediaAssetMapper mediaAssetMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final AppProperties properties;

    public MediaService(MediaAssetMapper mediaAssetMapper, SystemConfigMapper systemConfigMapper, AppProperties properties) {
        this.mediaAssetMapper = mediaAssetMapper;
        this.systemConfigMapper = systemConfigMapper;
        this.properties = properties;
    }

    @Transactional
    public Views.MediaAssetView upload(MultipartFile file, MediaUsageType usageType) {
        validate(file);
        String contentType = file.getContentType();
        String extension = EXTENSIONS.get(contentType);
        String storedName = UUID.randomUUID() + "." + extension;
        LocalDate now = LocalDate.now();
        Path directory = Path.of(properties.uploadRoot(), usageType.path(), String.valueOf(now.getYear()),
                "%02d".formatted(now.getMonthValue()), "%02d".formatted(now.getDayOfMonth())).toAbsolutePath().normalize();
        Path target = directory.resolve(storedName).normalize();
        try {
            Files.createDirectories(directory);
            file.transferTo(target);
        } catch (IOException e) {
            throw new BizException(ErrorCode.UPLOAD_ERROR, "failed to store file");
        }
        MediaAsset asset = new MediaAsset();
        asset.setOriginalFilename(safeFilename(file.getOriginalFilename()));
        asset.setStoredFilename(storedName);
        asset.setContentType(contentType);
        asset.setFileSize(file.getSize());
        asset.setStorageType("LOCAL");
        asset.setStoragePath(target.toString());
        asset.setUrl("/uploads/" + usageType.path() + "/" + now.getYear() + "/" + "%02d".formatted(now.getMonthValue())
                + "/" + "%02d".formatted(now.getDayOfMonth()) + "/" + storedName);
        asset.setUsageType(usageType);
        asset.setUploadedBy(AuthContext.currentUserId());
        try {
            mediaAssetMapper.insert(asset);
        } catch (RuntimeException e) {
            try {
                Files.deleteIfExists(target);
            } catch (IOException ignored) {
            }
            throw e;
        }
        return toView(asset);
    }

    public PageResponse<Views.MediaAssetView> list(int page, int size, MediaUsageType usageType) {
        Page<MediaAsset> result = mediaAssetMapper.selectPage(new Page<>(page, size), new LambdaQueryWrapper<MediaAsset>()
                .eq(usageType != null, MediaAsset::getUsageType, usageType)
                .isNull(MediaAsset::getDeletedAt)
                .orderByDesc(MediaAsset::getCreatedAt));
        return PageResponse.of(result.getRecords().stream().map(this::toView).toList(), page, size, result.getTotal());
    }

    @Transactional
    public boolean delete(Long id) {
        MediaAsset asset = mediaAssetMapper.selectById(id);
        if (asset == null || asset.getDeletedAt() != null) {
            throw new BizException(ErrorCode.NOT_FOUND, "media asset not found");
        }
        asset.setDeletedAt(LocalDateTime.now());
        mediaAssetMapper.updateById(asset);
        return true;
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.UPLOAD_ERROR, "file is empty");
        }
        if (!EXTENSIONS.containsKey(file.getContentType())) {
            throw new BizException(ErrorCode.UPLOAD_ERROR, "unsupported image type");
        }
        Set<String> allowed = allowedTypes();
        if (!allowed.contains(file.getContentType())) {
            throw new BizException(ErrorCode.UPLOAD_ERROR, "image type is not allowed");
        }
        long maxBytes = maxFileSizeMb() * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.UPLOAD_ERROR, "file is too large");
        }
        String name = safeFilename(file.getOriginalFilename()).toLowerCase();
        String extension = EXTENSIONS.get(file.getContentType());
        if (!name.endsWith("." + extension) && !(file.getContentType().equals("image/jpeg") && name.endsWith(".jpeg"))) {
            throw new BizException(ErrorCode.UPLOAD_ERROR, "file extension does not match content type");
        }
    }

    private Set<String> allowedTypes() {
        SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "upload.allowedImageTypes").last("limit 1"));
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return EXTENSIONS.keySet();
        }
        return Set.of(config.getConfigValue().split(","));
    }

    private long maxFileSizeMb() {
        SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, "upload.maxFileSizeMb").last("limit 1"));
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return 10;
        }
        return Long.parseLong(config.getConfigValue());
    }

    private static String safeFilename(String original) {
        String name = original == null ? "upload" : Path.of(original).getFileName().toString();
        return name.replaceAll("[\\\\/\\r\\n]", "_");
    }

    private Views.MediaAssetView toView(MediaAsset asset) {
        return new Views.MediaAssetView(asset.getId(), asset.getOriginalFilename(), asset.getContentType(),
                asset.getFileSize(), asset.getUsageType(), asset.getUrl(), asset.getCreatedAt());
    }
}
