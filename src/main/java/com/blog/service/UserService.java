package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BizException;
import com.blog.common.ErrorCode;
import com.blog.common.PageResponse;
import com.blog.domain.User;
import com.blog.domain.UserRole;
import com.blog.domain.UserStatus;
import com.blog.dto.Requests;
import com.blog.mapper.UserMapper;
import com.blog.security.AuthContext;
import com.blog.vo.Views;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;

    public UserService(UserMapper userMapper, PasswordEncoder passwordEncoder, AuthService authService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
    }

    public PageResponse<Views.GuestView> guests(int page, int size, String username) {
        Page<User> result = userMapper.selectPage(new Page<>(page, size), baseGuestQuery()
                .like(StringUtils.hasText(username), User::getUsername, username)
                .orderByDesc(User::getCreatedAt));
        return PageResponse.of(result.getRecords().stream().map(this::toGuestView).toList(), page, size, result.getTotal());
    }

    @Transactional
    public boolean resetGuestPassword(Long id, Requests.GuestPasswordRequest request) {
        User user = getGuest(id);
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(user);
        authService.invalidateUserTokens(id);
        return true;
    }

    @Transactional
    public boolean updateGuestStatus(Long id, Requests.GuestStatusRequest request) {
        User user = getGuest(id);
        user.setStatus(request.status());
        userMapper.updateById(user);
        if (request.status() == UserStatus.DISABLED) {
            authService.invalidateUserTokens(id);
        }
        return true;
    }

    @Transactional
    public boolean changeAdminPassword(Requests.AdminPasswordRequest request) {
        User admin = userMapper.selectById(AuthContext.currentUserId());
        if (admin == null || admin.getRole() != UserRole.ADMIN || !passwordEncoder.matches(request.oldPassword(), admin.getPasswordHash())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "old password is incorrect");
        }
        admin.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userMapper.updateById(admin);
        authService.invalidateUserTokens(admin.getId());
        return true;
    }

    private User getGuest(Long id) {
        User user = userMapper.selectById(id);
        if (user == null || user.getDeletedAt() != null || user.getRole() != UserRole.GUEST) {
            throw new BizException(ErrorCode.NOT_FOUND, "guest not found");
        }
        return user;
    }

    private Views.GuestView toGuestView(User user) {
        return new Views.GuestView(user.getId(), user.getUsername(), user.getNickname(), user.getStatus(), user.getLastLoginAt(), user.getCreatedAt());
    }

    private LambdaQueryWrapper<User> baseGuestQuery() {
        return new LambdaQueryWrapper<User>().eq(User::getRole, UserRole.GUEST).isNull(User::getDeletedAt);
    }
}
