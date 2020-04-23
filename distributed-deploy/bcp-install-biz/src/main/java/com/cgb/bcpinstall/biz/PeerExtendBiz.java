package com.cgb.bcpinstall.biz;

import com.alibaba.fastjson.JSON;
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
 * @date 2020/2/3 10:03
 */
@Service
@Slf4j
public class PeerExtendBiz {

    @Autowired
    private RolesBiz rolesBiz;

    @Autowired
    private CheckPointDb checkPointDb;

    @Autowired
    private ModeService modeService;

    @Autowired
    private FileService fileService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private FabricCliService fabricCliService;

    @Autowired
    private RemoteService remoteService;

    @Autowired
    private InstallService installService;

    @Autowired
    private FabricConfigGen fabricConfigGen;

    @Autowired
    private ConfigFileGen configFileGen;

    @Autowired
    private DockerConfigGenImpl dockerConfigGen;

    public void peerExtend(Map<String, String> diffPeerHostConfig, InitConfigEntity configEntity) {
        //在主节点修改crypto-config.yaml文件，添加新节点hostname,编写生成证书命令行，参数extend，执行命令生成新节点证书,生成新增节点的compose文件
        //在crypto-config配置文件添加新节点hostName，调用generate.sh生成新节点证书
        log.info("在crypto-config配置文件添加新节点hostName，调用generate.sh生成新节点证书");
        /*initializer.reCreateNewPeerConfigFile(configEntity);*/
        fabricConfigGen.cryptoGen(configEntity);
        configFileGen.createExtendCerts();
        //生成新节点的docker-compose文件
        log.info("生成新节点的docker-compose文件");

        Map<String, List<String>> peerHostGroup = dockerConfigGen.groupHostByIp(diffPeerHostConfig);
        Map<String, String> ipPathMap = this.createNewPeerDockerFile(configEntity, peerHostGroup);
        //将新生成的证书拷贝到主节点安装目录
        log.info("将新生成的证书拷贝到主节点安装目录");
        fileService.masterCopyCryptoConfig();
        log.info("注册新Peer节点角色");
        List<String> ports = this.registerNewPeerRole(peerHostGroup);
        log.info("推送新peer的安装文件");
        this.sendNewPeerFile(ipPathMap, configEntity);
        //启动新节点
        log.info("启动新新增peer");
        List<ServerEntity> serverList = this.startNewPeer(ipPathMap, ports, configEntity);
        log.info("等待所有 peer 启动成功");
        while (serverList.stream().anyMatch(s -> s.getStatus() != InstallStatusEnum.SUCCESS)) {
            try {
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        // 将新节点的域名更新到cli所在宿主机的host
        // 在宿主机防火墙中打开新节点的端口
        log.info("将新节点的域名写入到hosts，在防火墙中开启新节点端口");
        environmentService.updateNewPeerHostPort(diffPeerHostConfig);

        // 启动一个 cli 容器
        log.info("主节点创建cli容器");
        if (!fabricCliService.createCliContainer(modeService.getInstallPath() + "cli", configEntity)) {
            log.error("创建cli容器失败");
            return;
        }

        //获取扩容peer可以加入的业务链列表
        Set<String> channelList = new HashSet<>();
        try {
            channelList.addAll(fabricCliService.getAllChannels(configEntity));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //新节点加入链
        if (!CollectionUtils.isEmpty(channelList)) {
            log.info("新增节点加入链");
            Set<String> joinChannels = newPeerJoinChannel(configEntity, diffPeerHostConfig, channelList);
            log.info("新增节点加入链-joinChannels=" + JSON.toJSONString(joinChannels));
        }

        log.info("将新 peer(s) 信息加入本地数据库");
        // 更新数据库
        for (String host : diffPeerHostConfig.keySet()) {
            String ip = diffPeerHostConfig.get(host);
            int index = ip.lastIndexOf(":");
            String port = ip.substring(index + 1);
            ip = ip.substring(0, index);
            NodeDO nodeDO = new NodeDO();
            nodeDO.setOrgMspId(configEntity.getOrgMSPID());
            nodeDO.setRole(RoleEnum.PEER);
            nodeDO.setHostName(host);
            nodeDO.setIp(ip);
            nodeDO.setPort(Integer.parseInt(port));
            nodeDO.setStatus(InstallStatusEnum.SUCCESS);

            try {
                this.checkPointDb.addNodeRecord(nodeDO);
            } catch (SQLException e) {
                log.error(String.format("添加新 peer 节点 %s 记录到数据库异常", host), e);
                e.printStackTrace();
            }
        }

    }

    private List<String> registerNewPeerRole(Map<String, List<String>> peerHostGroup) {
        List<String> ports = new ArrayList<>();
        for (String ip : peerHostGroup.keySet()) {
            List<String> hostList = peerHostGroup.get(ip);
            for (String host : hostList) {
                int index = host.lastIndexOf(":");
                ports.add(host.substring(index + 1));
            }
            this.rolesBiz.addRole(RoleEnum.PEER, "http://" + ip + ":8080", ip, ports);
        }
        return ports;
    }

    private void sendNewPeerFile(Map<String, String> ipPathMap, InitConfigEntity configEntity) {
        for (String ip : ipPathMap.keySet()) {
            String path = ipPathMap.get(ip);

            String folderName = new File(path).getName();
            if (NetUtil.ipIsMine(ip)) {
                fileService.copyFiles(RoleEnum.PEER, ip, folderName, modeService.getInstallPath(), folderName, configEntity, null);
                this.rolesBiz.setServerStatus(ip, InstallStatusEnum.DOWNLOADED);
            } else {
                log.info("为新增 peer 打包安装包");

                String packFilePath = fileService.packExtendNodeFiles(ip, folderName, RoleEnum.PEER, configEntity);

                // 发送到节点启动
                log.info("将生成的文件包发送到新增 peer 节点");
                remoteService.pushSlaveInstallPackage(ip, packFilePath, configEntity);
            }
        }
    }

    private List<ServerEntity> startNewPeer(Map<String, String> ipPathMap, List<String> ports, InitConfigEntity configEntity) {
        List<ServerEntity> serverList = this.rolesBiz.getRolesMap().get(RoleEnum.PEER);
        for (String ip : ipPathMap.keySet()) {
            log.info(String.format("发送安装命令到新增 peer 节点 %s", ip));

            String path = ipPathMap.get(ip);

            String folderName = new File(path).getName();

            // 如果主节点也是此角色，则先安装
            if (NetUtil.ipIsMine(ip)) {
                Map<String, String> hosts = environmentService.getRoleNeedSetHost(RoleEnum.PEER, configEntity);
                if (installService.startRole(RoleEnum.PEER, ports, hosts, folderName)) {
                    this.rolesBiz.setServerStatus(ip, InstallStatusEnum.SUCCESS);
                }
            } else {
                for (ServerEntity server : serverList) {
                    if (server.getHost().equalsIgnoreCase(ip)) {
                        // 发送安装指令给从节点
                        do {
                            HttpInstallResponse response = remoteService.sendInstallCommand(server, RoleEnum.PEER, folderName, configEntity);
                            if (ResponseCode.SUCCESS.getCode().equals(response.getCode())) {
                                log.warn(String.format("发送安装指令给 %s 节点安装 peer 成功", ip));
                                this.rolesBiz.setServerStatus(ip, InstallStatusEnum.INSTALLING);
                                break;
                            }

                            log.warn(String.format("发送安装指令给 %s 节点安装 peer 失败，稍后重试...", ip));
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
        return serverList;
    }

    private Set<String> newPeerJoinChannel(InitConfigEntity configEntity, Map<String, String> diffPeerHostConifg, Set<String> channelList) {
        //获取运行脚本所需参数，peerAddress、peerTlsRoot、peerTlsCert,peerTlsKey、orderer的域名:端口、对应orderer的ca证书、加入链的名称、
        String privateChannel = "privatechannel" + configEntity.getOrgMSPID().toLowerCase();
        String ordererHost = configEntity.getOrdererHostConfig().keySet().iterator().next();
        String ordererPort = configEntity.getOrdererHostConfig().get(ordererHost).split(":")[1];
        String orderer = ordererHost + ":" + ordererPort;
        String ordererCaPath = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/ordererOrganizations/%s/orderers/%s/msp/tlscacerts/tlsca.%s-cert.pem", configEntity.getOrdererDomain(), ordererHost, configEntity.getOrdererDomain());
        Set<String> joinChannels = null;
        for (String peerHost : diffPeerHostConifg.keySet()) {
            //扩容节点选择加入链
            joinChannels = this.selectJoinChannel(channelList, privateChannel);
            //初始化环境参数
            String peerPort = diffPeerHostConifg.get(peerHost).split(":")[1];
            String peerAddress = peerHost + ":" + peerPort;
            String peerTlsCaPath = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/%s/peers/%s/tls/ca.crt", configEntity.getPeerDomain(), peerHost);
            String peerTlsCertPath = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/%s/peers/%s/tls/server.crt", configEntity.getPeerDomain(), peerHost);
            String peerTlsKeyPath = String.format("/opt/gopath/src/github.com/hyperledger/fabric/peer/crypto/peerOrganizations/%s/peers/%s/tls/server.key", configEntity.getPeerDomain(), peerHost);
            //初始化执行命令
            for (String joinChannel : joinChannels) {
                String cmd = String.format("docker exec cli bash ./scripts/newPeerJoinChannel.sh -c %s -o %s -O %s -p %s -r %s -e %s -k %s", joinChannel, orderer, ordererCaPath, peerAddress, peerTlsCaPath, peerTlsCertPath, peerTlsKeyPath);
                //执行peer加入链的命令
                try {
                    FileUtils.copyFile(new File(modeService.getInitDir() + "template/newPeerJoinChannel.sh"), new File(modeService.getInstallPath() + "cli/scripts/newPeerJoinChannel.sh"));
                    ProcessUtil.Result result = ProcessUtil.execCmd(cmd, null, modeService.getInstallPath() + "cli");
                } catch (Exception e) {
                    log.error("执行 docker 脚本异常", e);
                    e.printStackTrace();
                }
            }
        }
        return joinChannels;
    }

    /**
     * 新节点加入所选链
     *
     * @param queryChannelList
     * @param privateChannel
     * @return
     */
    private Set<String> selectJoinChannel(Set<String> queryChannelList, String privateChannel) {
        Set<String> selectChannels = new HashSet<>();
        Map<String, String> selectChannelIndexMap = new HashMap<>(16);
        //选择需要加入的业务链
        System.out.println("请根据以下列表，选择扩容peer需要加入的业务链。");
        StringBuilder builder = new StringBuilder();
        int nextLine = 1;
        for (String channel : queryChannelList) {
            if (channel.equals(privateChannel) || StringUtils.isEmpty(channel)) {
                continue;
            }

            builder.append(nextLine).append(")").append(channel).append("\t");
            selectChannelIndexMap.put(Integer.toString(nextLine), channel);
            if (nextLine % 5 == 0) {
                builder.append("\n");
            }
            nextLine += 1;
        }
        System.out.println(builder.toString());
        Scanner sc = new Scanner(System.in);
        System.out.print("请输入需要加入的链名编号(多于一个编号时以“,”分割,请回车后输入):");

        String inputIndexList = "";
        if (sc.hasNextLine()) {
            inputIndexList = sc.nextLine();
            System.out.println("用户输入" + inputIndexList);
        }
        sc.close();

        if (StringUtils.isEmpty(inputIndexList)) {
            log.info("扩容节点——用户输入为空，默认加入本机构的所有业务链");
            selectChannels.addAll(queryChannelList);
            selectChannels.remove("");
        } else {
            String[] channelIndexArray = inputIndexList.split(",");
            Arrays.stream(channelIndexArray).forEach(i -> {
                String channelName = selectChannelIndexMap.get(i);
                if (!StringUtils.isEmpty(channelName)) {
                    selectChannels.add(channelName);
                }
            });
            selectChannels.add(privateChannel);
        }

        return selectChannels;
    }

    /**
     * 生成新节点的docker文件
     *
     * @param configEntity
     * @param peerHostGroup
     * @throws IOException
     */
    private Map<String, String> createNewPeerDockerFile(InitConfigEntity configEntity, Map<String, List<String>> peerHostGroup) {
        Map<String, String> ipPathMap = new HashMap<>(16);
        for (String peerServerIp : peerHostGroup.keySet()) {
            try {
                String folderName = UUID.randomUUID().toString();
                folderName = StringUtils.deleteAny(folderName, "-");
                folderName = "new-" + folderName;
                String filePath = dockerConfigGen.createPeerYamlFile(configEntity, peerServerIp, peerHostGroup.get(peerServerIp), "fabric-net/dockerFile" + File.separator + "peer-" + folderName + File.separator);
                String parentPath = new File(filePath).getParent();
                ipPathMap.put(peerServerIp, parentPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ipPathMap;
    }
}
