package com.apigateway.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

/**
 * 开发库方案 A：删除并重建 vectordb，供 Flyway 从 V1 全量迁移。
 * 用法：mvn -q -DskipTests compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 *       java -cp "target/test-classes;target/cp.txt" com.apigateway.support.RebuildDevDatabase
 */
public class RebuildDevDatabase {

    private static final String HOST = "192.168.31.100";
    private static final int PORT = 5432;
    private static final String USER = "root";
    private static final String PASS = "root";
    private static final String DB = "vectordb";

    public static void main(String[] args) throws Exception {
        String adminUrl = "jdbc:postgresql://" + HOST + ":" + PORT + "/postgres";
        try (Connection conn = DriverManager.getConnection(adminUrl, USER, PASS);
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    SELECT pg_terminate_backend(pid)
                    FROM pg_stat_activity
                    WHERE datname = '%s' AND pid <> pg_backend_pid()
                    """.formatted(DB));
            stmt.execute("DROP DATABASE IF EXISTS " + DB);
            stmt.execute("CREATE DATABASE " + DB);
        }
        System.out.println("OK: database '" + DB + "' dropped and recreated on " + HOST);
    }
}
