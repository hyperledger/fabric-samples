package com.cgb.bcpinstall.config.configGenImpl;

import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.util.FileUtil;
import com.cgb.bcpinstall.common.util.NetUtil;
import com.cgb.bcpinstall.config.DockerConfigGen;
import com.cgb.bcpinstall.service.ModeService;
import com.cgb.bcpinstall.service.YamlFileService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author zheng.li
 * @date 2020/3/12 15:47
 */
@Service
@Slf4j
public class DockerConfigGenImpl implements DockerConfigGen {

    @Autowired
    private ModeService modeService;

    @Autowired
    private YamlFileService yamlFileService;

    @Override
    public boolean peerComposeFileGen(InitConfigEntity initConfig) {
        // 根据 IP 地址分组
        Map<String, List<String>> peerGroups = this.groupHostByIp(initConfig.getPeerHostConfig());

        // 每个 IP 一个 docker-compose 文件
        for (String serverIp : peerGroups.keySet()) {
            String path = String.format("fabric-net/dockerFile/peer-%s/", serverIp);
            String filePath = null;
            try {
                filePath = createPeerYamlFile(initConfig, serverIp, peerGroups.get(serverIp), path);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (StringUtils.isEmpty(filePath)) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean ordererComposeFileGen(InitConfigEntity initConfig) {
        // 根据 IP 地址分组
        Map<String, List<String>> orderGroups = this.groupHostByIp(initConfig.getOrdererHostConfig());
        // 每个 IP 一个 docker-compose 文件
        for (String orderServerIp : orderGroups.keySet()) {
            String filePath = null;
            try {
                filePath = createOrdererYamlFile(initConfig, orderServerIp, orderGroups.get(orderServerIp), null);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (StringUtils.isEmpty(filePath)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 根据ip进行host分组，相同ip的host属于同一集合
     *
     * @param nodeHostConfig
     * @return
     */
    public Map<String, List<String>> groupHostByIp(Map<String, String> nodeHostConfig) {
        Map<String, List<String>> groups = new HashMap<>(16);

        for (String url : nodeHostConfig.keySet()) {
            String ip = nodeHostConfig.get(url);
            int index = ip.lastIndexOf(":");
            String port = ip.substring(index + 1);
            ip = ip.substring(0, index);

            List<String> nodes;
            if (groups.containsKey(ip)) {
                nodes = groups.get(ip);
            } else {
                nodes = new ArrayList<>();
                groups.put(ip, nodes);
            }
            nodes.add(url + ":" + port);
        }
        return groups;
    }

    /**
     * 根据模板生成orderer的docker-compose文件
     *
     * @param initConfig 配置信息
     * @param hostIp     orderer所在主节的ip
     * @param orderHosts orderer的host集合
     * @param folderName 配置文件生成路径
     * @return
     * @throws IOException
     */
    public String createOrdererYamlFile(InitConfigEntity initConfig, String hostIp, List<String> orderHosts, String folderName) throws IOException {
        String workingDir = modeService.getInitDir();
        Map<Object, Object> ordererComposeConfig = yamlFileService.loadYamlFile(workingDir + "/template/docker-compose-orderer.yaml");

        Map<Object, Object> networks = (Map<Object, Object>) ordererComposeConfig.get("networks");
        networks.clear();
        for (int i = 0; i < orderHosts.size(); ++i) {
            networks.put(initConfig.getNetwork() + i, null);
        }

        Map<Object, Object> valumes = (Map<Object, Object>) ordererComposeConfig.get("volumes");
        valumes.clear();
        for (String orderHost : orderHosts) {
            valumes.put(orderHost.substring(0, orderHost.lastIndexOf(":")), null);
        }

        Map<Object, Object> services = (Map<Object, Object>) ordererComposeConfig.get("services");
        services.clear();

        for (int hostIndex = 0; hostIndex < orderHosts.size(); ++hostIndex) {
            String orderHost = orderHosts.get(hostIndex);

            int index = orderHost.lastIndexOf(":");
            String port = orderHost.substring(index + 1);
            orderHost = orderHost.substring(0, index);

            Map<Object, Object> orderConfig = yamlFileService.loadYamlFile(workingDir + "template/orderer.yaml");
            Map<Object, Object> config = (Map<Object, Object>) orderConfig.get("orderer.example.com");
            config.put("container_name", orderHost);
            int finalHostIndex = hostIndex;
            config.put("networks", new ArrayList<String>() {{
                add(initConfig.getNetwork() + finalHostIndex);
            }});

            List<String> newVols = new ArrayList<>();
            List<String> vols = (List<String>) config.get("volumes");
            for (String item : vols) {
                String newItem;
                if (item.contains("orderer.example.com")) {
                    newItem = item.replace("orderer.example.com", orderHost);
                } else {
                    newItem = item;
                }
                if (newItem.contains("example.com")) {
                    int i = orderHost.indexOf(".");
                    newItem = newItem.replace("example.com", orderHost.substring(i + 1));
                }

                newVols.add(newItem);
            }
            newVols.add("/var/run/:/opt/gopath/src/github.com/hyperledger/fabric");
            config.put("volumes", newVols);

            // extra_hosts
            List<String> extraHosts = new ArrayList<>();
            for (String eHost : initConfig.getOrdererHostConfig().keySet()) {
                if (eHost.equalsIgnoreCase(orderHost)) {
                    continue;
                }

                String eIp = initConfig.getOrdererHostConfig().get(eHost);
                eIp = eIp.substring(0, eIp.lastIndexOf(":"));
                if (eIp.equalsIgnoreCase("127.0.0.1")) {
                    eIp = NetUtil.getMyNormalIP();
                }
                extraHosts.add(eHost + ":" + eIp);
            }
            if (!CollectionUtils.isEmpty(extraHosts)) {
                config.put("extra_hosts", extraHosts);
            }

            List<String> ports = (List<String>) config.get("ports");
            ports.clear();
            ports.add(port + ":7050");

            services.put(orderHost, config);
        }

        String filePath = workingDir + String.format("fabric-net/dockerFile/%s/", StringUtils.isEmpty(folderName) ? "order-" + hostIp : folderName);
        FileUtil.makeFilePath(filePath, false);
        String yamlFilePath = filePath + "docker-compose-orderer.yaml";
        if (!yamlFileService.writeObjectToYamlFile(ordererComposeConfig, yamlFilePath)) {
            return null;
        }

        // 复制 start-orderer.sh
        /*String content = getFileContent(workingDir + "template/start-peer.sh");
        String newFilePath = filePath + "start-orderer.sh";

        if (!writeFileContent(newFilePath, content)) {
            return null;
        }*/
        String srcShFilePath = workingDir + "template/start-peer.sh";
        String newShFilePath = filePath + "start-orderer.sh";
        FileUtils.copyFile(new File(srcShFilePath), new File(newShFilePath));
        return yamlFilePath;
    }

    /**
     * 根据模板生成peer的docker-compose文件
     *
     * @param initConfig 配置信息
     * @param hostIp     orderer所在主节的ip
     * @param peerHosts  orderer的host集合
     * @param folderName 配置文件生成路径
     * @return
     * @throws IOException
     */
    public String createPeerYamlFile(InitConfigEntity initConfig, String hostIp, List<String> peerHosts, String folderName) throws IOException {

        Map<Object, Object> peerComposeConfig = yamlFileService.loadYamlFile(modeService.getInitDir() + "template/docker-compose-peer.yaml");

        Map<Object, Object> networks = (Map<Object, Object>) peerComposeConfig.get("networks");
        networks.clear();
        networks.put(initConfig.getNetwork(), null);

        Map<Object, Object> valumes = (Map<Object, Object>) peerComposeConfig.get("volumes");
        valumes.clear();
        for (String host : peerHosts) {
            valumes.put(host.substring(0, host.lastIndexOf(":")), null);
        }

        Map<Object, Object> services = (Map<Object, Object>) peerComposeConfig.get("services");
        Map<Object, Object> cliConfig = (Map<Object, Object>) services.get("cli");
        services.clear();

        List<String> dependsOn = (List<String>) cliConfig.get("depends_on");
        dependsOn.clear();

        // 获取其他 peer 配置
        Map<String, String> othPeerHostConfig = new HashMap<>();
        Set<String> peerHostSet = new HashSet<>(peerHosts);
        for (String host : initConfig.getPeerHostConfig().keySet()) {
            String origIp = initConfig.getPeerHostConfig().get(host);
            String ip = origIp.split(":")[0];
            String port = origIp.split(":")[1];
            if (!ip.equalsIgnoreCase(hostIp)) {
                othPeerHostConfig.put(host, origIp);
            } else {
                host = host + ":" + port;
                peerHostSet.add(host);
            }
        }

        // peer
        /*int couchDbPort = 7984;*/
        /*int ssPort = 9443;*/
        String firstHost = null;
        for (String peerHost : peerHosts) {
            if (StringUtils.isEmpty(firstHost)) {
                firstHost = peerHost;
            }

            String finalPeerHost = peerHost;
            List<String> otherPeerHosts = peerHostSet.stream().filter(i -> !i.equals(finalPeerHost)).collect(Collectors.toList());
            if (!CollectionUtils.isEmpty(otherPeerHosts)) {
                for (String otherHost : otherPeerHosts) {
                    String host = otherHost.split(":")[0];
                    log.info("扩容节点其他节点的host:" + host);
                    String origIp = initConfig.getPeerHostConfig().get(host);
                    if (origIp.contains("127.0.0.1")) {
                        origIp = NetUtil.getMyNormalIP() + ":" + origIp.split(":")[1];
                    }
                    othPeerHostConfig.put(host, origIp);
                }
            }

            String peerHostPreFix = peerHost.split(initConfig.getPeerDomain())[0];
            // couchdb
            Map<Object, Object> couchDbConfig = new HashMap<>();
            couchDbConfig.put("container_name", peerHostPreFix + initConfig.getOrgMSPID() + "." + "couchdb");
            couchDbConfig.put("image", "hyperledger/fabric-couchdb");
            couchDbConfig.put("environment", new ArrayList<String>() {{
                add("COUCHDB_USER=");
                add("COUCHDB_PASSWORD=");
            }});

            String origHost = peerHost;
            int index = peerHost.lastIndexOf(":");
            int port = Integer.parseInt(peerHost.substring(index + 1));
            peerHost = peerHost.substring(0, index);

            String finalCouchdbPeerHost = peerHost;
            couchDbConfig.put("ports", new ArrayList<String>() {{
                add(initConfig.getCouchdbPortConfig().get(finalCouchdbPeerHost) + ":5984");
            }});
            couchDbConfig.put("networks", new ArrayList<String>() {{
                add(initConfig.getNetwork());
            }});
            services.put(peerHostPreFix + initConfig.getOrgMSPID() + "." + "couchdb", couchDbConfig);

            //
            Map<Object, Object> peerConfig = yamlFileService.loadYamlFile(modeService.getInitDir() + "template/peer.yaml");
            Map<Object, Object> config = (Map<Object, Object>) peerConfig.get("peer0.org1.example.com");
            config.put("container_name", peerHost);
            config.put("networks", new ArrayList<String>() {{
                add(initConfig.getNetwork());
            }});

            List<String> newVols = new ArrayList<>();
            List<String> vols = (List<String>) config.get("volumes");
            for (String item : vols) {
                String newItem;
                if (item.contains("peer0.org1.example.com")) {
                    newItem = item.replace("peer0.org1.example.com", peerHost);
                } else {
                    newItem = item;
                }
                if (newItem.contains("org1.example.com")) {
                    int i = peerHost.indexOf(".");
                    newItem = newItem.replace("org1.example.com", peerHost.substring(i + 1));
                }

                newVols.add(newItem);
            }
            config.put("volumes", newVols);

            // environment
            List<String> environment = new ArrayList<>();
            environment.add("CORE_VM_ENDPOINT=unix:///host/var/run/docker.sock");

            String netWorkConfig;
            if (folderName.contains("new")) {
                String peerPathSplit = folderName.split("peer-")[1];
                String peerNetSplit = peerPathSplit.split(File.separator)[0];
                netWorkConfig = "peer-" + peerNetSplit + "_" + initConfig.getNetwork();
            } else {
                netWorkConfig = "peer_" + initConfig.getNetwork();
            }

            environment.add("CORE_VM_DOCKER_HOSTCONFIG_NETWORKMODE=" + netWorkConfig);
            environment.add("FABRIC_LOGGING_SPEC=INFO");
            environment.add("CORE_PEER_TLS_ENABLED=true");
            environment.add("CORE_PEER_GOSSIP_USELEADERELECTION=true");
            environment.add("CORE_PEER_GOSSIP_ORGLEADER=false");
            environment.add("CORE_PEER_PROFILE_ENABLED=true");
            environment.add("CORE_PEER_TLS_CERT_FILE=/etc/hyperledger/fabric/tls/server.crt");
            environment.add("CORE_PEER_TLS_KEY_FILE=/etc/hyperledger/fabric/tls/server.key");
            environment.add("CORE_PEER_TLS_ROOTCERT_FILE=/etc/hyperledger/fabric/tls/ca.crt");
            environment.add("CORE_PEER_ID=" + peerHost);
            environment.add("CORE_PEER_ADDRESS=" + peerHost + ":" + port);
            environment.add("CORE_PEER_LISTENADDRESS=0.0.0.0:" + port);
            environment.add("CORE_PEER_CHAINCODEADDRESS=" + peerHost + ":" + (port + 1));
            environment.add("CORE_PEER_CHAINCODELISTENADDRESS=0.0.0.0:" + (port + 1));

            if (CollectionUtils.isEmpty(othPeerHostConfig)) {
                environment.add("CORE_PEER_GOSSIP_BOOTSTRAP=" + origHost);
            } else {
                String firstKey = othPeerHostConfig.keySet().iterator().next();
                String othIp = othPeerHostConfig.get(firstKey);
                environment.add("CORE_PEER_GOSSIP_BOOTSTRAP=" + firstKey + ":" + othIp.substring(othIp.lastIndexOf(":") + 1));
            }

            environment.add("CORE_PEER_GOSSIP_EXTERNALENDPOINT=" + origHost);
            environment.add("CORE_PEER_LOCALMSPID=" + initConfig.getOrgMSPID());
            environment.add("CORE_OPERATIONS_LISTENADDRESS=0.0.0.0" + ":9443");
            environment.add("CORE_METRICS_PROVIDER=prometheus");
            environment.add("CORE_LEDGER_STATE_STATEDATABASE=CouchDB");
            environment.add("CORE_LEDGER_STATE_COUCHDBCONFIG_COUCHDBADDRESS=" + peerHostPreFix + initConfig.getOrgMSPID() + "." + "couchdb" + ":5984");
            /*environment.add("CORE_LEDGER_STATE_COUCHDBCONFIG_COUCHDBADDRESS=couchdb" + hIndex + ":" + finalCouchDbPort);*/
            environment.add("CORE_LEDGER_STATE_COUCHDBCONFIG_USERNAME=");
            environment.add("CORE_LEDGER_STATE_COUCHDBCONFIG_PASSWORD=");

            config.put("environment", environment);

            // depends_on
            config.put("depends_on", new ArrayList<String>() {{
                add(peerHostPreFix + initConfig.getOrgMSPID() + "." + "couchdb");
            }});

            List<String> extraHosts = new ArrayList<>();
            for (String host : initConfig.getOrdererHostConfig().keySet()) {
                String ip = initConfig.getOrdererHostConfig().get(host);
                int i = ip.lastIndexOf(":");
                ip = ip.substring(0, i);

                if ("127.0.0.1".equalsIgnoreCase(ip)) {
                    ip = NetUtil.getMyNormalIP();
                }
                extraHosts.add(host + ":" + ip);
            }

            if (!CollectionUtils.isEmpty(othPeerHostConfig)) {
                for (String othHost : othPeerHostConfig.keySet()) {
                    String othIp = othPeerHostConfig.get(othHost);
                    int i = othIp.lastIndexOf(":");
                    othIp = othIp.substring(0, i);
                    if ("127.0.0.1".equals(othIp)) {
                        othIp = NetUtil.getMyNormalIP();
                    }
                    if (!peerHost.contains(othHost)) {
                        extraHosts.add(othHost + ":" + othIp);
                    }
                }
            }
//            extraHosts.add("couchdb" + hIndex + ":" + hostIp);
            if (!CollectionUtils.isEmpty(extraHosts)) {
                config.put("extra_hosts", extraHosts);
            }

            List<String> ports = (List<String>) config.get("ports");
            ports.clear();
            ports.add(port + ":" + port);
            String metricsPort = initConfig.getMetricPortConfig().get(peerHost);
            ports.add(metricsPort + ":" + "9443");
            services.put(peerHost, config);
            dependsOn.add(peerHost);
        }

        int index = firstHost.lastIndexOf(":");
        String onlyHost = firstHost.substring(0, index);
        String orgUrl = initConfig.getPeerDomain();
        List<String> newEnvironment = new ArrayList<>();
        List<String> environment = (List<String>) cliConfig.get("environment");
        for (String oldEnv : environment) {
            String newEnv;
            if (oldEnv.contains("CORE_PEER_ADDRESS")) {
                newEnv = "CORE_PEER_ADDRESS=" + firstHost;
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
            add(initConfig.getNetwork());
        }});

        cliConfig.remove("extra_hosts");
        services.put("cli", cliConfig);

        /*String filePath = workingDir + String.format("fabric-net/dockerFile/peer-%s/", hostIp);*/
        String filePath = modeService.getInitDir() + folderName;
        FileUtil.makeFilePath(filePath, false);
        String yamlFilePath = filePath + "docker-compose-peer.yaml";

        log.info("新节点生成docker-compose文件路径:" + yamlFilePath);

        if (!yamlFileService.writeObjectToYamlFile(peerComposeConfig, yamlFilePath)) {
            return null;
        }

        // 生成 start-peer.sh
        String newShFilePath = filePath + "start-peer.sh";
        String srcShFilePath = modeService.getInitDir() + "template/start-peer.sh";
        FileUtils.copyFile(new File(srcShFilePath), new File(newShFilePath));

        return yamlFilePath;
    }
}
