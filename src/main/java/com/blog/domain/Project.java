package com.blog.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Project {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String slug;
    private String description;
    private String contentMarkdown;
    private String imageUrl;
    private String projectUrl;
    private String tagsJson;
    private Integer sortOrder;
    private ProjectStatus status;
    private Long createdBy;
    private Long updatedBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
