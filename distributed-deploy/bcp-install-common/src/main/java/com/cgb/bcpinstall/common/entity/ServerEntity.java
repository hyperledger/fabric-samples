package com.cgb.bcpinstall.common.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
public class ServerEntity implements Serializable {
    private static final long serialVersionUID = 320994761941192716L;

    /**
     * 所担任的角色
     */
    private RoleEnum role;
    /**
     * 从节点安装服务的url，格式: http://ip:port
     */
    private String httpUrl;
    /**
     * 从节点的IP地址
     */
    private String host;
    /**
     * 这个port指的是机器锁充当角色的对应端口
     */
    private List<String> rolePorts;

    private InstallStatusEnum status;
}
