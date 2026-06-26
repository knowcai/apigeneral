package com.apigateway.service;

import com.apigateway.dto.ApprovalDiffResult;
import com.apigateway.entity.ApprovalAction;
import com.apigateway.entity.ApprovalResourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ApprovalDiffServiceTest {

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ApprovalDiffService approvalDiffService;

    @Test
    void parseErrorWhenPayloadInvalid() {
        ApprovalDiffResult result = approvalDiffService.buildDiffResult(
                ApprovalResourceType.API_VERSION, ApprovalAction.UPDATE, 1L, "{not-json");
        assertTrue(result.getDiff().isEmpty());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("无法解析变更对比"));
    }

    @Test
    void createActionReturnsEmptyWithoutError() {
        ApprovalDiffResult result = approvalDiffService.buildDiffResult(
                ApprovalResourceType.API_DEFINITION, ApprovalAction.CREATE, null, "{}");
        assertTrue(result.getDiff().isEmpty());
        assertNull(result.getError());
    }
}
