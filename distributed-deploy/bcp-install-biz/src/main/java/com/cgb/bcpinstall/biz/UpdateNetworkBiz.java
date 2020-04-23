package com.cgb.bcpinstall.biz;

import com.cgb.bcpinstall.common.entity.*;
import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.db.CheckPointDb;
import com.cgb.bcpinstall.db.table.NodeDO;
import com.cgb.bcpinstall.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.sql.SQLException;
import java.util.*;

/**
 * @author zheng.li
 * @date 2020/2/2 14:01
 */
@Service
@Slf4j
public class UpdateNetworkBiz implements InstallMode {

    @Autowired
    private CheckPointDb checkPointDb;

    @Autowired
    private OrdererExtendBiz ordererExtendBiz;

    @Autowired
    private OrdererRemoveBiz ordererRemoveBiz;

    @Autowired
    private PeerRemoveBiz peerRemoveBiz;

    @Autowired
    private PeerExtendBiz peerExtendBiz;

    @Autowired
    private RemoteService remoteService;

    @Override
    public void run(InitConfigEntity configEntity) {
        DiffHostConfigs removedNodeConfigs = getRemovedNodes(configEntity);
        DiffHostConfigs addedNodeConfigs = getAddedNodes(configEntity);

        if (removedNodeConfigs == null && addedNodeConfigs == null
                || (CollectionUtils.isEmpty(removedNodeConfigs.getOrdererHostConfig())
                && CollectionUtils.isEmpty(removedNodeConfigs.getPeerHostConfig())
                && CollectionUtils.isEmpty(addedNodeConfigs.getOrdererHostConfig())
                && CollectionUtils.isEmpty(addedNodeConfigs.getPeerHostConfig()))) {
            log.info("未发现与上次安装的差异，无需安装任何节点");
            return;
        }
        doRemoveNodes(removedNodeConfigs, configEntity);
        doNewNodesInstall(addedNodeConfigs, configEntity);
        // 通知结束
        Set<String> serverUrls = new HashSet<>();
        parseServerUrl(serverUrls, removedNodeConfigs.getOrdererHostConfig());
        parseServerUrl(serverUrls, removedNodeConfigs.getPeerHostConfig());
        parseServerUrl(serverUrls, addedNodeConfigs.getOrdererHostConfig());
        parseServerUrl(serverUrls, addedNodeConfigs.getPeerHostConfig());
        remoteService.notifyNodesToEnd(serverUrls);
    }

    /**
     * 根据配置文件与数据库数据对比，获取移除节点信息
     *
     * @param configEntity
     * @return
     */
    private DiffHostConfigs getRemovedNodes(InitConfigEntity configEntity) {
        DiffHostConfigs removedNodes = new DiffHostConfigs();

        // 数据库中搜索所有 orderer 节点
        List<NodeDO> nodes = queryNodes(configEntity.getOrgMSPID(), RoleEnum.ORDER);
        removedNodes.setOrdererHostConfig(checkRemovedNodes(nodes, configEntity.getOrdererHostConfig()));
        nodes = queryNodes(configEntity.getOrgMSPID(), RoleEnum.PEER);
        removedNodes.setPeerHostConfig(checkRemovedNodes(nodes, configEntity.getPeerHostConfig()));

        return removedNodes;
    }

    private Map<String, String> checkRemovedNodes(List<NodeDO> prevNodes, Map<String, String> newHostConfig) {
        Map<String, String> removedHostConfig = new HashMap<>();

        for (NodeDO node : prevNodes) {
            if (!newHostConfig.containsKey(node.getHostName())) {
                removedHostConfig.put(node.getHostName(), node.getIp() + ":" + node.getPort());
            }
        }

        return removedHostConfig;
    }

    /**
     * 根据配置文件与数据库数据对比，获取添加节点信息
     *
     * @param configEntity
     * @return
     */
    private DiffHostConfigs getAddedNodes(InitConfigEntity configEntity) {
        DiffHostConfigs addedNodes = new DiffHostConfigs();

        List<NodeDO> nodes = queryNodes(configEntity.getOrgMSPID(), RoleEnum.ORDER);
        addedNodes.setOrdererHostConfig(checkAddedNodes(nodes, configEntity.getOrdererHostConfig()));

        nodes = queryNodes(configEntity.getOrgMSPID(), RoleEnum.PEER);
        addedNodes.setPeerHostConfig(checkAddedNodes(nodes, configEntity.getPeerHostConfig()));
        return addedNodes;
    }

    private Map<String, String> checkAddedNodes(List<NodeDO> prevNodes, Map<String, String> newHostConfig) {
        Map<String, String> addedHostConfig = new HashMap<>();

        for (String hostName : newHostConfig.keySet()) {
            if (prevNodes.stream().noneMatch(n -> n.getHostName().equalsIgnoreCase(hostName))) {
                addedHostConfig.put(hostName, newHostConfig.get(hostName));
            }
        }

        return addedHostConfig;
    }

    private void doRemoveNodes(DiffHostConfigs removedNodeConfigs, InitConfigEntity configEntity) {
        // Orderer
        if (!CollectionUtils.isEmpty(removedNodeConfigs.getOrdererHostConfig())) {
            ordererRemoveBiz.ordererRemove(removedNodeConfigs.getOrdererHostConfig(), configEntity);
        }
        // peer
        if (!CollectionUtils.isEmpty(removedNodeConfigs.getPeerHostConfig())) {
            peerRemoveBiz.peerRemove(removedNodeConfigs.getPeerHostConfig(), configEntity);
        }
    }

    private void doNewNodesInstall(DiffHostConfigs addedNodeConfigs, InitConfigEntity configEntity) {
        if (!CollectionUtils.isEmpty(addedNodeConfigs.getOrdererHostConfig())) {
            ordererExtendBiz.ordererExtend(addedNodeConfigs.getOrdererHostConfig(), configEntity);
        }

        if (!CollectionUtils.isEmpty(addedNodeConfigs.getPeerHostConfig())) {
            peerExtendBiz.peerExtend(addedNodeConfigs.getPeerHostConfig(), configEntity);
        }
    }

    private void parseServerUrl(Set<String> serverUrls, Map<String, String> hostConfig) {
        if (CollectionUtils.isEmpty(hostConfig)) {
            return;
        }

        for (String host : hostConfig.keySet()) {
            String ip = hostConfig.get(host);
            ip = ip.substring(0, ip.lastIndexOf(":"));

            serverUrls.add("http://" + ip + ":8080/");
        }
    }

    private List<NodeDO> queryNodes(String orgMspID, RoleEnum role) {
        NodeDO nodeDO = new NodeDO();
        nodeDO.setOrgMspId(orgMspID);
        nodeDO.setRole(role);
        try {
            return this.checkPointDb.find(nodeDO);
        } catch (SQLException e) {
            log.error("查询数据库异常", e);
            e.printStackTrace();
        }

        return null;
    }
}
