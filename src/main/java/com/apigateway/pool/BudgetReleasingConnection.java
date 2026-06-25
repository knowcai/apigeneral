package com.apigateway.pool;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;

public final class BudgetReleasingConnection {

    private BudgetReleasingConnection() {
    }

    public static Connection wrap(Connection delegate, Runnable onClose) {
        InvocationHandler handler = new InvocationHandler() {
            private boolean closed;

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if ("close".equals(method.getName())) {
                    if (!closed) {
                        closed = true;
                        try {
                            return method.invoke(delegate, args);
                        } finally {
                            onClose.run();
                        }
                    }
                    return null;
                }
                return method.invoke(delegate, args);
            }
        };
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                handler);
    }
}
