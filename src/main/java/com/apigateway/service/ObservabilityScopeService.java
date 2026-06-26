package com.apigateway.service;

import com.apigateway.entity.ApiDefinition;
import com.apigateway.entity.Datasource;
import com.apigateway.repository.ApiDefinitionRepository;
import com.apigateway.repository.DatasourceRepository;
import com.apigateway.security.AuthzService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ObservabilityScopeService {

    private final AuthzService authzService;
    private final ThemeService themeService;
    private final ApiDefinitionRepository apiDefinitionRepository;
    private final DatasourceRepository datasourceRepository;

    public record Scope(boolean global, Set<String> apiCodes, Set<Long> datasourceIds) {
        public boolean isEmpty() {
            return !global && apiCodes.isEmpty();
        }
    }

    public Scope currentScope() {
        authzService.requireAuthenticated();
        if (authzService.isSuperAdmin()) {
            return new Scope(true, Set.of(), Set.of());
        }
        List<Long> themeIds = themeService.accessibleThemeIds();
        if (themeIds.isEmpty()) {
            return new Scope(false, Set.of(), Set.of());
        }
        Set<String> apiCodes = apiDefinitionRepository.findByThemeIdIn(themeIds).stream()
                .map(ApiDefinition::getApiCode)
                .collect(Collectors.toSet());
        Set<Long> datasourceIds = datasourceRepository.findByThemeIdIn(themeIds).stream()
                .map(Datasource::getId)
                .collect(Collectors.toSet());
        return new Scope(false, apiCodes, datasourceIds);
    }
}
