package com.blog.domain;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ProfileConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String displayName;
    private String bio;
    private String avatarImageUrl;
    private String email;
    private String location;
    private String socialLinksJson;
    private String contentMarkdown;
    private Boolean isActive;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
