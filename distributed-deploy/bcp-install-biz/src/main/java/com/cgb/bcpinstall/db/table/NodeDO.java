package com.cgb.bcpinstall.db.table;

import com.cgb.bcpinstall.common.entity.InstallStatusEnum;
import com.cgb.bcpinstall.common.entity.RoleEnum;
import com.cgb.bcpinstall.db.util.annotation.ColumnAnnotation;
import com.cgb.bcpinstall.db.util.annotation.TableAnnotation;
import com.cgb.bcpinstall.db.util.object.BaseDO;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableAnnotation("tb_nodes")
public class NodeDO extends BaseDO {
    private static final long serialVersionUID = 6148451850282590664L;

    @ColumnAnnotation(columnName = "org_msp_id", isMaster = true, dbType = "VARCHAR", length = 128)
    private String orgMspId;

    @ColumnAnnotation(columnName = "role", isMaster = true, dbType = "VARCHAR", length = 64)
    private RoleEnum role;

    @ColumnAnnotation(columnName = "host_name", isMaster = true, dbType = "VARCHAR", length = 128)
    private String hostName;

    @ColumnAnnotation(columnName = "ip_address", isMaster = true, dbType = "VARCHAR", length = 64)
    private String ip;

    @ColumnAnnotation(columnName = "port", dbType = "INTEGER")
    private Integer port;

    @ColumnAnnotation(columnName = "status", dbType = "VARCHAR", length = 64)
    private InstallStatusEnum status;
}
