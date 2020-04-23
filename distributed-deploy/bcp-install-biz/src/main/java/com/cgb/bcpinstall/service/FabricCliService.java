package com.cgb.bcpinstall.service;

import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.util.FileUtil;
import com.cgb.bcpinstall.common.util.NetUtil;
import com.cgb.bcpinstall.common.util.ProcessUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * fabric-cLi的服务类
 *
 * @author zheng.li
 * @date 2020/3/2 15:22
 */
@Service
@Slf4j
public class FabricCliService {

    @Autowired
    private ModeService modeService;

    @Autowired
    private YamlFileService yamlFileService;

    /**
     * 获取创世区块
     *
     * @param configEntity
     * @return
     */
    public boolean fetchGenesisBlock(InitConfigEntity configEntity) {
        try {
            FileUtils.copyFile(new File(modeService.getInitDir() + "template/fetchGenesisBlock.sh"), new File(modeService.getInstallPath() + "cli/scripts/fetchGenesisBlock.sh"));
        } catch (IOException e) {
            log.error("复制脚本文件异常", e);
            e.printStackTrace();
            return false;
        }
        String firstOrdererHost = configEntity.getOrdererHostConfig().keySet().iterator().next();
        String ip = configEntity.getOrdererHostConfig().get(firstOrdererHost);
        String firstOrdererHostAddress = firstOrdererHost + ip.substring(ip.lastIndexOf(":"));
        String CHANNEL_NAME = configEntity.getNetwork() + "-sys-channel";
        String ORDERER_CA = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/%s/orderers/%s/msp/tlscacerts/tlsca.%s-cert.pem", configEntity.getOrdererDomain(), firstOrdererHost, configEntity.getOrdererDomain());
        String CORE_PEER_LOCALMSPID = "OrdererMSP";
        String CORE_PEER_ADDRESS = firstOrdererHostAddress;
        String CORE_PEER_TLS_ROOTCERT_FILE = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/%s/orderers/%s/tls/ca.crt", configEntity.getOrdererDomain(), firstOrdererHost);
        String CORE_PEER_MSPCONFIGPATH = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/%s/users/Admin@%s/msp", configEntity.getOrdererDomain(), configEntity.getOrdererDomain());

        String cmd = "docker exec cli bash scripts/fetchGenesisBlock.sh " + CHANNEL_NAME + " " + ORDERER_CA + " " + CORE_PEER_LOCALMSPID + " " + CORE_PEER_ADDRESS + " " + CORE_PEER_TLS_ROOTCERT_FILE + " " + CORE_PEER_MSPCONFIGPATH;
        try {
            ProcessUtil.execCmd(cmd, null, modeService.getInstallPath() + "cli");
            File installGenesisFile = new File(modeService.getInstallPath() + "channel-artifacts" + File.separator + "genesis.block");
            if (installGenesisFile.exists()) {
                installGenesisFile.delete();
            }
            File initPathGenesisFile = new File(modeService.getInitDir() + "fabric-net/cryptoAndConfig/channel-artifacts" + File.separator + "genesis.block");
            if (initPathGenesisFile.exists()) {
                initPathGenesisFile.delete();
            }
            FileUtils.copyFile(new File("/var/run/genesis.block"), new File(modeService.getInstallPath() + "channel-artifacts" + File.separator + "genesis.block"));
            FileUtils.copyFile(new File("/var/run/genesis.block"), initPathGenesisFile);
        } catch (Exception e) {
            log.error(String.format("执行脚本获取通道 %s 配置异常", CHANNEL_NAME), e);
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 通过cli容器获取本机构所以节点加入的通道集合
     *
     * @param initConfigEntity
     * @return
     * @throws IOException
     */
    public List<String> getAllChannels(InitConfigEntity initConfigEntity) throws IOException {
        List<String> channelList = new ArrayList<>();
        FileUtils.copyFile(new File(modeService.getInitDir() + "template/list-channels.sh"), new File(modeService.getInstallPath() + "cli/scripts/list-channels.sh"));

        for (String host : initConfigEntity.getPeerHostConfig().keySet()) {

            String ip = initConfigEntity.getPeerHostConfig().get(host);
            int index = ip.lastIndexOf(":");
            String port = ip.substring(index + 1);

            String CORE_PEER_ADDRESS = host + ":" + port;
            String CORE_PEER_TLS_ROOTCERT_FILE = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/%s/peers/%s/tls/ca.crt", initConfigEntity.getPeerDomain(), host);
            String CORE_PEER_TLS_CERT_FILE = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/%s/peers/%s/tls/server.crt", initConfigEntity.getPeerDomain(), host);
            String CORE_PEER_TLS_KEY_FILE = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/%s/peers/%s/tls/server.key", initConfigEntity.getPeerDomain(), host);

            try {
                ProcessUtil.Result result = ProcessUtil.execCmd("docker exec cli bash scripts/list-channels.sh " + CORE_PEER_ADDRESS + " " + CORE_PEER_TLS_ROOTCERT_FILE + " " + CORE_PEER_TLS_CERT_FILE + " " + CORE_PEER_TLS_KEY_FILE, null, modeService.getInstallPath() + "cli");

                if (result.getCode() == 0) {
                    String output = result.getData();
                    int i = output.indexOf("joined:");
                    output = output.substring(i + "joined:".length());
                    if (!StringUtils.isEmpty(output)) {
                        output = output.trim();
                        String[] channels = output.split("[\n]");
                        if (channels != null && channels.length > 0) {
                            for (String c : channels) {
                                c = c.trim();
                                if (StringUtils.isEmpty(c)) {
                                    continue;
                                }
                                channelList.add(c);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("执行 docker 脚步获取通道异常", e);
                e.printStackTrace();
            }
        }

        return channelList;
    }

    /**
     * 启动一个cli容器
     *
     * @param destFilePath
     * @param initConfigEntity
     * @return
     */
    public boolean createCliContainer(String destFilePath, InitConfigEntity initConfigEntity) {
        if (!destFilePath.endsWith(File.separator)) {
            destFilePath = destFilePath + File.separator;
        }

        FileUtil.makeFilePath(destFilePath + "scripts", true);

        try {
            if (!this.createCliYamlFile(destFilePath, initConfigEntity)) {
                log.error("创建cli容器相关文件失败");
                return false;
            }
        } catch (IOException e) {
            log.error("创建cli容器相关文件异常", e);
            e.printStackTrace();

            return false;
        }

        try {
            ProcessUtil.Result result = ProcessUtil.execCmd("bash " + destFilePath + "start-peer.sh startCli", null, destFilePath);
            if (result.getCode() == 0) {
                log.info("启动 cli 容器成功");
                return true;
            }
        } catch (Exception e) {
            log.error("执行启动 cli 容器的脚步异常", e);
            e.printStackTrace();
        }

        return false;
    }

    public boolean createCliYamlFile(String destFilePath, InitConfigEntity initConfig) throws IOException {

        Map<Object, Object> peerComposeConfig = yamlFileService.loadYamlFile(modeService.getInitDir() + "template/docker-compose-peer.yaml");

        Map<Object, Object> networks = (Map<Object, Object>) peerComposeConfig.get("networks");
        networks.clear();

        String network = initConfig.getNetwork() + "-cli";
        networks.put(network, null);

        peerComposeConfig.remove("volumes");

        Map<Object, Object> services = (Map<Object, Object>) peerComposeConfig.get("services");
        Map<Object, Object> cliConfig = (Map<Object, Object>) services.get("cli");
        services.clear();

        cliConfig.remove("depends_on");

        String orgUrl = initConfig.getPeerDomain();

        String peerHost = initConfig.getPeerHostConfig().keySet().iterator().next();
        String peerIp = initConfig.getPeerHostConfig().get(peerHost);
        String onlyHost = peerHost.substring(0, peerHost.indexOf("."));

        List<String> newEnvironment = new ArrayList<>();
        List<String> environment = (List<String>) cliConfig.get("environment");
        for (String oldEnv : environment) {
            String newEnv;
            if (oldEnv.contains("CORE_PEER_ADDRESS")) {
                newEnv = "CORE_PEER_ADDRESS=" + peerHost + peerIp.substring(peerIp.lastIndexOf(":"));
            } else if (oldEnv.contains("CORE_PEER_LOCALMSPID")) {
                newEnv = "CORE_PEER_LOCALMSPID=" + initConfig.getOrgMSPID();
            } else if (oldEnv.contains("CORE_PEER_TLS_CERT_FILE")) {
                newEnv = String.format(oldEnv, orgUrl, onlyHost);
            } else if (oldEnv.contains("CORE_PEER_TLS_KEY_FILE")) {
                newEnv = String.format(oldEnv, orgUrl, onlyHost);
            } else if (oldEnv.contains("CORE_PEER_TLS_ROOTCERT_FILE")) {
                newEnv = String.format(oldEnv, orgUrl, onlyHost);
            } else if (oldEnv.contains("CORE_PEER_MSPCONFIGPATH")) {
                newEnv = String.format(oldEnv, orgUrl, orgUrl);
            } else {
                newEnv = oldEnv;
            }
            newEnvironment.add(newEnv);
        }
        cliConfig.put("environment", newEnvironment);

        cliConfig.put("networks", new ArrayList<String>() {{
            add(network);
        }});

        List<String> extraHosts = new ArrayList<>();
        for (String eHost : initConfig.getOrdererHostConfig().keySet()) {
            String eIp = initConfig.getOrdererHostConfig().get(eHost);
            eIp = eIp.substring(0, eIp.lastIndexOf(":"));
            if (eIp.equalsIgnoreCase("127.0.0.1")) {
                eIp = NetUtil.getMyNormalIP();
            }
            extraHosts.add(eHost + ":" + eIp);
        }
        for (String eHost : initConfig.getPeerHostConfig().keySet()) {
            String eIp = initConfig.getPeerHostConfig().get(eHost);
            eIp = eIp.substring(0, eIp.lastIndexOf(":"));
            if (eIp.equalsIgnoreCase("127.0.0.1")) {
                eIp = NetUtil.getMyNormalIP();
            }
            extraHosts.add(eHost + ":" + eIp);
        }
        cliConfig.put("extra_hosts", extraHosts);

        services.put("cli", cliConfig);

        if (!destFilePath.endsWith(File.separator)) {
            destFilePath = destFilePath + File.separator;
        }

        FileUtil.makeFilePath(destFilePath, false);
        String yamlFilePath = destFilePath + "docker-compose-peer.yaml";
        if (!yamlFileService.writeObjectToYamlFile(peerComposeConfig, yamlFilePath)) {
            return false;
        }

        // 生成 start-peer.sh
        /*String content = getFileContent(workingDir + "template/start-peer.sh");
        String newFilePath = destFilePath + "start-peer.sh";

        if (!writeFileContent(newFilePath, content)) {
            return false;
        }*/
        String newShFilePath = destFilePath + "start-peer.sh";
        String srcShFilePat = modeService.getInitDir() + "template/start-peer.sh";
        FileUtils.copyFile(new File(srcShFilePat), new File(newShFilePath));

        return true;
    }
}
