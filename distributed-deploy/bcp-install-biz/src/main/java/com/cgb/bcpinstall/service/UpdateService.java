package com.cgb.bcpinstall.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.cgb.bcpinstall.common.entity.RemoveCmd;
import com.cgb.bcpinstall.common.entity.RoleEnum;
import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.util.*;
import com.cgb.bcpinstall.config.GlobalConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * 扩缩容节点的服务类
 *
 * @author zheng.li
 * @date 2020/3/2 15:36
 */
@Service
@Slf4j
public class UpdateService {

    @Autowired
    private ModeService modeService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    protected HttpClientUtil httpClient;

    @Autowired
    protected GlobalConfig globalConfig;

    /**
     * 根据添加的新节点与最新的节点配置返回更新前的原节点配置
     *
     * @param currentConfig
     * @param newConfig
     * @return
     */
    public Map<String, String> getOldNodeConfigMap(Map<String, String> currentConfig, Map<String, String> newConfig) {
        if (CollectionUtils.isEmpty(currentConfig) && CollectionUtils.isEmpty(newConfig)) {
            return new HashMap<>(16);
        }
        newConfig.keySet().forEach(currentConfig::remove);
        return currentConfig;
    }

    public RemoveCmd createRemoveCmd(String ip, List<String> ipPorts, RoleEnum role) {
        RemoveCmd removeCmd = new RemoveCmd();
        List<String> hosts = new ArrayList<>();
        List<String> ports = new ArrayList<>();
        removeCmd.setIp(ip);
        removeCmd.setRole(role);
        for (String ipPort : ipPorts) {
            String[] ipPortSplit = ipPort.split(":");
            if (ipPortSplit.length != 2) {
                return null;
            }
            hosts.add(ipPortSplit[0]);
            ports.add(ipPortSplit[1]);
        }
        removeCmd.setHostNames(hosts);
        removeCmd.setPorts(ports);
        return removeCmd;
    }

    /**
     * 更新 fabric 网络配置
     *
     * @param channelName
     * @param configEntity
     * @return
     */
    public boolean updateNetworkConfig(String channelName, InitConfigEntity configEntity, Map<String, String> ordererConfigMap) {
        FileUtil.makeFilePath(modeService.getInstallPath() + "cli/scripts", false);

        try {
            FileUtils.copyFile(new File(modeService.getInitDir() + "template/fetch-config.sh"), new File(modeService.getInstallPath() + "cli/scripts/fetch-config.sh"));
            FileUtils.copyFile(new File(modeService.getInitDir() + "template/create-update-pb.sh"), new File(modeService.getInstallPath() + "cli/scripts/create-update-pb.sh"));
            FileUtils.copyFile(new File(modeService.getInitDir() + "template/update-channel-config.sh"), new File(modeService.getInstallPath() + "cli/scripts/update-channel-config.sh"));
        } catch (IOException e) {
            log.error("复制脚本文件异常", e);
            e.printStackTrace();
            return false;
        }

        // 获取通道配置信息
        String firstOrdererHost = configEntity.getOrdererHostConfig().keySet().iterator().next();
        String ip = configEntity.getOrdererHostConfig().get(firstOrdererHost);
        String firstOrdererHostAddress = firstOrdererHost + ip.substring(ip.lastIndexOf(":"));
        String CHANNEL_NAME = channelName;
        String ORDERER_CA = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/%s/orderers/%s/msp/tlscacerts/tlsca.%s-cert.pem", configEntity.getOrdererDomain(), firstOrdererHost, configEntity.getOrdererDomain());
        String CORE_PEER_LOCALMSPID = "OrdererMSP";
        String CORE_PEER_ADDRESS = firstOrdererHostAddress;
        String CORE_PEER_TLS_ROOTCERT_FILE = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/%s/orderers/%s/tls/ca.crt", configEntity.getOrdererDomain(), firstOrdererHost);
        String CORE_PEER_MSPCONFIGPATH = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/%s/users/Admin@%s/msp", configEntity.getOrdererDomain(), configEntity.getOrdererDomain());

        String cmd = "docker exec cli bash scripts/fetch-config.sh " + CHANNEL_NAME + " " + ORDERER_CA + " " + CORE_PEER_LOCALMSPID + " " + CORE_PEER_ADDRESS + " " + CORE_PEER_TLS_ROOTCERT_FILE + " " + CORE_PEER_MSPCONFIGPATH;
        try {
            ProcessUtil.execCmd(cmd, null, modeService.getInstallPath() + "cli");
        } catch (Exception e) {
            log.error(String.format("执行脚本获取通道 %s 配置异常", channelName), e);
            e.printStackTrace();
            return false;
        }

        // 修改通道配置: 增加 orderer 相关配置
        String filePath = String.format("/var/run/config_%s.json", channelName);
        if (!new File(filePath).exists()) {
            log.error(String.format("通道 %s 配置文件 %s 不存在", channelName, filePath));
            return false;
        }
        JSONObject jsonObject = JSONObject.parseObject(FileUtil.getFileContent(filePath));
        JSONArray array = jsonObject.getJSONObject("channel_group").getJSONObject("groups").getJSONObject("Orderer").getJSONObject("values").getJSONObject("ConsensusType").getJSONObject("value").getJSONObject("metadata").getJSONArray("consenters");
        JSONArray ordererAddresses = jsonObject.getJSONObject("channel_group").getJSONObject("values").getJSONObject("OrdererAddresses").getJSONObject("value").getJSONArray("addresses");
        array.clear();
        ordererAddresses.clear();

        for (String oHost : ordererConfigMap.keySet()) {
            String oIp = ordererConfigMap.get(oHost);

            String certFilePath = modeService.getInstallPath() + "crypto-config/ordererOrganizations/" + configEntity.getOrdererDomain() + "/orderers/" + oHost + "/tls/server.crt";
            if (!new File(certFilePath).exists()) {
                log.error(String.format("orderer %s 的证书文件 %s 不存在", oHost, certFilePath));
                return false;
            }

            String content = FileUtil.getFileContent(certFilePath);
            String certStr = new String(Base64.getEncoder().encode(content.getBytes()));

            JSONObject newOrdererObject = new JSONObject();
            newOrdererObject.put("client_tls_cert", certStr);
            newOrdererObject.put("host", oHost);
            newOrdererObject.put("port", oIp.substring(oIp.lastIndexOf(":") + 1));
            newOrdererObject.put("server_tls_cert", certStr);

            String ordererAddr = oHost + ":" + oIp.substring(oIp.lastIndexOf(":") + 1);
            array.add(newOrdererObject);
            ordererAddresses.add(ordererAddr);
        }

        File modifiedFile = new File(String.format("/var/run/config_%s_modified.json", channelName));
        FileUtil.writeTxtFile(jsonObject.toJSONString(), modifiedFile, "UTF-8");
        if (!modifiedFile.exists()) {
            log.error(String.format("创建通道 %s 配置编辑文件 %s 失败", channelName, modifiedFile.getAbsolutePath()));
            return false;
        }

        // 创建 pb 文件
        cmd = "docker exec cli bash scripts/create-update-pb.sh " + CHANNEL_NAME;
        try {
            ProcessUtil.execCmd(cmd, null, "/var/run");
        } catch (Exception e) {
            log.error(String.format("为通道 %s 执行脚本生成pb文件异常", channelName), e);
            e.printStackTrace();
            return false;
        }
        // 更新通道
        cmd = "docker exec cli bash scripts/update-channel-config.sh " + CHANNEL_NAME + " " + ORDERER_CA + " " + CORE_PEER_LOCALMSPID + " " + CORE_PEER_ADDRESS + " " + CORE_PEER_TLS_ROOTCERT_FILE + " " + CORE_PEER_MSPCONFIGPATH;
        try {
            ProcessUtil.execCmd(cmd, null, modeService.getInstallPath() + "cli");
        } catch (Exception e) {
            log.error(String.format("为通道 %s 执行脚本更新网络配置异常", channelName), e);
            e.printStackTrace();
        }

        return true;
    }

    public boolean removeNode(RoleEnum role, String domain, List<String> hostNames, List<String> ports) {
        if (!CollectionUtils.isEmpty(ports)) {
            ports.forEach(environmentService::removePortFromFirewall);
        }
        for (String host : hostNames) {
            try {
                log.info("移除节点容器：sh stopNode.sh " + host.split(domain)[0]);
                ProcessUtil.execCmd("sh stopNode.sh " + host.split(domain)[0], null, modeService.getInstallPath());
                String roleFileName = role == RoleEnum.ORDER ? "ordererOrganizations" : "peerOrganizations";
                String nodeFileName = role == RoleEnum.ORDER ? "orderers" : "peers";
                String rmCertFile = String.format("crypto-config" + File.separator + "%s" + File.separator + "%s" + File.separator + "%s" + File.separator + "%s", roleFileName, domain, nodeFileName, host);
                log.info("移除节点容器证书，路径：" + modeService.getInstallPath() + rmCertFile);
                FileUtil.rmFile(new File(modeService.getInstallPath() + rmCertFile));
            } catch (Exception e) {
                log.error(String.format("移除节点 %s 异常", host), e);
                e.printStackTrace();
            }
        }
        return true;
    }
}
