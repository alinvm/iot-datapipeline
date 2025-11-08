package org.example.iot.query.api;


import org.example.iot.query.service.QueryService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/query")
public class QueryController {

    private final QueryService service;

    public QueryController(QueryService service) {
        this.service = service;
    }

    @GetMapping("/{deviceId}/1m")
    public Map<String,String> oneMinute(@PathVariable UUID deviceId) {
        return service.latest1m(deviceId);
    }

    @GetMapping("/{deviceId}/1h")
    public Map<String, String> oneHour(@PathVariable UUID deviceId) {
        return service.latest1h(deviceId);
    }

    @GetMapping("/{deviceId}/all")
    public Map<String, String> allTime(@PathVariable UUID deviceId) {
        return service.allTime(deviceId);
    }
}
