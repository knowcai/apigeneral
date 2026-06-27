package com.apigateway.controller.admin;

import com.apigateway.dto.AccessLogQuery;
import com.apigateway.dto.AccessLogView;
import com.apigateway.dto.ApiResponse;
import com.apigateway.service.AccessLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/admin/logs")
@RequiredArgsConstructor
public class AdminAccessLogController {

    private final AccessLogService accessLogService;

    @GetMapping
    public ApiResponse<Page<AccessLogView>> list(
            @RequestParam(required = false) String apiCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String consumerName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        AccessLogQuery query = buildQuery(apiCode, status, consumerName, from, to, page, size);
        return ApiResponse.ok(accessLogService.search(query));
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(
            @RequestParam(required = false) String apiCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String consumerName,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        AccessLogQuery query = buildQuery(apiCode, status, consumerName, from, to, 0, 20);
        String csv = accessLogService.exportCsv(query);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"access-logs.csv\"")
                .contentType(new MediaType("text", "csv"))
                .body(csv);
    }

    private static AccessLogQuery buildQuery(String apiCode, String status, String consumerName,
                                             LocalDateTime from, LocalDateTime to, int page, int size) {
        AccessLogQuery query = new AccessLogQuery();
        query.setApiCode(apiCode);
        query.setStatus(status);
        query.setConsumerName(consumerName);
        query.setFrom(from);
        query.setTo(to);
        query.setPage(page);
        query.setSize(size);
        return query;
    }
}
