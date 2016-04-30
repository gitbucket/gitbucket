package org.postgresql;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Wraps the PostgreSQL JDBC driver to convert the returning column names to lower case.
 */
public class Driver2 extends Driver {

    @Override
    public java.sql.Connection connect(String url, Properties info) throws SQLException {
        Connection conn =  super.connect(url, info);

        Object proxy = Proxy.newProxyInstance(
                conn.getClass().getClassLoader(),
                new Class[]{ Connection.class },
                new ConnectionProxyHandler(conn)
        );

        return Connection.class.cast(proxy);
    }


    private static class ConnectionProxyHandler implements InvocationHandler {

        private Connection conn;

        public ConnectionProxyHandler(Connection conn){
            this.conn = conn;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if(method.getName().equals("prepareStatement")){
                if(args != null && args.length == 2 && args[1].getClass().isArray()){
                    String[] keys = (String[]) args[1];
                    for(int i = 0; i < keys.length; i++){
                        keys[i] = keys[i].toLowerCase();
                    }
                }
            }
            return method.invoke(conn, args);
        }
    }

}

