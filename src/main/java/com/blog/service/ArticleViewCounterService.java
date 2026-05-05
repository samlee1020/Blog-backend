package com.blog.service;

import com.blog.mapper.ArticleMapper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ArticleViewCounterService {
    private final ArticleMapper articleMapper;

    public ArticleViewCounterService(ArticleMapper articleMapper) {
        this.articleMapper = articleMapper;
    }

    @Async
    public void increment(Long articleId) {
        articleMapper.incrementViewCount(articleId);
    }
}
