package com.cgb.bcpinstall.db.util;

import com.cgb.bcpinstall.db.util.annotation.ColumnAnnotation;
import com.cgb.bcpinstall.db.util.annotation.TableAnnotation;
import com.cgb.bcpinstall.db.util.object.BaseDO;
import com.cgb.bcpinstall.db.util.object.Column;
import com.cgb.bcpinstall.db.util.object.Table;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MapperUtil {

    public static final char UNDERLINE = '_';

    public static String camelToUnderline(String param) {
        if (param == null || "".equals(param.trim())) {
            return "";
        }
        int len = param.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = param.charAt(i);
            if (Character.isUpperCase(c)) {
                sb.append(UNDERLINE);
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String underlineToCamel(String param) {
        if (param == null || "".equals(param.trim())) {
            return "";
        }
        int len = param.length();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            char c = param.charAt(i);
            if (c == UNDERLINE) {
                if (++i < len) {
                    sb.append(Character.toUpperCase(param.charAt(i)));
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String underlineToCamel2(String param) {
        if (param == null || "".equals(param.trim())) {
            return "";
        }
        StringBuilder sb = new StringBuilder(param);
        Matcher mc = Pattern.compile("_").matcher(param);
        int i = 0;
        while (mc.find()) {
            int position = mc.end() - (i++);
            // String.valueOf(Character.toUpperCase(sb.charAt(position)));
            sb.replace(position - 1, position + 1, sb.substring(position, position + 1).toUpperCase());
        }
        return sb.toString();
    }

    /**
     * 生成sql
     *
     * @param table
     */
    public static String createTableSql(Table table) {
        StringBuilder sb = new StringBuilder("CREATE TABLE ");
        sb.append(table.getTableName()).append("(\n");
        for (Column c : table.getFields()) {
            if (c.getColumnName().equals("id")) {
                sb.append("\t" + c.getColumnName()).append(" ").append(c.getDbType()).append(" NOT NULL AUTO_INCREMENT,\n");
                continue;
            }
            sb.append("\t" + c.getColumnName()).append(" ").append(c.getDbType());
            if (c.getDbType().equals("VARCHAR") || c.getDbType().equals("BIGINT")) {
                sb.append("(").append(c.getLength()).append(") ");
            } else if (c.getDbType().equals("DECIMAL")) {
                sb.append("(15,4) ");
            } else if (c.getDbType().equals("INT")) {
                sb.append("(").append(c.getLength()).append(") ");
            } else if (c.getDbType().equals("TIMESTAMP")) {
                sb.append(" NULL ");
            } else {
                sb.append(" ");
            }
            sb.append(c.isNull() ? " " : "NOT NULL ");
            sb.append(c.isUnique() ? " UNIQUE,\n" : ",\n");
        }
        // 添加默认字段
        String s = sb.toString();
        s = s + "\tPRIMARY KEY(id)\n)AUTO_INCREMENT=1;\n";
        System.out.println(s);
        return s;
    }

    /**
     * 生成table
     *
     * @param clazz
     * @return
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Table createMapper(Class clazz) {
        Table table = new Table();
        TableAnnotation tableAnno = (TableAnnotation) clazz.getAnnotation(TableAnnotation.class);
        table.setClassName(clazz.getName());
        table.setTableName(tableAnno.value());
        Field[] fields = clazz.getDeclaredFields();
        ColumnAnnotation column = null;
        String fieldName = null;
        Class type = null;
        Class parent = clazz.getSuperclass();
        Field[] fieldsParent = parent.getDeclaredFields();
        List<Column> fieldList = new LinkedList<Column>();
        Column c = null;
        for (Field field : fieldsParent) {
            if (field.getName().equals("serialVersionUID")) {
                continue;
            }
            column = field.getAnnotation(ColumnAnnotation.class);
            fieldName = field.getName();
            type = field.getType();
            c = new Column();
            c.setColumnName(camelToUnderline(fieldName));
            c.setType(type.getName());
            c.setFieldName(fieldName);
            if (column == null) {
                c.setNull(true);
                c.setLength(32);
                if (Long.class.equals(field.getType())) {
                    c.setDbType("BIGINT");
                    c.setLength(18);
                } else if (String.class.equals(field.getType())) {
                    c.setDbType("VARCHAR");
                } else if (Integer.class.equals(field.getType())) {
                    c.setDbType("INTEGER");
                } else if (Date.class.equals(field.getType())) {
                    c.setDbType("TIMESTAMP");
                } else if (boolean.class.equals(field.getType())) {
                    c.setDbType("SMALLINT");
                    c.setLength(1);
                } else if (Boolean.class.equals(field.getType())) {
                    c.setDbType("SMALLINT");
                    c.setLength(1);
                } else {
                    c.setDbType("VARCHAR");
                    c.setLength(30);
                }
            } else {
                if (StringUtils.isNotBlank(column.columnName())) {
                    c.setColumnName(column.columnName());
                }
                if (StringUtils.isBlank(column.dbType())) {
                    if (Long.class.equals(field.getType())) {
                        c.setDbType("BIGINT");
                    } else if (String.class.equals(field.getType())) {
                        c.setDbType("VARCHAR");
                    } else if (Integer.class.equals(field.getType())) {
                        c.setDbType("INTEGER");
                    } else if (Date.class.equals(field.getType())) {
                        c.setDbType("TIMESTAMP");
                    } else if (boolean.class.equals(field.getType())) {
                        c.setDbType("SMALLINT");
                    } else if (Boolean.class.equals(field.getType())) {
                        c.setDbType("SMALLINT");
                    } else {
                        c.setDbType("VARCHAR");
                    }
                } else {
                    c.setDbType(column.dbType());
                }
                c.setLength(column.length());
                c.setNull(column.isNull());
                c.setUnique(column.isUnique());
                c.setMaster(column.isMaster());
                c.setLike(column.isLike());
            }
            fieldList.add(c);
        }

        List<Column> fieldListSub = new LinkedList<>();
        for (Field field : fields) {
            if (field.getName().equals("serialVersionUID")) {
                continue;
            }
            column = field.getAnnotation(ColumnAnnotation.class);
            fieldName = field.getName();
            type = field.getType();
            c = new Column();
            c.setColumnName(camelToUnderline(fieldName));
            c.setType(type.getName());
            c.setFieldName(fieldName);
            if (column == null) {
                c.setNull(true);
                c.setLength(32);
                if (Long.class.equals(field.getType())) {
                    c.setDbType("BIGINT");
                    c.setLength(18);
                } else if (String.class.equals(field.getType())) {
                    c.setDbType("VARCHAR");
                    c.setLength(32);
                } else if (Integer.class.equals(field.getType())) {
                    c.setDbType("INTEGER");
                    c.setLength(11);
                } else if (Date.class.equals(field.getType())) {
                    c.setDbType("TIMESTAMP");
                } else if (boolean.class.equals(field.getType())) {
                    c.setDbType("SMALLINT");
                    c.setLength(1);
                } else if (Boolean.class.equals(field.getType())) {
                    c.setDbType("SMALLINT");
                    c.setLength(1);
                } else {
                    c.setDbType("VARCHAR");
                    c.setLength(32);
                }
            } else {
                if (StringUtils.isNotBlank(column.columnName())) {
                    c.setColumnName(column.columnName());
                }
                if (StringUtils.isBlank(column.dbType())) {
                    if (Long.class.equals(field.getType())) {
                        c.setDbType("BIGINT");
                    } else if (String.class.equals(field.getType())) {
                        c.setDbType("VARCHAR");
                    } else if (Integer.class.equals(field.getType())) {
                        c.setDbType("INTEGER");
                    } else if (Date.class.equals(field.getType())) {
                        c.setDbType("TIMESTAMP");
                    } else if (boolean.class.equals(field.getType())) {
                        c.setDbType("SMALLINT");
                    } else if (Boolean.class.equals(field.getType())) {
                        c.setDbType("SMALLINT");
                    } else {
                        c.setDbType("VARCHAR");
                    }
                } else {
                    c.setDbType(column.dbType());
                }

                c.setLength(column.length());
                c.setNull(column.isNull());
                c.setUnique(column.isUnique());
                c.setMaster(column.isMaster());
                c.setLike(column.isLike());
            }
            fieldListSub.add(c);
        }
        fieldList.addAll(1, fieldListSub);
        table.setFields(fieldList);
        return table;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T> Object getFieldValue(T obj, String fieldName) throws IllegalAccessException, NoSuchFieldException {
        Class clazz = obj.getClass();
        Class parent = clazz.getSuperclass();
        Field[] fieldsParent = parent.getDeclaredFields();
        for (Field field : fieldsParent) {
            if (field.getName().equalsIgnoreCase(fieldName)) {
                field.setAccessible(true);
                return field.get(obj);
            }
        }
        Field[] fields = clazz.getDeclaredFields();
        for (Field field : fields) {
            if (field.getName().equalsIgnoreCase(fieldName)) {
                field.setAccessible(true);
                return field.get(obj);
            }
        }
        return null;
    }

    public static <T extends BaseDO> String createDeleteByIdSql(T obj) {
        Table table = createMapper(obj.getClass());
        return "delete from " + table.getTableName() + " where id=" + obj.getId();
    }

    public static <T extends BaseDO> String createDeleteByParamSql(T obj) {
        Table table = createMapper(obj.getClass());
        StringBuilder builder = new StringBuilder();

        boolean first = true;
        for (Column c : table.getFields()) {
            Object val = null;
            try {
                val = getFieldValue(obj, c.getFieldName());
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
            if (val == null) {
                continue;
            }

            if (first) {
                first = false;
                builder.append(" ");
            } else {
                builder.append(" and ");
            }

            builder.append(" ").append(c.getColumnName()).append("=");

            if (val.getClass().isEnum()) {
                builder.append("'").append(val == null ? "" : val.toString()).append("'");
            } else if (String.class.getName().equals(c.getType())) {
                builder.append("'").append(val.toString()).append("'");
            } else if (Date.class.getName().equals(c.getType())) {
                SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                builder.append("'").append(dateFormat.format((Date) val)).append("'");
            } else {
                builder.append(val.toString());
            }
        }

        String sql = "delete from " + table.getTableName();
        if (!builder.toString().isEmpty()) {
            sql = sql + " where " + builder.toString();
        }

        return sql;
    }

    public static <T extends BaseDO> String createInsertSql(T obj) {
        Table table = createMapper(obj.getClass());

        StringBuilder sql = new StringBuilder();
        sql.append("insert into ").append(table.getTableName()).append("\n \t").append("(");
        StringBuilder insertColumn = new StringBuilder();
        StringBuilder insertValues = new StringBuilder();
        for (Column c : table.getFields()) {
            if (c.getColumnName().equals("id")) {
                continue;
            }

            insertColumn.append(c.getColumnName()).append(",");
            if (c.getColumnName().equals("revision")) {
                insertValues.append("\t 0, ");
                continue;
            } else if (c.getColumnName().equals("create_time")) {
                insertValues.append("\t current_timestamp,");
            } else if (c.getColumnName().equals("modify_time")) {
                insertValues.append("\t current_timestamp,");
            } else {
                try {
                    Object val = getFieldValue(obj, c.getFieldName());

                    if (val.getClass().isEnum()) {
                        insertValues.append("\t '").append(val == null ? "" : val.toString()).append("',");
                    } else if (String.class.getName().equals(c.getType())) {
                        insertValues.append("\t '").append(val == null ? "" : val.toString()).append("',");
                    } else if (Date.class.getName().equals(c.getType())) {
                        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        insertValues.append("\t '").append(val == null ? "current_timestamp" : dateFormat.format((Date) val)).append("',");
                    } else {
                        insertValues.append("\t ").append(val == null ? "0" : val.toString()).append(",");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        String columns = insertColumn.toString();
        String values = insertValues.toString();
        sql.append(columns, 0, columns.length() - 1).append(") \n \t values \n \t (").append(values, 0, values.length() - 1).append(") ");
        return sql.toString();
    }

    public static <T extends BaseDO> String createUpdateByIdSql(T obj) {
        Table table = createMapper(obj.getClass());

        StringBuilder stringBuilder = new StringBuilder("update " + table.getTableName() + " set ");
        for (Column c : table.getFields()) {
            if (c.getColumnName().equals("id") || c.getColumnName().equals("revision")
                    || c.getColumnName().equals("create_time") || c.getColumnName().equals("modify_time")) {
                continue;
            }

            try {
                Object val = getFieldValue(obj, c.getFieldName());
                if (val == null) {
                    continue;
                }

                stringBuilder.append(c.getColumnName()).append("=");

                if (String.class.getName().equals(c.getType())) {
                    stringBuilder.append("'").append(val.toString()).append("',\t");
                } else if (Date.class.getName().equals(c.getType())) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    stringBuilder.append("'").append(dateFormat.format((Date) val)).append("',\t");
                } else {
                    stringBuilder.append(val.toString()).append(",\t");
                }

            } catch (Exception e) {
                e.printStackTrace();
                ;
            }
        }
        try {
            stringBuilder.append("revision=revision+1, modify_time=current_timestamp where id = ").append(Objects.requireNonNull(getFieldValue(obj, "id")).toString());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return stringBuilder.toString();
    }

    /**
     * 生成可根据主查询字段查询语句
     */
    public static <T extends BaseDO> String generalFindByMasterSql(T obj) {
        Table table = createMapper(obj.getClass());

        //获取可通过其查询的字段
        List<Column> masters = new ArrayList<>();
        for (Column col : table.getFields()) {
            if (col.isMaster()) {
                masters.add(col);
            }
        }
        if (masters.isEmpty()) {
            return null;
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("select * from ").append(table.getTableName()).append(" where ");
        // 针对每个master生成对应的语句element
        boolean first = true;
        for (Column master : masters) {
            if (first) {
                first = false;
            } else {
                stringBuilder.append(" and ");
            }

            try {
                Object val = getFieldValue(obj, master.getFieldName());
                if (val == null) {
                    continue;
                }
                stringBuilder.append(" ").append(master.getColumnName()).append("=");
                if (String.class.getName().equals(master.getType())) {
                    stringBuilder.append(" '").append(val.toString()).append("',");
                } else if (Date.class.getName().equals(master.getType())) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    stringBuilder.append(" '").append(dateFormat.format((Date) val)).append("',");
                } else {
                    stringBuilder.append(" ").append(val.toString()).append(",");
                }

            } catch (Exception e) {
                e.printStackTrace();
                ;
            }
        }

        String sql = stringBuilder.toString();
        return sql.substring(0, sql.length() - 1);
    }

    /**
     * 生成所有字段的查询sql
     *
     * @param obj
     * @param <T>
     * @return
     */
    public static <T extends BaseDO> String generalCountSql(T obj) {
        Table table = createMapper(obj.getClass());

        List<Column> columns = table.getFields();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("select count(*) as total from ").append(table.getTableName()).append(" where ");
        // 针对每个master生成对应的语句element
        boolean first = true;
        for (Column master : columns) {
            try {
                Object val = getFieldValue(obj, master.getFieldName());
                if (val == null) {
                    continue;
                }
                if (first) {
                    first = false;
                } else {
                    stringBuilder.append(" and ");
                }

                stringBuilder.append(" ").append(master.getColumnName()).append("=");
                if (String.class.getName().equals(master.getType())) {
                    stringBuilder.append(" '").append(val.toString()).append("'");
                } else if (Date.class.getName().equals(master.getType())) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    stringBuilder.append(" '").append(dateFormat.format((Date) val)).append("'");
                } else {
                    stringBuilder.append(" ").append(val.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
                ;
            }
        }

        return stringBuilder.toString();
    }

    /**
     * 生成所有字段的查询sql
     *
     * @param obj
     * @param <T>
     * @return
     */
    public static <T extends BaseDO> String generalFindSql(T obj) {
        Table table = createMapper(obj.getClass());

        List<Column> columns = table.getFields();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("select * from ").append(table.getTableName());
        // 针对每个master生成对应的语句element
        StringBuilder whereBuilder = new StringBuilder();
        boolean first = true;
        for (Column master : columns) {
            try {
                Object val = getFieldValue(obj, master.getFieldName());
                if (val == null) {
                    continue;
                }
                if (first) {
                    first = false;
                } else {
                    whereBuilder.append(" and ");
                }

                whereBuilder.append(" ").append(master.getColumnName()).append("=");
                if (val.getClass().isEnum()) {
                    whereBuilder.append(" '").append(val.toString()).append("'");
                } else if (String.class.getName().equals(master.getType())) {
                    whereBuilder.append(" '").append(val.toString()).append("'");
                } else if (Date.class.getName().equals(master.getType())) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    whereBuilder.append(" '").append(dateFormat.format((Date) val)).append("'");
                } else {
                    whereBuilder.append(" ").append(val.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
                ;
            }
        }

        if (!whereBuilder.toString().isEmpty()) {
            stringBuilder.append(" where ").append(whereBuilder.toString());
        }

        return stringBuilder.toString();
    }

    public static String createBatchInsertSql(Table table) {
        StringBuilder sql = new StringBuilder();
        sql.append("insert into ").append(table.getTableName()).append("\n \t").append("(");
        StringBuilder insertColumn = new StringBuilder();
        StringBuilder values = new StringBuilder();
        for (Column c : table.getFields()) {
            if (c.getColumnName().equals("id")) {
                continue;
            }
            insertColumn.append(c.getColumnName()).append(",");
            if (c.getColumnName().equals("revision")) {
                values.append("0,\n \t");
                continue;
            } else if (c.getColumnName().equals("create_time")) {
                values.append("\t current_timestamp,\n");
            } else if (c.getColumnName().equals("modify_time")) {
                values.append("\t currsssent_timestamp, \n");
            } else {
                values.append("\t \t #{").append("item." + c.getFieldName()).append(", ").append("jdbcType=").append(c.getDbType()).append(", javaType=").append(c.getType()).append("},\n");
            }
        }

        sql.append(insertColumn.toString().substring(0, insertColumn.toString().length() - 1)).append(") \n \t values  \n \t");
        return sql.toString();
    }

    public static <T extends BaseDO> String generalFindIdSql(T obj) {
        Table table = createMapper(obj.getClass());

        List<Column> columns = table.getFields();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("select id from ").append(table.getTableName()).append(" where ");
        // 针对每个master生成对应的语句element
        boolean first = true;
        for (Column master : columns) {
            try {
                Object val = getFieldValue(obj, master.getFieldName());
                if (val == null) {
                    continue;
                }
                if (first) {
                    first = false;
                } else {
                    stringBuilder.append(" and ");
                }

                stringBuilder.append(" ").append(master.getColumnName()).append("=");
                if (String.class.getName().equals(master.getType())) {
                    stringBuilder.append(" '").append(val.toString()).append("'");
                } else if (Date.class.getName().equals(master.getType())) {
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    stringBuilder.append(" '").append(dateFormat.format((Date) val)).append("'");
                } else {
                    stringBuilder.append(" ").append(val.toString());
                }
            } catch (Exception e) {
                e.printStackTrace();
                ;
            }
        }

        return stringBuilder.toString();
    }

    public static <T extends BaseDO> String createParameteredInsertSql(T obj) {
        Table table = createMapper(obj.getClass());

        StringBuilder sql = new StringBuilder();
        sql.append("insert into ").append(table.getTableName()).append("\n \t").append("(");
        StringBuilder insertColumn = new StringBuilder();
        StringBuilder insertValues = new StringBuilder();
        for (Column c : table.getFields()) {
            if (c.getColumnName().equals("id")) {
                continue;
            }

            insertColumn.append(c.getColumnName()).append(",");
            insertValues.append(" ?,");
        }
        String columns = insertColumn.toString();
        String values = insertValues.toString();
        sql.append(columns, 0, columns.length() - 1).append(") \n \t values \n \t (").append(values, 0, values.length() - 1).append(") ");
        return sql.toString();
    }
}