package com.cgb.bcpinstall.db;

import com.cgb.bcpinstall.db.table.NodeDO;
import com.cgb.bcpinstall.db.util.MapperUtil;
import com.cgb.bcpinstall.db.util.object.BaseDO;
import com.cgb.bcpinstall.db.util.object.Column;
import com.cgb.bcpinstall.db.util.object.Table;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.sql.Date;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class CheckPointDb {
    public boolean nodesTableEmpty() throws SQLException {
        return this.findDO(new NodeDO()).isEmpty();
    }

    public Long addNodeRecord(NodeDO obj) throws SQLException {
        synchronized (this) {
            return executeInsert(MapperUtil.createMapper(obj.getClass()), MapperUtil.createInsertSql(obj));
        }
    }

    public void deleteNodeRecord(NodeDO obj) throws SQLException {
        synchronized (this) {
            execute(MapperUtil.createMapper(obj.getClass()), MapperUtil.createDeleteByParamSql(obj));
        }
    }

    public List<NodeDO> find(NodeDO nodeDO) throws SQLException {
        synchronized (this) {
            return this.findDO(nodeDO);
        }
    }

    public boolean updateNode(NodeDO obj) throws SQLException {
        synchronized (this) {
            Table table = MapperUtil.createMapper(obj.getClass());

            NodeDO param = new NodeDO();
            param.setOrgMspId(obj.getOrgMspId());
            param.setHostName(obj.getHostName());
            param.setIp(obj.getIp());
            param.setPort(obj.getPort());
            List<NodeDO> found = findDO(param);
            if (found.isEmpty()) {
                return executeInsert(table, MapperUtil.createInsertSql(obj)) != null;
            } else {
                if (found.size() > 1) {
                    execute(table, MapperUtil.createDeleteByParamSql(param));
                    return executeInsert(table, MapperUtil.createInsertSql(obj)) != null;
                } else {
                    NodeDO item = found.get(0);
                    if (item.getStatus() == obj.getStatus()) {
                        return false;
                    }

                    obj.setId(found.get(0).getId());
                    return executeUpdate(table, MapperUtil.createUpdateByIdSql(obj)) == 1;
                }
            }
        }
    }

    private <T extends BaseDO> List<T> findDO(T obj) throws SQLException {
        return find(obj, null, 0, 0);
    }

    /**
     *
     * @param obj
     * @param orderBy
     * @param pageIndex 从1开始
     * @param pageSize
     * @param <T>
     * @return
     * @throws SQLException
     */
    private <T extends BaseDO> List<T> find(T obj, Map<String, Boolean> orderBy, int pageIndex, int pageSize) throws SQLException {
        List<T> result = new ArrayList<>();

        Class clazz = obj.getClass();
        Table table = MapperUtil.createMapper(clazz);
        checkAndCreateTable(table);

        String sql = MapperUtil.generalFindSql(obj);

        if (!CollectionUtils.isEmpty(orderBy)) {
            StringBuilder orderByStr = new StringBuilder();
            Set<String> orderFields = orderBy.keySet();
            for (String f: orderFields) {
                List<Column> cols = table.getFields().stream()
                        .filter(c -> c.getFieldName().equalsIgnoreCase(f) || c.getColumnName().equalsIgnoreCase(f))
                        .collect(Collectors.toList());
                if (!cols.isEmpty()) {
                    if (orderByStr.length() == 0) {
                        orderByStr.append(" order by ");
                    }
                    orderByStr.append(cols.get(0).getColumnName()).append(orderBy.get(f) ? " asc," : " desc,");
                }
            }
            String subSql = orderByStr.toString();
            if (subSql.endsWith(",")) {
                subSql = subSql.substring(0, subSql.length() - 1);
            }
            if (!subSql.isEmpty()) {
                sql = sql + subSql;
            }
        }

        if (pageIndex > 0 && pageSize > 0) {
            sql = sql + " limit " + (pageIndex - 1) * pageSize + ", " + pageSize;
        }

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);

            List<Column> columns = table.getFields();
            while (rs.next()) {
                T rtObj = (T)clazz.newInstance();
                for (Column col: columns) {
                    Object v = rs.getObject(col.getColumnName());
                    setFieldValue(rtObj, col, rs);
                }
                result.add(rtObj);
            }
        } catch (IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        } finally {
            ConnectionPool.releaseConnection(conn, stmt, rs);
        }

        return result;
    }

    private <T extends BaseDO> int countOf(T obj) throws SQLException {
        Class clazz = obj.getClass();
        Table table = MapperUtil.createMapper(clazz);
        checkAndCreateTable(table);

        String sql = MapperUtil.generalCountSql(obj);

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);
            if (rs.next()) {
                return rs.getInt("total");
            }
        } finally {
            ConnectionPool.releaseConnection(conn, stmt, rs);
        }

        return 0;
    }

    private static <T extends Enum<?>> T getEnumObject(String value, Class<T> clazz) {
        if (!clazz.isEnum()) {
            return null;
        }
        try {
            T[] enumConstants = clazz.getEnumConstants();
            for (T ec : enumConstants) {
                if (((Enum<?>) ec).name().equals(value)) {
                    return ec;
                }
            }
            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private <T extends BaseDO> void setFieldValue(T rtObj, Column col, ResultSet rs) {
        Field[] fields = rtObj.getClass().getDeclaredFields();

        boolean found = false;
        for (Field field: fields) {
            field.setAccessible(true);

            if (field.getName().equals(col.getFieldName())) {
                found = true;
                try {
                    Class type = field.getType();
                    if (type.isEnum()) {
                        field.set(rtObj, getEnumObject(col.getColumnName(), type));
                    } else if (type.equals(Long.class)) {
                        field.set(rtObj, rs.getLong(col.getColumnName()));
                    } else if (type.equals(String.class)) {
                        field.set(rtObj, rs.getString(col.getColumnName()));
                    } else if (type.equals(Date.class)) {
                        field.set(rtObj, rs.getDate(col.getColumnName()));
                    } else if (type.equals(Integer.class)) {
                        field.set(rtObj, rs.getInt(col.getColumnName()));
                    } else {
                        field.set(rtObj, rs.getObject(col.getColumnName()));
                    }
                } catch (IllegalAccessException | SQLException e) {
                    e.printStackTrace();
                }
            }
        }

        if (!found) {
            Class parent = rtObj.getClass().getSuperclass();
            Field[] fieldsParent = parent.getDeclaredFields();
            for (Field field: fieldsParent) {
                field.setAccessible(true);

                if (field.getName().equals(col.getFieldName())) {
                    try {
                        Class type = field.getType();
                        if (type.equals(Long.class)) {
                            field.set(rtObj, rs.getLong(col.getColumnName()));
                        } else if (type.equals(String.class)) {
                            field.set(rtObj, rs.getString(col.getColumnName()));
                        } else if (type.equals(java.util.Date.class)) {
                            field.set(rtObj, rs.getTimestamp(col.getColumnName()));
                        } else if (type.equals(Integer.class)) {
                            field.set(rtObj, rs.getInt(col.getColumnName()));
                        } else {
                            field.set(rtObj, rs.getObject(col.getColumnName()));
                        }
                    } catch (IllegalAccessException | SQLException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private boolean execute(Table table, String sql) throws SQLException {
        checkAndCreateTable(table);

        Connection conn = null;
        Statement stmt = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            stmt = conn.createStatement();
            return stmt.execute(sql);
        } finally {
            ConnectionPool.releaseConnection(conn, stmt, null);
        }
    }

    private Long executeInsert(Table table, String sql) throws SQLException {
        checkAndCreateTable(table);

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            stmt = conn.createStatement();
            stmt.execute(sql, Statement.RETURN_GENERATED_KEYS);
            rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong("id");
            }
            return 0L;
        } finally {
            ConnectionPool.releaseConnection(conn, stmt, rs);
        }
    }

    private int executeUpdate(Table table, String sql) throws SQLException {
        checkAndCreateTable(table);

        Connection conn = null;
        Statement stmt = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            stmt = conn.createStatement();
            return stmt.executeUpdate(sql);
        } finally {
            ConnectionPool.releaseConnection(conn, stmt, null);
        }
    }

    private void checkAndCreateTable(Table table) throws SQLException {
        Connection conn = ConnectionPool.getInstance().getConnection();
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet rsTables = meta.getTables(null, null, table.getTableName(), new String[] { "TABLE" });
        Statement stmt = null;
        if (!rsTables.next()) {
            stmt = conn.createStatement();
            stmt.execute(MapperUtil.createTableSql(table));
        }
        ConnectionPool.releaseConnection(conn, stmt, rsTables);
    }

    public <T extends BaseDO> Long executeInsertWithParametered(T obj, String sql) throws SQLException {
        Table table = MapperUtil.createMapper(obj.getClass());
        checkAndCreateTable(table);

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            for (int i = 0; i < table.getFields().size(); ++i) {
                Column c = table.getFields().get(i);

                if (c.getColumnName().equals("id")) {
                    continue;
                }

                if (c.getColumnName().equals("revision")) {
                    stmt.setLong(i, 0);
                } else if (c.getColumnName().equals("create_time")) {
                    stmt.setTimestamp(i, new Timestamp(System.currentTimeMillis()));
                } else if (c.getColumnName().equals("modify_time")) {
                    stmt.setTimestamp(i, new Timestamp(System.currentTimeMillis()));
                }  else {
                    try {
                        Object val = MapperUtil.getFieldValue(obj, c.getFieldName());
                        if ("CLOB".equalsIgnoreCase(c.getDbType())) {
                            ByteArrayInputStream byteInputStream = new ByteArrayInputStream(val == null ? "".getBytes() : val.toString().getBytes());
                            stmt.setAsciiStream(i, byteInputStream);
                        }
                        else {
                            if (String.class.getName().equals(c.getType())) {
                                stmt.setString(i, val.toString());
                            } else if (java.util.Date.class.getName().equals(c.getType())) {
                                if (val == null) {
                                    stmt.setTimestamp(i, new Timestamp(System.currentTimeMillis()));
                                } else {
                                    stmt.setTimestamp(i, new Timestamp(((java.util.Date)val).getTime()));
                                }
                            } else {
                                stmt.setString(i, val == null ? "" : val.toString());
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            stmt.executeUpdate();
            rs = stmt.getGeneratedKeys();
            if (rs.next()) {
                return rs.getLong("id");
            }
            return 0L;
        } finally {
            ConnectionPool.releaseConnection(conn, stmt, rs);
        }
    }

    public  <T extends BaseDO> List<Long> findOnlyIds(T obj, Map<String, Boolean> orderBy, int pageIndex, int pageSize) throws SQLException {
        List<Long> result = new ArrayList<>();

        Class clazz = obj.getClass();
        Table table = MapperUtil.createMapper(clazz);
        checkAndCreateTable(table);

        String sql = MapperUtil.generalFindIdSql(obj);

        if (!CollectionUtils.isEmpty(orderBy)) {
            StringBuilder orderByStr = new StringBuilder();
            Set<String> orderFields = orderBy.keySet();
            for (String f: orderFields) {
                List<Column> cols = table.getFields().stream()
                        .filter(c -> c.getFieldName().equalsIgnoreCase(f) || c.getColumnName().equalsIgnoreCase(f))
                        .collect(Collectors.toList());
                if (!cols.isEmpty()) {
                    if (orderByStr.length() == 0) {
                        orderByStr.append(" order by ");
                    }
                    orderByStr.append(cols.get(0).getColumnName()).append(orderBy.get(f) ? " asc," : " desc,");
                }
            }
            String subSql = orderByStr.toString();
            if (subSql.endsWith(",")) {
                subSql = subSql.substring(0, subSql.length() - 1);
            }
            if (!subSql.isEmpty()) {
                sql = sql + subSql;
            }
        }

        if (pageIndex > 0 && pageSize > 0) {
            sql = sql + " limit " + (pageIndex - 1) * pageSize + ", " + pageSize;
        }

        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = ConnectionPool.getInstance().getConnection();
            stmt = conn.createStatement();
            rs = stmt.executeQuery(sql);

            List<Column> columns = table.getFields();
            while (rs.next()) {
                Long id = rs.getLong("id");
                result.add(id);
            }
        } finally {
            ConnectionPool.releaseConnection(conn, stmt, rs);
        }

        return result;
    }
}
