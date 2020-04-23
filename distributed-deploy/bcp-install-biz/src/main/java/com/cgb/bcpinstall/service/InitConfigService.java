package com.cgb.bcpinstall.service;

import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Map;

/**
 * 配置信息服务类
 *
 * @author zheng.li
 * @date 2020/3/9 10:26
 */
@Slf4j
@Service
public class InitConfigService {

    /**
     * 根据配置文件路径获取配置信息
     *
     * @param filePath
     * @return
     * @throws FileNotFoundException
     */
    public InitConfigEntity parseConfigFile(String filePath) throws FileNotFoundException {
        File file = new File(filePath);
        if (!file.exists() && !file.isFile()) {
            throw new FileNotFoundException(filePath);
        }
        InitConfigEntity initConfigEntity = null;
        Yaml yaml = new Yaml();
        try {
            initConfigEntity = yaml.loadAs(new FileInputStream(file), InitConfigEntity.class);
        } catch (Exception e) {
            log.error("配置文件读取异常，请检查各配置项是否符合格式要求");
        }
        return initConfigEntity;
    }

    public boolean isCorrectConfig(InitConfigEntity initConfigEntity) {
        boolean isCorrect = true;

        if (!isNotEmptyConfig(initConfigEntity)) {
            log.error("配置文件中相关配置项为空");
            isCorrect = false;
        }

        if (!isCorrectFormat(initConfigEntity)) {
            log.error("配置文件中相关域名的ip和端口格式不正确");
            isCorrect = false;
        }
        if (!isCorrectDomain(initConfigEntity)) {
            log.error("配置文件中orderer或peer的域名不匹配");
            isCorrect = false;
        }
        if (!checkPeerConfig(initConfigEntity)) {
            log.error("配置文件中peer没有配置相应的交易查询端口");
            isCorrect = false;
        }

        return isCorrect;
    }

    /**
     * 检查orderer与peer域名是否匹配
     *
     * @param initConfigEntity
     * @return
     */
    private boolean isCorrectDomain(InitConfigEntity initConfigEntity) {
        String ordererDomain = initConfigEntity.getOrdererDomain();
        String peerDomain = initConfigEntity.getPeerDomain();
        boolean peerMatch = initConfigEntity.getPeerHostConfig().keySet().stream().allMatch(i -> i.endsWith(peerDomain));
        boolean ordererMatch = initConfigEntity.getOrdererHostConfig().keySet().stream().allMatch(i -> i.endsWith(ordererDomain));
        return peerMatch && ordererMatch;
    }

    private boolean checkPeerConfig(InitConfigEntity configEntity) {
        boolean isCorrect = true;
        Map<String, String> peerConfigMap = configEntity.getPeerHostConfig();
        Map<String, String> peerMetricConfigMap = configEntity.getMetricPortConfig();
        for (String host : peerConfigMap.keySet()) {
            if (peerMetricConfigMap.keySet().stream().noneMatch(i -> i.equals(host))) {
                isCorrect = false;
            }
        }
        return isCorrect;
    }

    private boolean isNotEmptyConfig(InitConfigEntity configEntity) {
        boolean isCorrect = true;
        if (StringUtils.isEmpty(configEntity.getNetwork())
                || StringUtils.isEmpty(configEntity.getChannelName())
                || StringUtils.isEmpty(configEntity.getOrgMSPID())
                || StringUtils.isEmpty(configEntity.getOrgName())
                || StringUtils.isEmpty(configEntity.getOrdererDomain())
                || StringUtils.isEmpty(configEntity.getPeerDomain())) {
            isCorrect = false;
        }
        if (CollectionUtils.isEmpty(configEntity.getOrdererHostConfig())
                || CollectionUtils.isEmpty(configEntity.getPeerHostConfig())
                || CollectionUtils.isEmpty(configEntity.getMetricPortConfig())) {
            isCorrect = false;
        }

        if (configEntity.getOrdererHostConfig().entrySet().stream().anyMatch(i -> StringUtils.isEmpty(i.getValue()))) {
            isCorrect = false;
        }

        if (configEntity.getPeerHostConfig().entrySet().stream().anyMatch(i -> StringUtils.isEmpty(i.getValue()))) {
            isCorrect = false;
        }

        if (configEntity.getMetricPortConfig().entrySet().stream().anyMatch(i -> StringUtils.isEmpty(i.getValue()))) {
            isCorrect = false;
        }

        return isCorrect;
    }

    private boolean isCorrectFormat(InitConfigEntity configEntity) {
        Collection<String> peerIpPorts = configEntity.getPeerHostConfig().values();
        if (!isCorrectFormat(peerIpPorts)) {
            return false;
        }
        Collection<String> ordererIpPorts = configEntity.getOrdererHostConfig().values();
        return isCorrectFormat(ordererIpPorts);
    }

    private boolean isCorrectFormat(Collection<String> ipPorts) {
        for (String ipPort : ipPorts) {
            if (ipPort.split(":").length != 2) {
                return false;
            }
        }
        return true;
    }
}
