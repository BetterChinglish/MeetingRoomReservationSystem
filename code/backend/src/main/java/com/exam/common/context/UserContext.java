package com.exam.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class UserContext {
    private static final ThreadLocal<CurrentUser> CURRENT_USER = new ThreadLocal<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentUser {
        private Long userId;
        private String username;
        private String name;
        private String role; // "EMPLOYEE" or "ADMIN"
        private Long deptId;
        private String jti;

        public boolean isAdmin() {
            return "ADMIN".equalsIgnoreCase(role);
        }
    }

    public static void set(CurrentUser user) {
        CURRENT_USER.set(user);
    }

    public static CurrentUser get() {
        return CURRENT_USER.get();
    }

    public static Long getUserId() {
        CurrentUser user = CURRENT_USER.get();
        return user != null ? user.getUserId() : null;
    }

    public static boolean isCurrentUserAdmin() {
        CurrentUser user = CURRENT_USER.get();
        return user != null && user.isAdmin();
    }

    public static void clear() {
        CURRENT_USER.remove();
    }
}
