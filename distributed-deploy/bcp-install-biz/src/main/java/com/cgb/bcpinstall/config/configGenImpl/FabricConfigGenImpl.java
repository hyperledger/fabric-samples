package com.cgb.bcpinstall.config.configGenImpl;

import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.util.FileUtil;
import com.cgb.bcpinstall.config.FabricConfigGen;
import com.cgb.bcpinstall.service.ModeService;
import com.cgb.bcpinstall.service.YamlFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author zheng.li
 * @date 2020/3/12 15:48
 */
@Service
@Slf4j
public class FabricConfigGenImpl implements FabricConfigGen {

    private static final String CONFIGTX = "configtx";
    private static final String CRYPTO = "crypto";

    @Autowired
    private ModeService modeService;

    @Autowired
    private YamlFileService yamlFileService;

    @Override
    public boolean configTxGen(InitConfigEntity initConfig) {
        Map<Object, Object> configTxEntity = this.loadTemplate(CONFIGTX);
        if (CollectionUtils.isEmpty(configTxEntity)) {
            return false;
        }
        Map<Object, Object> ordererOrg = null;
        Map<Object, Object> org1 = null;
        List<Map<Object, Object>> organizations = (List<Map<Object, Object>>) configTxEntity.get("Organizations");
        for (Map<Object, Object> item : organizations) {
            if ("OrdererOrg".equals(item.get("Name"))) {
                item.put("MSPDir", String.format("crypto-config/ordererOrganizations/%s/msp", initConfig.getOrdererDomain()));
                ordererOrg = item;
            } else {
                org1 = item;
                item.put("Name", initConfig.getOrgMSPID());
                item.put("ID", initConfig.getOrgMSPID());
                item.put("MSPDir", String.format("crypto-config/peerOrganizations/%s/msp", initConfig.getPeerDomain()));

                Map<Object, Object> policies = (Map<Object, Object>) item.get("Policies");
                Map<Object, Object> config = (Map<Object, Object>) policies.get("Readers");
                config.put("Rule", String.format("OR('%s.admin','%s.peer','%s.client')", initConfig.getOrgMSPID(), initConfig.getOrgMSPID(), initConfig.getOrgMSPID()));

                config = (Map<Object, Object>) policies.get("Writers");
                config.put("Rule", String.format("OR('%s.admin','%s.client')", initConfig.getOrgMSPID(), initConfig.getOrgMSPID()));

                config = (Map<Object, Object>) policies.get("Admins");
                config.put("Rule", String.format("OR('%s.admin')", initConfig.getOrgMSPID()));

                String peerHost = initConfig.getPeerHostConfig().keySet().iterator().next();
                String peerIp = initConfig.getPeerHostConfig().get(peerHost);
                int index = peerIp.lastIndexOf(":");

                List<Map<Object, Object>> anchorPeers = (List<Map<Object, Object>>) item.get("AnchorPeers");
                anchorPeers.get(0).put("Host", peerHost);
                anchorPeers.get(0).put("Port", Integer.parseInt(peerIp.substring(index + 1)));
            }
        }

        String firstOrdererHost = initConfig.getOrdererHostConfig().keySet().iterator().next();
        String firstOrdererIp = initConfig.getOrdererHostConfig().get(firstOrdererHost);
        int index = firstOrdererIp.lastIndexOf(":");

        // Orderer
        Map<Object, Object> orderer = (Map<Object, Object>) configTxEntity.get("Orderer");
        List<String> orderAddresses = (List<String>) orderer.get("Addresses");
        orderAddresses.clear();
        orderAddresses.add(String.format(firstOrdererHost + firstOrdererIp.substring(index)));

        // SampleMultiNodeEtcdRaft
        Map<Object, Object> profile = (Map<Object, Object>) ((Map<Object, Object>) configTxEntity.get("Profiles")).get("SampleMultiNodeEtcdRaft");
        Map<Object, Object> orderConfig = (Map<Object, Object>) profile.get("Orderer");

        Map<String, Object> etcdRaftConfig = (Map<String, Object>) orderConfig.get("EtcdRaft");

        List<Map<String, Object>> consenters = (List<Map<String, Object>>) (etcdRaftConfig).get("Consenters");
        consenters.clear();
        consenters.addAll(this.generateOrdersConfig(initConfig));

        organizations = (List<Map<Object, Object>>) orderConfig.get("Organizations");
        organizations.clear();
        organizations.add(ordererOrg);

        Map<Object, Object> capabilities = (Map<Object, Object>) orderConfig.get("Capabilities");

        orderAddresses = (List<String>) orderConfig.get("Addresses");
        orderAddresses.clear();
        for (String host : initConfig.getOrdererHostConfig().keySet()) {
            String ip = initConfig.getOrdererHostConfig().get(host);
            orderAddresses.add(host + ip.substring(ip.lastIndexOf(":")));
        }
        orderConfig.clear();
        orderConfig.putAll(orderer);
        orderConfig.put("OrdererType", "etcdraft");
        orderConfig.put("EtcdRaft", etcdRaftConfig);
        orderConfig.put("Addresses", orderAddresses);
        orderConfig.put("Organizations", organizations);
        orderConfig.put("Capabilities", capabilities);

        Map<Object, Object> application = (Map<Object, Object>) profile.get("Application");
        organizations = (List<Map<Object, Object>>) application.get("Organizations");
        organizations.clear();
        organizations.add(ordererOrg);

        Map<Object, Object> sampleConsortium = (Map<Object, Object>) ((Map<Object, Object>) profile.get("Consortiums")).get("SampleConsortium");
        organizations = (List<Map<Object, Object>>) sampleConsortium.get("Organizations");
        organizations.clear();
        organizations.add(org1);

        // PrivateChannel
        profile = (Map<Object, Object>) ((Map<Object, Object>) configTxEntity.get("Profiles")).get("PrivateChannel");
        application = (Map<Object, Object>) profile.get("Application");
        organizations = (List<Map<Object, Object>>) application.get("Organizations");
        organizations.clear();
        organizations.add(org1);

        // OneOrgChannel
        profile = (Map<Object, Object>) ((Map<Object, Object>) configTxEntity.get("Profiles")).get("OneOrgChannel");
        application = (Map<Object, Object>) profile.get("Application");
        organizations = (List<Map<Object, Object>>) application.get("Organizations");
        organizations.clear();
        organizations.add(org1);

        return writeYamlFile(configTxEntity, CONFIGTX);
    }

    /**
     * 将配置信息写入configtx.yaml文件
     *
     * @param configTxEntity
     * @return
     */
    private boolean writeYamlFile(Map<Object, Object> configTxEntity, String fileName) {
        String yamlFile = null;
        if (fileName.equals(CONFIGTX)) {
            yamlFile = modeService.getInitDir() + "fabric-net/cryptoAndConfig/configtx.yaml";
        }
        if (fileName.equals(CRYPTO)) {
            yamlFile = modeService.getInitDir() + "fabric-net/cryptoAndConfig/crypto-config.yaml";
        }
        if (StringUtils.isEmpty(yamlFile)) {
            return false;
        }

        return yamlFileService.writeObjectToYamlFile(configTxEntity, yamlFile);
    }

    /**
     * 加载指定文件名称的文件模板
     *
     * @param fileName
     * @return
     */
    private Map<Object, Object> loadTemplate(String fileName) {
        String txTempFile = null;
        if (fileName.equals(CONFIGTX)) {
            txTempFile = modeService.getInitDir() + "template/configtx.yaml";
        }
        if (fileName.equals(CRYPTO)) {
            txTempFile = modeService.getInitDir() + "template/crypto-config.yaml";
        }
        if (StringUtils.isEmpty(txTempFile) || StringUtils.isEmpty(fileName)) {
            return null;
        }
        Map<Object, Object> result = null;
        try {
            result = yamlFileService.loadYamlFile(txTempFile);
        } catch (FileNotFoundException e) {
            log.error("找不到指定文件,文件路径:" + txTempFile);
            e.printStackTrace();
        }
        return result;
    }

    private List<Map<String, Object>> generateOrdersConfig(InitConfigEntity configEntity) {
        String tlsFormat = "crypto-config/ordererOrganizations/%s/orderers/%s/tls/server.crt";
        List<Map<String, Object>> config = new ArrayList<>();
        for (String host : configEntity.getOrdererHostConfig().keySet()) {
            String ip = configEntity.getOrdererHostConfig().get(host);
            int index = ip.lastIndexOf(":");

            Map<String, Object> orderConfig = new HashMap<>();
            orderConfig.put("Host", host);
            orderConfig.put("Port", Integer.parseInt(ip.substring(index + 1)));
            String tls = String.format(tlsFormat, configEntity.getOrdererDomain(), host);
            orderConfig.put("ClientTLSCert", tls);
            orderConfig.put("ServerTLSCert", tls);
            config.add(orderConfig);
        }
        return config;
    }

    @Override
    public boolean cryptoGen(InitConfigEntity initConfig) {
        Map<Object, Object> cryptoConfig = this.loadTemplate(CRYPTO);
        List<Map<Object, Object>> ordererOrgs = (List<Map<Object, Object>>) cryptoConfig.get("OrdererOrgs");
        ordererOrgs.get(0).put("Domain", initConfig.getOrdererDomain());
        List<Map<Object, Object>> specs = (List<Map<Object, Object>>) ordererOrgs.get(0).get("Specs");
        specs.clear();
        for (String orderHost : initConfig.getOrdererHostConfig().keySet()) {
            int index = orderHost.indexOf(".");
            specs.add(new HashMap<Object, Object>() {{
                put("Hostname", orderHost.substring(0, index));
            }});
        }

        List<Map<Object, Object>> peerOrgs = (List<Map<Object, Object>>) cryptoConfig.get("PeerOrgs");
        peerOrgs.get(0).put("Name", initConfig.getOrgMSPID().replace("MSP", ""));
        peerOrgs.get(0).put("Domain", initConfig.getPeerDomain());

        List<Map<Object, Object>> peerSpecs = (List<Map<Object, Object>>) peerOrgs.get(0).get("Specs");
        peerSpecs.clear();
        for (String peerHost : initConfig.getPeerHostConfig().keySet()) {
            String peerHostName = peerHost.split("." + initConfig.getPeerDomain())[0];
            peerSpecs.add(new HashMap<Object, Object>() {{
                put("Hostname", peerHostName);
            }});
        }
        return writeYamlFile(cryptoConfig, CRYPTO);
    }

}
