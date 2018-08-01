package jdbc;

import com.mchange.v2.c3p0.ComboPooledDataSource;

import java.beans.PropertyVetoException;
import java.sql.*;

public class DBManager {

    private static final ComboPooledDataSource cdps = init();
    private static final String URL = "jdbc:mysql://localhost/sample?user=dbuser&password=12dbuser";
    private static final String DRIVER = "com.mysql.jdbc.Driver";
    private Connection connection;
    private static int MINIMUM_POOL_SIZE = 40;
    private static int MAXIMUM_POOL_SIZE = 100;
    private static int INCREMENT_SIZE = 20;
    private static int MAX_STATEMENTS = 400;

    private static ComboPooledDataSource init() {
        try {
            ComboPooledDataSource cdps = new ComboPooledDataSource();
            cdps.setDriverClass(DRIVER);
            cdps.setJdbcUrl(URL);
            cdps.setMinPoolSize(MINIMUM_POOL_SIZE);
            cdps.setAcquireIncrement(INCREMENT_SIZE);
            cdps.setMaxPoolSize(MAXIMUM_POOL_SIZE);
            cdps.setMaxStatements(MAX_STATEMENTS);
            return cdps;
        } catch (PropertyVetoException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void getConnectionFromPool() throws ClassNotFoundException, SQLException, PropertyVetoException {
        connection = cdps.getConnection();
    }

    public Connection getCurrentConnection() throws ClassNotFoundException, SQLException, PropertyVetoException {
        return this.connection;
    }

    public PreparedStatement getPreparedStatement(String sql) throws SQLException, ClassNotFoundException, PropertyVetoException {
        if (connection == null) getConnectionFromPool();
        return connection.prepareStatement(sql);
    }

    public Statement getStatement() throws SQLException, ClassNotFoundException, PropertyVetoException {
        if (connection == null) getConnectionFromPool();
        return connection.createStatement();
    }

    public ResultSet getResultSet(PreparedStatement preparedStatement) throws SQLException {
        if (preparedStatement == null) return null;
        return preparedStatement.executeQuery();
    }

    public void shutDownConnection() {
        try {
            if (connection != null) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static void shutDownPool() {
        try {
            if (cdps != null) cdps.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
