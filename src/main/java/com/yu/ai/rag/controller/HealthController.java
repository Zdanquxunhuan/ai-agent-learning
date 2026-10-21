package com.yu.ai.rag.controller;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 应用健康检查接口
 */
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    /**
     * 检查应用是否正常提供服务
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
