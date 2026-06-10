package com.travyn;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;

@RestController
public class TestController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/test-tokens")
    public List<Map<String, Object>> getTokens() {
        return jdbcTemplate.queryForList("SELECT token, is_active FROM sos_tokens ORDER BY created_at DESC LIMIT 10");
    }
}
