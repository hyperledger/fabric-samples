package com.cgb.bcpinstall.biz;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cgb.bcpinstall.common.entity.RemoveCmd;
import com.cgb.bcpinstall.common.entity.RoleEnum;
import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.response.HttpInstallResponse;
import com.cgb.bcpinstall.common.response.ResponseCode;
import com.cgb.bcpinstall.common.util.HttpClientUtil;
import com.cgb.bcpinstall.common.util.NetUtil;
import com.cgb.bcpinstall.db.CheckPointDb;
import com.cgb.bcpinstall.db.table.NodeDO;
import com.cgb.bcpinstall.service.FabricCliService;
import com.cgb.bcpinstall.service.FileService;
import com.cgb.bcpinstall.service.ModeService;
import com.cgb.bcpinstall.service.UpdateService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * @author zheng.li
 * @date 2020/2/3 11:04
 */
@Service
@Slf4j
public class OrdererRemoveBiz {

    @Autowired
    private HttpClientUtil httpClient;

    @Autowired
    private CheckPointDb checkPointDb;

    @Autowired
    private ModeService modeService;

    @Autowired
    private FabricCliService fabricCliService;

    @Autowired
    private UpdateService updateService;

    @Autowired
    private FileService fileService;

    public void ordererRemove(Map<String, String> removedOrdererHostConfig, InitConfigEntity configEntity) {
        log.info("主节点启动 cli 容器");
        // 启动一个 cli 容器
        if (!fabricCliService.createCliContainer(modeService.getInstallPath() + "cli", configEntity)) {
            log.error("创建cli容器失败");
            return;
        }

        // 收集所有节点加入的通道
        log.info("获取所有节点加入的通道列表");
        Set<String> channelList = new HashSet<>();
        try {
            channelList.addAll(fabricCliService.getAllChannels(configEntity));
        } catch (IOException e) {
            log.error("获取节点加入的所有通道异常", e);
            e.printStackTrace();
        }
        log.info("缩容orderer-获取的通道列表：" + JSON.toJSONString(channelList));
        log.info("修改网络配置");

        Map<String, String> oldOrdererConfig = configEntity.getOrdererHostConfig();
        oldOrdererConfig.putAll(removedOrdererHostConfig);

        for (String host : removedOrdererHostConfig.keySet()) {
            log.info("缩容orderer-oldOrdererConfig=" + JSON.toJSONString(oldOrdererConfig));
            oldOrdererConfig.remove(host);
            // 修改网络配置
            log.info("将移除的 orderer(s) 从系统通道中移除");
            // 先修改系统通道
            if (!updateService.updateNetworkConfig(configEntity.getNetwork() + "-sys-channel", configEntity, oldOrdererConfig)) {
                log.error(String.format("为系统通道 %s 更新网络配置失败", configEntity.getNetwork() + "-sys-channel"));
                return;
            }
            channelList.remove(configEntity.getNetwork() + "-sys-channel");
            for (String channelName : channelList) {
                log.info(String.format("将移除的 orderer(s) 从通道 %s 中移除", channelName));
                if (!updateService.updateNetworkConfig(channelName, configEntity, oldOrdererConfig)) {
                    log.error(String.format("为通道 %s 更新网络配置失败", channelName));
                }
            }
        }


        log.info("移除主节点目录下的证书");
        fileService.removeCertFile(RoleEnum.ORDER, configEntity, removedOrdererHostConfig, true);
        log.info("移除 orderer(s) 节点");
        removeOrdererContainer(removedOrdererHostConfig, configEntity);
        log.info("将已移除的 orderer(s) 节点从数据库中删除");
        // 从数据库中删除
        for (String host : removedOrdererHostConfig.keySet()) {
            String ip = removedOrdererHostConfig.get(host);
            int index = ip.lastIndexOf(":");
            String port = ip.substring(index + 1);
            ip = ip.substring(0, index);

            NodeDO nodeDO = new NodeDO();
            nodeDO.setRole(RoleEnum.ORDER);
            nodeDO.setOrgMspId(configEntity.getOrgMSPID());
            nodeDO.setHostName(host);
            nodeDO.setIp(ip);
            nodeDO.setPort(Integer.parseInt(port));
            try {
                this.checkPointDb.deleteNodeRecord(nodeDO);
            } catch (SQLException e) {
                log.error(String.format("将节点 %s 从数据库中删除异常", host), e);
                e.printStackTrace();
            }
        }
    }

    private void removeOrdererContainer(Map<String, String> removedOrdererHostConfig, InitConfigEntity configEntity) {
        // 通知节点移除
        // 根据IP分组
        Map<String, List<String>> groups = new HashMap<>(16);
        for (String host : removedOrdererHostConfig.keySet()) {
            String ip = removedOrdererHostConfig.get(host);
            int index = ip.lastIndexOf(":");
            String port = ip.substring(index + 1);
            ip = ip.substring(0, index);

            List<String> hostList;
            if (groups.containsKey(ip)) {
                hostList = groups.get(ip);
            } else {
                hostList = new ArrayList<>();
                groups.put(ip, hostList);
            }
            hostList.add(host + ":" + port);
        }
        String stopNodeFilePath = modeService.getInitDir() + "template/stopNode.sh";
        for (String ip : groups.keySet()) {
            RemoveCmd peerRemoveCmd = updateService.createRemoveCmd(ip, groups.get(ip), RoleEnum.ORDER);
            peerRemoveCmd.setOrdererDomain(configEntity.getOrdererDomain());
            if (NetUtil.ipIsMine(ip)) {
                try {
                    FileUtils.copyFile(new File(stopNodeFilePath), new File(modeService.getInstallPath() + "stopNode.sh"));
                } catch (Exception e) {
                    log.info("复制stopNode.sh发生异常");
                    return;
                }
                updateService.removeNode(RoleEnum.ORDER, peerRemoveCmd.getOrdererDomain(), peerRemoveCmd.getHostNames(), peerRemoveCmd.getPorts());
            } else {

                String stopFilePath = modeService.getInitDir() + "template/stopNode.sh";
                String url = "http://" + ip + ":8080/v1/install/remove";
                do {
                    try {
                        String result = this.httpClient.sendFileAndJson(url, stopFilePath, JSONObject.toJSONString(peerRemoveCmd));
                        if (!StringUtils.isEmpty(result)) {
                            HttpInstallResponse response = JSONObject.parseObject(result, HttpInstallResponse.class);
                            if (ResponseCode.SUCCESS.getCode().equalsIgnoreCase(response.getCode())) {
                                break;
                            }
                        }

                        log.warn(String.format("给节点 %s 发送移除命令返回失败，稍后重试", ip));
                    } catch (Exception e) {
                        log.warn(String.format("给节点 %s 发送移除命令异常，稍后重试", ip), e);
                        e.printStackTrace();
                    }

                    try {
                        Thread.sleep(5000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } while (true);
            }
        }
    }

}
