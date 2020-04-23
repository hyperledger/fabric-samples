package com.cgb.bcpinstall.service;

import com.cgb.bcpinstall.biz.RolesBiz;
import com.cgb.bcpinstall.common.entity.RoleEnum;
import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.util.HttpClientUtil;
import com.cgb.bcpinstall.config.GlobalConfig;
import com.cgb.bcpinstall.db.CheckPointDb;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色服务类
 *
 * @author zheng.li
 * @date 2020/3/2 15:20
 */
@Service
@Slf4j
public class RoleService {

    @Autowired
    protected RolesBiz rolesBiz;

    @Autowired
    protected GlobalConfig globalConfig;


    /**
     * 根据服务器IP地址解析其所担任的角色，并添加到RoleBiz中
     *
     * @param remoteAddr     服务器IP地址
     * @param serverHttpPort 服务器HTTP服务的端口
     */
    public void addServerRole(String remoteAddr, String serverHttpPort, InitConfigEntity configEntity) {
        Map<RoleEnum, List<String>> roles = parseAllRoles(remoteAddr, configEntity);
        for (RoleEnum role : roles.keySet()) {
            List<String> ports = roles.get(role);

            this.rolesBiz.addRole(role, "http://" + remoteAddr + ":" + serverHttpPort, remoteAddr, ports);
        }
    }

    /**
     * 根据配置文件和远程从节点的IP地址获取其角色
     * 注意：一台服务器可以有多个角色
     *
     * @param slaveAddress
     * @return 返回 RoleEnum->端口列表
     */
    public Map<RoleEnum, List<String>> parseAllRoles(String slaveAddress, InitConfigEntity configEntity) {
        log.info(String.format("为服务器 %s 解析角色", slaveAddress));
        Map<RoleEnum, List<String>> roles = new HashMap<>(16);
        this.parseRole(roles, slaveAddress, configEntity.getOrdererHostConfig(), RoleEnum.ORDER, configEntity);
        this.parseRole(roles, slaveAddress, configEntity.getPeerHostConfig(), RoleEnum.PEER, configEntity);
        log.info(String.format("服务器 %s 解析后，所承担的角色有: ", slaveAddress, roles.keySet().stream().map(Enum::name).collect(Collectors.joining(","))));
        return roles;
    }

    private void parseRole(Map<RoleEnum, List<String>> roles, String slaveAddress, Map<String, String> hostConfig, RoleEnum role, InitConfigEntity configEntity) {
        for (String host : hostConfig.keySet()) {
            String ip = hostConfig.get(host);
            int index = ip.lastIndexOf(":");
            String port = ip.substring(index + 1);
            ip = ip.substring(0, index);

            if (slaveAddress.equals(ip)) {
                List<String> ports;
                if (roles.containsKey(role)) {
                    ports = roles.get(role);
                } else {
                    ports = new ArrayList<>();
                    roles.put(role, ports);
                }

                if (ports.stream().noneMatch(p -> p.equalsIgnoreCase(port))) {
                    ports.add(port);
                }
            }
        }
    }
}
