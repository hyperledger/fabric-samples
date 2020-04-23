package com.cgb.bcpinstall.biz;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cgb.bcpinstall.common.entity.*;
import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.response.HttpInstallResponse;
import com.cgb.bcpinstall.common.response.ResponseCode;
import com.cgb.bcpinstall.common.util.*;
import com.cgb.bcpinstall.config.ConfigFileGen;
import com.cgb.bcpinstall.config.FabricConfigGen;
import com.cgb.bcpinstall.config.configGenImpl.DockerConfigGenImpl;
import com.cgb.bcpinstall.db.CheckPointDb;
import com.cgb.bcpinstall.db.table.NodeDO;
import com.cgb.bcpinstall.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * @author zheng.li
 * @date 2020/2/3 10:54
 */
@Service
@Slf4j
public class OrdererExtendBiz {

    @Autowired
    private RolesBiz rolesBiz;

    @Autowired
    private HttpClientUtil httpClient;

    @Autowired
    private CheckPointDb checkPointDb;

    @Autowired
    private ModeService modeService;

    @Autowired
    private FileService fileService;

    @Autowired
    private UpdateService updateService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private RemoteService remoteService;

    @Autowired
    private FabricCliService fabricCliService;

    @Autowired
    private InstallService installService;

    @Autowired
    private InstallBiz installBiz;

    @Autowired
    private FabricConfigGen fabricConfigGen;

    @Autowired
    private ConfigFileGen configFileGen;

    @Autowired
    private DockerConfigGenImpl dockerConfigGen;

    public void ordererExtend(Map<String, String> newOrdererHostConfig, InitConfigEntity configEntity) {
        log.info("为新增 orderer 生成证书");
        // 生成证书
        try {
            fabricConfigGen.configTxGen(configEntity);
            fabricConfigGen.cryptoGen(configEntity);
            configFileGen.createExtendCerts();
        } catch (Exception e) {
            log.error("为新增的 orderer 节点生成证书异常", e);
            e.printStackTrace();
            return;
        }

        log.info("为新增 orderer 生成 docker 相关文件");
        // 生成 docker-compose-order-xxxx.yaml 和 start-order.sh 文件

        Map<String, String> filePathMap = new HashMap<>(16);
        // 按机器IP分组
        Map<String, List<String>> orderGroups = dockerConfigGen.groupHostByIp(newOrdererHostConfig);
        for (String orderServerIp : orderGroups.keySet()) {
            // 每个 IP 一个 docker-compose 文件

            try {
                String folderName = UUID.randomUUID().toString();
                folderName = StringUtils.deleteAny(folderName, "-");
                folderName = orderServerIp + "-new-" + folderName;
                String filePath = dockerConfigGen.createOrdererYamlFile(configEntity, orderServerIp, orderGroups.get(orderServerIp), "order-" + folderName);
                String parentPath = new File(filePath).getParent();
                filePathMap.put(orderServerIp, parentPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        log.info("将新生成的证书拷贝到主节点安装目录");
        fileService.masterCopyCryptoConfig();
        fileService.masterCopyConfigtxFile();

        // 启动一个 cli 容器
        log.info("主节点创建cli容器");
        if (!fabricCliService.createCliContainer(modeService.getInstallPath() + "cli", configEntity)) {
            log.error("创建cli容器失败");
            return;
        }

        log.info("修改所有其他 orderer 节点配置");
        // 修改所有其他 orderer 节点配置，并重启
        updateOldOrdererContainers(newOrdererHostConfig, configEntity);

        // 收集所有节点加入的通道
        log.info("获取所有节点加入的通道列表");
        Set<String> channelList = new HashSet<>();
        try {
            channelList.addAll(fabricCliService.getAllChannels(configEntity));
        } catch (IOException e) {
            log.error("获取节点加入的所有通道异常", e);
            e.printStackTrace();
        }


        log.info("修改网络配置");
        // 修改网络配置
        log.info("将新加入的 orderer(s) 加入系统通道");

        Map<String, String> oldOrdererConfig = updateService.getOldNodeConfigMap(configEntity.getOrdererHostConfig(), newOrdererHostConfig);

        for (String newOrdererHost : newOrdererHostConfig.keySet()) {
            log.info("扩容orderer-oldOrdererConfig=" + JSON.toJSONString(oldOrdererConfig));
            oldOrdererConfig.put(newOrdererHost, newOrdererHostConfig.get(newOrdererHost));
            // 先修改系统通道
            if (!updateService.updateNetworkConfig(configEntity.getNetwork() + "-sys-channel", configEntity, oldOrdererConfig)) {
                log.error(String.format("为系统通道 %s 更新网络配置失败", configEntity.getNetwork() + "-sys-channel"));
                return;
            }

            // 更新业务通道
            if (!CollectionUtils.isEmpty(channelList)) {
                for (String channelName : channelList) {
                    log.info(String.format("将新加入的 orderer(s) 加入 %s 通道", channelName));
                    if (!updateService.updateNetworkConfig(channelName, configEntity, oldOrdererConfig)) {
                        log.error(String.format("为通道 %s 更新网络配置失败", channelName));
                    }
                }
            }
        }


        log.info("获取最新创世块");
        if (!fabricCliService.fetchGenesisBlock(configEntity)) {
            log.error("获取创世块发生错误");
        }

        log.info("注册 orderer 节点角色");
        List<String> ports = new ArrayList<>();
        for (String ip : orderGroups.keySet()) {
            List<String> hostList = orderGroups.get(ip);
            for (String host : hostList) {
                int index = host.lastIndexOf(":");
                ports.add(host.substring(index + 1));
            }

            this.rolesBiz.addRole(RoleEnum.ORDER, "http://" + ip + ":8080", ip, ports);
        }

        for (String ip : filePathMap.keySet()) {
            String path = filePathMap.get(ip);

            String folderName = new File(path).getName();
            if (NetUtil.ipIsMine(ip)) {
                fileService.copyFiles(RoleEnum.ORDER, ip, folderName, modeService.getInstallPath(), folderName, configEntity, null);
                this.rolesBiz.setServerStatus(ip, InstallStatusEnum.DOWNLOADED);
            } else {
                log.info("为新增 orderer 打包安装包");

                String packFilePath = fileService.packExtendNodeFiles(ip, folderName, RoleEnum.ORDER, configEntity);
                // 发送到节点启动
                log.info("将生成的文件包发送到新增 orderer 节点");
                remoteService.pushSlaveInstallPackage(ip, packFilePath, configEntity);
            }
        }

        log.info("启动 orderer 节点");
        // 等待节点启动成功
        List<ServerEntity> serverList = this.rolesBiz.getRolesMap().get(RoleEnum.ORDER);
        for (String ip : filePathMap.keySet()) {
            log.info(String.format("发送安装命令到新增 orderer 节点 %s", ip));

            String path = filePathMap.get(ip);

            String folderName = new File(path).getName();

            // 如果主节点也是此角色，则先安装
            if (NetUtil.ipIsMine(ip)) {
                Map<String, String> hosts = environmentService.getRoleNeedSetHost(RoleEnum.ORDER, configEntity);
                if (installService.startRole(RoleEnum.ORDER, ports, hosts, folderName)) {
                    this.rolesBiz.setServerStatus(ip, InstallStatusEnum.SUCCESS);
                }
            } else {
                for (ServerEntity server : serverList) {
                    if (server.getHost().equalsIgnoreCase(ip)) {
                        // 发送安装指令给从节点
                        do {
                            HttpInstallResponse response = remoteService.sendInstallCommand(server, RoleEnum.ORDER, folderName, configEntity);
                            if (ResponseCode.SUCCESS.getCode().equals(response.getCode())) {
                                log.warn(String.format("发送安装指令给 %s 节点安装 orderer 成功", ip));
                                this.rolesBiz.setServerStatus(ip, InstallStatusEnum.INSTALLING);
                                break;
                            }

                            log.warn(String.format("发送安装指令给 %s 节点安装 orderer 失败，稍后重试...", ip));
                            try {
                                Thread.sleep(3000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } while (true);
                        break;
                    }
                }
            }
        }

        log.info("等待所有 orderer 启动成功");
        while (serverList.stream().anyMatch(s -> s.getStatus() != InstallStatusEnum.SUCCESS)) {
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        log.info("将新 orderer(s) 信息加入本地数据库");
        // 更新数据库
        for (String host : newOrdererHostConfig.keySet()) {
            String ip = newOrdererHostConfig.get(host);
            int index = ip.lastIndexOf(":");
            String port = ip.substring(index + 1);
            ip = ip.substring(0, index);

            NodeDO nodeDO = new NodeDO();
            nodeDO.setOrgMspId(configEntity.getOrgMSPID());
            nodeDO.setRole(RoleEnum.ORDER);
            nodeDO.setHostName(host);
            nodeDO.setIp(ip);
            nodeDO.setPort(Integer.parseInt(port));
            nodeDO.setStatus(InstallStatusEnum.SUCCESS);

            try {
                this.checkPointDb.addNodeRecord(nodeDO);
            } catch (SQLException e) {
                log.error(String.format("添加新 orderer 节点 %s 记录到数据库异常", host), e);
                e.printStackTrace();
            }
        }

    }


    /**
     * 更新原 orderer 节点
     *
     * @param newOrdererHostConfig
     * @param configEntity
     */
    private void updateOldOrdererContainers(Map<String, String> newOrdererHostConfig, InitConfigEntity configEntity) {
        Map<String, String> newHosts = new HashMap<>(16);
        for (String host : newOrdererHostConfig.keySet()) {
            String ipPort = newOrdererHostConfig.get(host);
            String ip = ipPort.substring(0, ipPort.lastIndexOf(":"));
            newHosts.put(host, ip);
        }

        Map<String, String> needSendIps = new HashMap<>(16);
        for (String newHost : newOrdererHostConfig.keySet()) {

            for (String curHost : configEntity.getOrdererHostConfig().keySet()) {
                String curIp = configEntity.getOrdererHostConfig().get(curHost);
                curIp = curIp.substring(0, curIp.lastIndexOf(":"));
                if (!curHost.equalsIgnoreCase(newHost)) {
                    needSendIps.put(curHost, curIp);
                }
            }
        }

        // 即使本机也可以发送此命令更新
        for (String oldHost : needSendIps.keySet()) {
            String ip = needSendIps.get(oldHost);
            // 发送更新 orderer 的命令
            UpdateCmd cmd = new UpdateCmd();
            cmd.setRole(RoleEnum.ORDER);
            cmd.setHosts(newHosts);
            cmd.setPeerHostConfig(newOrdererHostConfig);
            cmd.setCurrentHost(oldHost);
            //发送更新host脚本
            String updateOrdererHostPath = modeService.getInitDir() + "template" + File.separator + "updateOrdererHost.sh";
            if (NetUtil.ipIsMine(ip)) {
                String shDesPath = "/var/run/updateOrdererHost.sh";
                try {
                    FileUtils.copyFile(new File(updateOrdererHostPath), new File(shDesPath));
                    installBiz.updateOrderers(cmd);
                } catch (IOException e) {
                    log.error("复制updateOrdererHost.sh文件发生异常");
                    e.printStackTrace();
                }
            } else {
                int retryInit = 0;
                int retryTotal = 10;
                do {
                    if (retryTotal == retryInit) {
                        log.error("重试超过次数");
                        break;
                    }
                    String result = this.httpClient.sendFileAndJson("http://" + ip + ":8080/v1/install/update", updateOrdererHostPath, JSONObject.toJSONString(cmd));
                    if (!StringUtils.isEmpty(result)) {
                        HttpInstallResponse response = JSONObject.parseObject(result, HttpInstallResponse.class);
                        if (ResponseCode.SUCCESS.getCode().equalsIgnoreCase(response.getCode())) {
                            break;
                        }
                    }
                    log.error(String.format("发送更新 orderer 指令到节点 %s 返回错误, 稍后重试", ip));
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    retryInit++;
                } while (true);
            }
        }
    }


}
