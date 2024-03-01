package com.project.java.orm;

import java.util.UUID;
import java.lang.reflect.Field;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DatabaseManager {
    private static final String URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String USER = "postgres";
    private static final String PASSWORD = "tutnet55";

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public void createTable(Class<?> clazz) throws SQLException {
        String tableName = clazz.getAnnotation(Table.class).name();
        StringBuilder query = new StringBuilder("CREATE TABLE IF NOT EXISTS " + tableName + " (");

        for (Field field : clazz.getDeclaredFields()) {
            Colum columnAnnotation = field.getAnnotation(Colum.class);
            if (columnAnnotation != null) {
                query.append(columnAnnotation.name()).append(" ");
                query.append(mapFieldTypeToSqlType(field.getType())).append(", ");
            }
        }

        query.setLength(query.length() - 2);
        query.append(");");

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(query.toString());
        }
    }


    public <T> void save(T object) throws SQLException {
        try (Connection connection = getConnection()) {
            Class<?> clazz = object.getClass();
            String tableName = clazz.getAnnotation(Table.class).name();

            StringBuilder insertQuery = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
            StringBuilder valuesQuery = new StringBuilder("VALUES (");

            Field idField = null;
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    idField = field;
                    continue;
                }

                field.setAccessible(true);
                insertQuery.append(field.getAnnotation(Colum.class).name()).append(",");
                valuesQuery.append("?,");
            }

            insertQuery.setLength(insertQuery.length() - 1);
            valuesQuery.setLength(valuesQuery.length() - 1);
            insertQuery.append(") ").append(valuesQuery).append(");");

            try (PreparedStatement preparedStatement = connection.prepareStatement(insertQuery.toString())) {
                int index = 1;
                for (Field field : clazz.getDeclaredFields()) {
                    if (!field.isAnnotationPresent(Id.class)) {
                        field.setAccessible(true);
                        preparedStatement.setObject(index++, field.get(object));
                    }
                }

                preparedStatement.executeUpdate();

                if (idField != null) {
                    try (ResultSet generatedKeys = preparedStatement.getGeneratedKeys()) {
                        if (generatedKeys.next()) {
                            idField.setAccessible(true);
                            idField.set(object, generatedKeys.getObject(1));
                        }
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public <T> Optional<T> findById(Class<T> clazz, Object id) throws SQLException {
        createTableIfNotExists(clazz);
        try (Connection connection = getConnection()) {
            String tableName = clazz.getAnnotation(Table.class).name();

            Field idField = null;
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    idField = field;
                    break;
                }
            }

            if (idField == null) {
                throw new IllegalArgumentException("No @Id annotation found in class: " + clazz.getName());
            }

            Colum columnAnnotation = idField.getAnnotation(Colum.class);
            String idColumnName = columnAnnotation != null ? columnAnnotation.name() : idField.getName();

            if (!columnExists(connection, tableName, idColumnName)) {
                throw new IllegalArgumentException("Column with name " + idColumnName + " does not exist in table " + tableName);
            }

            StringBuilder selectQuery = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE ").append(idColumnName).append(" = ?;");

            try (PreparedStatement preparedStatement = connection.prepareStatement(selectQuery.toString())) {
                preparedStatement.setObject(1, id);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet.next()) {
                        return Optional.of(mapResultSetToObject(resultSet, clazz));
                    }
                } catch (InstantiationException | IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        return Optional.empty();
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        ResultSet resultSet = metaData.getColumns(null, null, tableName, columnName);
        return resultSet.next();
    }

    public <T> List<T> findAll(Class<T> clazz) throws SQLException {
        createTableIfNotExists(clazz);
        try (Connection connection = getConnection()) {
            String tableName = clazz.getAnnotation(Table.class).name();
            StringBuilder selectQuery = new StringBuilder("SELECT * FROM ").append(tableName).append(";");

            try (Statement statement = connection.createStatement()) {
                try (ResultSet resultSet = statement.executeQuery(selectQuery.toString())) {
                    List<T> objects = new ArrayList<>();

                    while (resultSet.next()) {
                        objects.add(mapResultSetToObject(resultSet, clazz));
                    }

                    return objects;
                }
            }
        } catch (IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }

    private String mapFieldTypeToSqlType(Class<?> fieldType) {
        if (fieldType.equals(String.class)) {
            return "VARCHAR(255)";
        } else if (fieldType.equals(Integer.class) || fieldType.equals(int.class)) {
            return "INTEGER";
        } else if (fieldType.equals(Double.class) || fieldType.equals(double.class)) {
            return "DOUBLE PRECISION";
        } else if (fieldType.equals(UUID.class)) {
            return "UUID";
        }

        throw new IllegalArgumentException("Unsupported field type: " + fieldType.getName());
    }

    private <T> T mapResultSetToObject(ResultSet resultSet, Class<T> clazz) throws SQLException, IllegalAccessException, InstantiationException {
        T object = clazz.newInstance();

        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Colum.class)) {
                field.setAccessible(true);
                field.set(object, resultSet.getObject(field.getAnnotation(Colum.class).name()));
            }
        }

        return object;
    }

    public void createTableIfNotExists(Class<?> clazz) throws SQLException {
        String tableName = clazz.getAnnotation(Table.class).name();


        if (!tableExists(tableName)) {
            createTable(clazz);
        } else {

            if (!columnExists(tableName, "id")) {
                addIdColumn(tableName);
            }
        }
    }
    private boolean tableExists(String tableName) throws SQLException {
        try (Connection connection = getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet resultSet = metaData.getTables(null, null, tableName, new String[]{"TABLE"});
            return resultSet.next();
        }
    }

    private boolean columnExists(String tableName, String columnName) throws SQLException {
        try (Connection connection = getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet resultSet = metaData.getColumns(null, null, tableName, columnName);
            return resultSet.next();
        }
    }

    private void addIdColumn(String tableName) throws SQLException {
        String sql = "ALTER TABLE " + tableName + " ADD COLUMN id SERIAL PRIMARY KEY;";

        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

}
