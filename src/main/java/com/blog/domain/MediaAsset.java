package com.blog.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MediaAsset {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String originalFilename;
    private String storedFilename;
    private String contentType;
    private Long fileSize;
    private String storageType;
    private String storagePath;
    private String url;
    private MediaUsageType usageType;
    private Long uploadedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    private LocalDateTime deletedAt;
}
