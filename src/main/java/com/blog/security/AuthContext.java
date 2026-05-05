package com.blog.security;

import com.blog.common.BizException;
import com.blog.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class AuthContext {
    private AuthContext() {
    }

    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "unauthorized");
        }
        return loginUser;
    }

    public static Long currentUserId() {
        return currentUser().userId();
    }
}
