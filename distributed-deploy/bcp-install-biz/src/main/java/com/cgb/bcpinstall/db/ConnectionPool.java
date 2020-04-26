/*
 *  Copyright CGB Corp All Rights Reserved.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.cgb.bcpinstall.db;

import com.cgb.bcpinstall.common.util.FileUtil;
import com.cgb.bcpinstall.db.table.NodeDO;
import com.cgb.bcpinstall.db.util.MapperUtil;
import com.cgb.bcpinstall.db.util.object.Table;
import org.h2.jdbcx.JdbcConnectionPool;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class ConnectionPool {
    private static ConnectionPool instance = null;
    private JdbcConnectionPool jdbcConnectionPool = null;

    private ConnectionPool() {
        boolean needInit = false;
        String dbFilePath = FileUtil.getUserDir() + "bcp-install";
        if (!new File(dbFilePath + ".mv.db").exists()) {
            needInit = true;
        }
        jdbcConnectionPool = JdbcConnectionPool.create("jdbc:h2:file:" + dbFilePath + ";database_to_upper=false;DB_CLOSE_ON_EXIT=FALSE", "sa", "");
        jdbcConnectionPool.setMaxConnections(50);

        if (needInit) {
            try {
                initDb();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void initDb() throws SQLException {
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = getConnection();
            stmt = conn.createStatement();

            Table table = MapperUtil.createMapper(NodeDO.class);
            String sql = MapperUtil.createTableSql(table);
            stmt.execute(sql);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            releaseConnection(conn, stmt, null);
        }
    }

    public static void releaseConnection(Connection conn, Statement stmt,
                                         ResultSet rs) throws SQLException {
        if (rs != null) {
            rs.close();
        }
        if (stmt != null) {
            stmt.close();
        }
        if (conn != null) {
            conn.close();
        }
    }

    public static ConnectionPool getInstance() {
        if (instance == null) {
            instance = new ConnectionPool();
        }
        return instance;
    }

    public Connection getConnection() throws SQLException {
        return jdbcConnectionPool.getConnection();
    }
}
