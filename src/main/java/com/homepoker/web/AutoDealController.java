package com.homepoker.web;

import com.homepoker.table.AutoDealService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 자동 다음 핸드(오토딜) 토글.
 *  - GET /api/tables/{id}/autodeal : 현재 설정 → {enabled}
 *  - PUT /api/tables/{id}/autodeal : {"enabled": true|false}
 */
@RestController
public class AutoDealController {

    public record AutoDealRequest(boolean enabled) {}

    private final AutoDealService autoDealService;

    public AutoDealController(AutoDealService autoDealService) {
        this.autoDealService = autoDealService;
    }

    @GetMapping("/api/tables/{id}/autodeal")
    public Map<String, Boolean> get(@PathVariable String id) {
        return Map.of("enabled", autoDealService.isEnabled(id));
    }

    @PutMapping("/api/tables/{id}/autodeal")
    public Map<String, Boolean> set(@PathVariable String id, @RequestBody AutoDealRequest req) {
        autoDealService.setEnabled(id, req.enabled());
        return Map.of("enabled", autoDealService.isEnabled(id));
    }
}
