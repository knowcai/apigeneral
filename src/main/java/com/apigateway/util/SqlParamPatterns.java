package com.apigateway.util;

import java.util.regex.Pattern;

public final class SqlParamPatterns {

    public static final Pattern NAMED_PARAM = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    private SqlParamPatterns() {
    }
}
