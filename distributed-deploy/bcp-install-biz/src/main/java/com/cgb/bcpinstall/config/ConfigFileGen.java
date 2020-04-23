package com.cgb.bcpinstall.config;

import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.util.CacheUtil;
import com.cgb.bcpinstall.common.util.ProcessUtil;
import com.cgb.bcpinstall.service.ModeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author zheng.li
 * @date 2020/3/12 15:04
 */
@Component
@Slf4j
public class ConfigFileGen {

    @Autowired
    private ModeService modeService;

    @Autowired
    private DockerConfigGen dockerConfigGen;

    @Autowired
    private FabricConfigGen fabricConfigGen;


    public void createConfigFile(InitConfigEntity config) {

        // 生成 configtx.yaml 文件,其它机构在profile中只有PrivateChannel配置，需在子类具体实现
        if (fabricConfigGen.configTxGen(config)) {
            log.info("生成 configtx.yaml 文件成功");
        } else {
            log.error("生成 configtx.yaml 文件失败");
        }

        // 生成 crypto-config.yaml 文件
        if (fabricConfigGen.cryptoGen(config)) {
            log.info("生成 crypto-config.yaml 文件成功");
        } else {
            log.error("生成 crypto-config.yaml 文件失败");
        }

        // 创建 crypto-config 目录和证书
        if (createNewCerts()) {
            log.info("创建证书成功");
        } else {
            log.error("创建证书失败");
        }

        // 生成 orderer docker compose yaml 文件
        if (dockerConfigGen.ordererComposeFileGen(config)) {
            log.info("生成 docker-compose-orderer.yaml 文件成功");
        } else {
            log.error("生成 docker-compose-orderer.yaml 文件失败");
        }

        // 生成 peer docker compose yaml 文件,本配置文件不区分发起机构与其他机构，可具体实现
        if (dockerConfigGen.peerComposeFileGen(config)) {
            log.info("生成 docker-compose-peer.yaml 文件成功");
        } else {
            log.error("生成 docker-compose-peer.yaml 文件失败");
        }
    }

    /**
     * 根据crypto-config生成新证书文件
     *
     * @return
     */
    public boolean createNewCerts() {
        return createCerts("up");
    }

    /**
     * 根据crypto-config在已存在证书基础下生成新节点证书文件
     *
     * @return
     */
    public boolean createExtendCerts() {
        return createCerts("extend");
    }

    /**
     * 根据mode生成证书
     *
     * @param mode
     * @return
     */
    private boolean createCerts(String mode) {
        String workingDir = modeService.getInitDir() + "fabric-net/cryptoAndConfig";
        String shellFilePath = modeService.getInitDir() + "generate.sh";
        try {
            ProcessUtil.Result result = ProcessUtil.execCmd("bash " + shellFilePath + " " + mode, new String[]{"CRYPTTOGEN_FILE_PATH" + "=" + CacheUtil.getCryptogenFilePath()}, workingDir);
            if (result.getCode() == 0) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }
}
