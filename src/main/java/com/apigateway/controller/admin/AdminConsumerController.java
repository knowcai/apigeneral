package com.apigateway.controller.admin;

import com.apigateway.dto.ApiResponse;
import com.apigateway.dto.ConsumerCreateResponse;
import com.apigateway.dto.ConsumerRequest;
import com.apigateway.dto.ConsumerResponse;
import com.apigateway.service.ConsumerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/consumers")
@RequiredArgsConstructor
public class AdminConsumerController {

    private final ConsumerService consumerService;

    @GetMapping
    public ApiResponse<List<ConsumerResponse>> list() {
        return ApiResponse.ok(consumerService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<ConsumerResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(consumerService.get(id));
    }

    @PostMapping
    public ApiResponse<ConsumerCreateResponse> create(@Valid @RequestBody ConsumerRequest req) {
        return ApiResponse.ok(consumerService.create(req));
    }

    @PutMapping("/{id}")
    public ApiResponse<ConsumerResponse> update(@PathVariable Long id, @Valid @RequestBody ConsumerRequest req) {
        return ApiResponse.ok(consumerService.update(id, req));
    }

    @PostMapping("/{id}/rotate-key")
    public ApiResponse<ConsumerCreateResponse> rotateKey(@PathVariable Long id) {
        return ApiResponse.ok(consumerService.rotateKey(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        consumerService.delete(id);
        return ApiResponse.ok(null);
    }
}
