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
package com.cgb.bcpinstall.service;

import com.cgb.bcpinstall.biz.RolesBiz;
import com.cgb.bcpinstall.common.entity.InstallResult;
import com.cgb.bcpinstall.common.entity.InstallStatusEnum;
import com.cgb.bcpinstall.common.entity.RoleEnum;
import com.cgb.bcpinstall.common.entity.ServerEntity;
import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.response.HttpInstallResponse;
import com.cgb.bcpinstall.common.response.ResponseCode;
import com.cgb.bcpinstall.common.util.FileUtil;
import com.cgb.bcpinstall.common.util.NetUtil;
import com.cgb.bcpinstall.common.util.ProcessUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

/**
 * 安装服务类
 *
 * @author zheng.li
 * @date 2020/3/2 14:55
 */
@Service
@Slf4j
public class InstallService {

    @Autowired
    private ModeService modeService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private RemoteService remoteService;

    @Autowired
    protected RolesBiz rolesBiz;

    @Value("${init.config}")
    protected String initConfigFile;

    /**
     * 主节点调用，用来进行相应角色的安装，如果主节点担任相应角色，则启动本地安装，否则发送安装指令给从节点
     *
     * @param role
     */
    public void install(RoleEnum role, InitConfigEntity configEntity) {
        List<String> allMyIps = NetUtil.getLocalIPList();

        Map<RoleEnum, List<ServerEntity>> rolesMap = this.rolesBiz.getRolesMap();
        List<ServerEntity> serverList = rolesMap.get(role);

        boolean first = true;
        for (ServerEntity server : serverList) {
            if (server.getStatus() != InstallStatusEnum.SUCCESS) {
                // 如果主节点也是此角色，则先安装
                int initCount = 1;
                if (allMyIps.stream().anyMatch(ip -> ip.equals(server.getHost()))) {
                    Map<String, String> hosts = environmentService.getRoleNeedSetHost(role, configEntity);

                    if (startRole(role, server.getRolePorts(), hosts, null)) {
                        server.setStatus(InstallStatusEnum.SUCCESS);
                    }
                    InstallResult result = new InstallResult();
                    result.setRole(server.getRole());
                    result.setSuccess(true);
                    updateInstallResult(server.getHost(), result, configEntity);
                } else {
                    //设置重试次数初始化值
                    int retryCount = 1;
                    //设置重试次数
                    int retryTotal = 10;
                    // 发送安装指令给从节点
                    do {
                        HttpInstallResponse response = remoteService.sendInstallCommand(server, role, null, configEntity);
                        if (ResponseCode.SUCCESS.getCode().equals(response.getCode())) {
                            // log.warn(String.format("发送安装指令给 %s 节点安装 %s 成功", server.getHost(), role.name().toLowerCase()));
                            log.warn(String.format("Send installation instructions to %s node to install %s successfully", server.getHost(), role.name().toLowerCase()));
                            server.setStatus(InstallStatusEnum.INSTALLING);
                            break;
                        }
                        if (retryCount == retryTotal) {
                            break;
                        }
                        // log.warn(String.format("发送安装指令给 %s 节点安装 %s 失败，稍后重试...", server.getHost(), role.name().toLowerCase()));
                        log.warn(String.format("Failed to sending installation instructions to %s node  to install %s, try again later...", server.getHost(), role.name().toLowerCase()));
                        try {
                            Thread.sleep(3000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        retryCount++;
                    } while (true);
                }
            }
        }

        // 等待完成安装
        int checkCount = 1;
        int checkTotal = 10;
        while (serverList.stream().anyMatch(s -> s.getStatus() != InstallStatusEnum.SUCCESS)) {
            if (checkCount == checkTotal) {
                break;
            }
            // log.info(String.format("等待所有 %s 节点完成安装...", role.name().toLowerCase()));
            log.info(String.format("Wait for all %s nodes to complete the installation...", role.name().toLowerCase()));
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            checkCount++;
        }
    }

    public boolean startRole(RoleEnum role, List<String> rolePorts, Map<String, String> hosts, String roleFolderName) {
        return startRole(role, rolePorts, hosts, roleFolderName, null);
    }

    /**
     * 执行角色相应的脚本
     *
     * @param role
     */
    public boolean startRole(RoleEnum role, List<String> rolePorts, Map<String, String> hosts, String roleFolderName, String host) {
        // log.info(String.format("开始执行角色 %s 的脚本", role.name()));
        log.info(String.format("Start executing the script code of role %s", role.name()));
        if (!new File(modeService.getInstallPath()).exists()) {
            FileUtil.makeFilePath(modeService.getInstallPath(), false);
        }

        String[] env = null;
        String shellFilePath = null;
        String workingDir = null;
        switch (role) {
            case ORDER:
                workingDir = modeService.getInstallPath() + (StringUtils.isEmpty(roleFolderName) ? "order" : roleFolderName);
                shellFilePath = modeService.getInstallPath() + (StringUtils.isEmpty(roleFolderName) ? "order" : roleFolderName) + "/start-orderer.sh up";
                break;

            case PEER:
                workingDir = modeService.getInstallPath() + (StringUtils.isEmpty(roleFolderName) ? "peer" : roleFolderName);
                shellFilePath = modeService.getInstallPath() + (StringUtils.isEmpty(roleFolderName) ? "peer" : roleFolderName) + "/start-peer.sh up";
                break;
        }
        if (!StringUtils.isEmpty(shellFilePath)) {
            environmentService.envSet(rolePorts, hosts);

            try {
                ProcessUtil.Result res = ProcessUtil.execCmd("bash " + shellFilePath, env, workingDir);
                return res.getCode() == 0;
            } catch (Exception e) {
                // log.error(String.format("启动角色%s脚本异常", role.name()), e);
                log.error(String.format("An exception occurred while executing script code for role %s", role.name()), e);
                e.printStackTrace();
            }
        }

        return false;
    }

    /**
     * 更新从节点安装结果
     *
     * @param remoteAddress
     * @param result
     */
    public void updateInstallResult(String remoteAddress, InstallResult result, InitConfigEntity configEntity) {
        if (configEntity == null) {
            File configFile = new File(this.initConfigFile);
            Yaml yaml = new Yaml();
            try {
                configEntity = yaml.loadAs(new FileInputStream(configFile), InitConfigEntity.class);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        if (result.isSuccess()) {
            // log.info(String.format("节点 %s 已完成 %s 角色的安装", remoteAddress, result.getRole().name()));
            log.info(String.format("Node %s has intalled complete %s roles", remoteAddress, result.getRole().name()));

            // 加入数据库
            switch (result.getRole()) {
                case ORDER:
                    modeService.checkAndInsertDb(configEntity.getOrdererHostConfig(), remoteAddress, RoleEnum.ORDER, configEntity.getOrgMSPID());
                    break;
                case PEER:
                    modeService.checkAndInsertDb(configEntity.getPeerHostConfig(), remoteAddress, RoleEnum.PEER, configEntity.getOrgMSPID());
                    break;
            }
        }
        this.rolesBiz.updateInstallResult(remoteAddress, result);
    }
}
