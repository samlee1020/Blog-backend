package com.blog.service;

import com.blog.config.AppProperties;
import com.blog.domain.User;
import com.blog.domain.UserRole;
import com.blog.domain.UserStatus;
import com.blog.mapper.UserMapper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class StartupInitializer implements ApplicationRunner {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final AuthService authService;

    public StartupInitializer(UserMapper userMapper, PasswordEncoder passwordEncoder, AppProperties properties, AuthService authService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.authService = authService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (authService.findActiveByUsername(properties.adminUsername()) != null) {
            return;
        }
        User admin = new User();
        admin.setUsername(properties.adminUsername());
        admin.setNickname("Admin");
        admin.setPasswordHash(passwordEncoder.encode(properties.adminPassword()));
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);
        userMapper.insert(admin);
    }
}
