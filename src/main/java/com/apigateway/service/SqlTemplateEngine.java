package com.apigateway.service;

import com.apigateway.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.apigateway.util.SqlParamPatterns;

public final class SqlTemplateEngine {

    private static final Pattern NAMED_PARAM = SqlParamPatterns.NAMED_PARAM;

    private SqlTemplateEngine() {
    }

    public record ParsedSql(String sql, List<String> paramNames) {
    }

    public static ParsedSql parse(String template, Map<String, Object> params) {
        Matcher matcher = NAMED_PARAM.matcher(template);
        StringBuilder sql = new StringBuilder();
        List<String> names = new ArrayList<>();
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!params.containsKey(name)) {
                throw new BusinessException("缺少参数: " + name);
            }
            names.add(name);
            matcher.appendReplacement(sql, "?");
        }
        matcher.appendTail(sql);
        return new ParsedSql(sql.toString(), names);
    }

    public static List<Object> bindValues(ParsedSql parsed, Map<String, Object> params) {
        return parsed.paramNames().stream().map(params::get).toList();
    }
}
