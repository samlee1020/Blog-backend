package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BizException;
import com.blog.common.ErrorCode;
import com.blog.domain.CoverConfig;
import com.blog.domain.ProfileConfig;
import com.blog.domain.SystemConfig;
import com.blog.dto.Requests;
import com.blog.dto.Requests.LinkItem;
import com.blog.mapper.CoverConfigMapper;
import com.blog.mapper.ProfileConfigMapper;
import com.blog.mapper.SystemConfigMapper;
import com.blog.vo.Views;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Service
public class SiteService {
    private final CoverConfigMapper coverConfigMapper;
    private final ProfileConfigMapper profileConfigMapper;
    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public SiteService(CoverConfigMapper coverConfigMapper, ProfileConfigMapper profileConfigMapper,
                       SystemConfigMapper systemConfigMapper, ObjectMapper objectMapper, StringRedisTemplate redisTemplate) {
        this.coverConfigMapper = coverConfigMapper;
        this.profileConfigMapper = profileConfigMapper;
        this.systemConfigMapper = systemConfigMapper;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public Views.CoverView cover() {
        String cached = redisTemplate.opsForValue().get("site:cover");
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, Views.CoverView.class);
            } catch (JsonProcessingException ignored) {
                redisTemplate.delete("site:cover");
            }
        }
        CoverConfig config = activeCover();
        Views.CoverView view = toCoverView(config);
        cache("site:cover", view);
        return view;
    }

    @Transactional
    public Views.CoverView updateCover(Requests.CoverRequest request) {
        CoverConfig config = activeCover();
        config.setTitle(request.title());
        config.setSubtitle(request.subtitle());
        config.setBackgroundImageUrl(request.backgroundImageUrl());
        config.setAvatarImageUrl(request.avatarImageUrl());
        config.setLinksJson(writeJson(request.links()));
        coverConfigMapper.updateById(config);
        redisTemplate.delete("site:cover");
        return toCoverView(config);
    }

    public Views.ProfileView profile() {
        String cached = redisTemplate.opsForValue().get("site:profile");
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, Views.ProfileView.class);
            } catch (JsonProcessingException ignored) {
                redisTemplate.delete("site:profile");
            }
        }
        ProfileConfig config = activeProfile();
        Views.ProfileView view = toProfileView(config);
        cache("site:profile", view);
        return view;
    }

    @Transactional
    public Views.ProfileView updateProfile(Requests.ProfileRequest request) {
        ProfileConfig config = activeProfile();
        config.setDisplayName(request.displayName());
        config.setBio(request.bio());
        config.setAvatarImageUrl(request.avatarImageUrl());
        config.setEmail(request.email());
        config.setLocation(request.location());
        config.setSocialLinksJson(writeJson(request.socialLinks()));
        config.setContentMarkdown(request.contentMarkdown());
        profileConfigMapper.updateById(config);
        redisTemplate.delete("site:profile");
        return toProfileView(config);
    }

    public List<Views.SystemConfigView> systemConfigs() {
        return systemConfigMapper.selectList(new LambdaQueryWrapper<SystemConfig>().orderByAsc(SystemConfig::getConfigKey))
                .stream().map(this::toSystemConfigView).toList();
    }

    @Transactional
    public Views.SystemConfigView updateSystemConfig(String configKey, Requests.SystemConfigRequest request) {
        SystemConfig config = systemConfigMapper.selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, configKey).last("limit 1"));
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "system config not found");
        }
        config.setConfigValue(request.configValue());
        config.setValueType(request.valueType());
        config.setDescription(request.description());
        systemConfigMapper.updateById(config);
        redisTemplate.delete("site:config");
        return toSystemConfigView(config);
    }

    private CoverConfig activeCover() {
        CoverConfig config = coverConfigMapper.selectOne(new LambdaQueryWrapper<CoverConfig>()
                .eq(CoverConfig::getIsActive, true).last("limit 1"));
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "cover config not found");
        }
        return config;
    }

    private ProfileConfig activeProfile() {
        ProfileConfig config = profileConfigMapper.selectOne(new LambdaQueryWrapper<ProfileConfig>()
                .eq(ProfileConfig::getIsActive, true).last("limit 1"));
        if (config == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "profile config not found");
        }
        return config;
    }

    private Views.CoverView toCoverView(CoverConfig config) {
        return new Views.CoverView(config.getTitle(), config.getSubtitle(), config.getBackgroundImageUrl(),
                config.getAvatarImageUrl(), readLinks(config.getLinksJson()));
    }

    private Views.ProfileView toProfileView(ProfileConfig config) {
        return new Views.ProfileView(config.getDisplayName(), config.getBio(), config.getAvatarImageUrl(), config.getEmail(),
                config.getLocation(), readLinks(config.getSocialLinksJson()), config.getContentMarkdown());
    }

    private Views.SystemConfigView toSystemConfigView(SystemConfig config) {
        return new Views.SystemConfigView(config.getConfigKey(), config.getConfigValue(), config.getValueType(),
                config.getDescription(), config.getUpdatedAt());
    }

    private List<LinkItem> readLinks(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyList() : value);
        } catch (JsonProcessingException e) {
            throw new BizException(ErrorCode.VALIDATION_ERROR, "invalid json value");
        }
    }

    private void cache(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), Duration.ofMinutes(30));
        } catch (JsonProcessingException ignored) {
        }
    }
}
