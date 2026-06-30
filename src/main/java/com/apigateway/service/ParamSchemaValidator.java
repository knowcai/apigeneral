package com.apigateway.service;

import com.apigateway.exception.BusinessException;

import com.apigateway.util.SqlParamPatterns;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ParamSchemaValidator {

    private static final Pattern NAMED_PARAM = SqlParamPatterns.NAMED_PARAM;

    private ParamSchemaValidator() {
    }

    public static void validate(String sqlTemplate, Map<String, Object> paramSchema, Map<String, Object> params) {
        List<String> sqlParams = extractParamNames(sqlTemplate);
        Map<String, Object> safeParams = params != null ? params : Map.of();
        Map<String, Object> schema = paramSchema != null ? paramSchema : Map.of();

        for (String name : sqlParams) {
            Object spec = schema.get(name);
            boolean required = spec == null || readRequired(spec);
            if (required && !safeParams.containsKey(name)) {
                throw new BusinessException("缺少参数: " + name);
            }
            if (!safeParams.containsKey(name)) {
                continue;
            }
            String type = spec != null ? readType(spec) : "string";
            validateType(name, type, safeParams.get(name));
        }
    }

    private static List<String> extractParamNames(String sqlTemplate) {
        Matcher matcher = NAMED_PARAM.matcher(sqlTemplate);
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return List.copyOf(names);
    }

    private static boolean readRequired(Object spec) {
        if (spec instanceof Map<?, ?> map) {
            Object required = map.get("required");
            if (required instanceof Boolean b) {
                return b;
            }
        }
        return true;
    }

    private static String readType(Object spec) {
        if (spec instanceof Map<?, ?> map && map.get("type") != null) {
            return String.valueOf(map.get("type")).toLowerCase();
        }
        return "string";
    }

    private static void validateType(String name, String type, Object value) {
        switch (type) {
            case "integer", "int", "long" -> {
                if (!(value instanceof Number) && !isIntegerString(value)) {
                    throw new BusinessException("参数 " + name + " 应为整数");
                }
            }
            case "number", "double", "float", "decimal" -> {
                if (!(value instanceof Number) && !isNumberString(value)) {
                    throw new BusinessException("参数 " + name + " 应为数字");
                }
            }
            case "boolean", "bool" -> {
                if (!(value instanceof Boolean) && !isBooleanString(value)) {
                    throw new BusinessException("参数 " + name + " 应为布尔值");
                }
            }
            case "string", "text" -> {
                if (!(value instanceof String) && !(value instanceof Number) && !(value instanceof Boolean)) {
                    throw new BusinessException("参数 " + name + " 应为字符串");
                }
            }
            default -> {
                // 未知类型仅做非空校验
            }
        }
    }

    private static boolean isIntegerString(Object value) {
        if (value == null) {
            return false;
        }
        try {
            Long.parseLong(String.valueOf(value));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isNumberString(Object value) {
        if (value == null) {
            return false;
        }
        try {
            Double.parseDouble(String.valueOf(value));
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isBooleanString(Object value) {
        if (value instanceof Boolean) {
            return true;
        }
        String s = String.valueOf(value);
        return "true".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s);
    }
}
