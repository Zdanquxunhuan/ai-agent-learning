package com.yu.ai.rag.security;

/**
 * 当前已经认证的用户上下文。
 */
public record UserContext(String userId) {

    public UserContext {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException(
                    "userId must not be blank"
            );
        }
    }
}