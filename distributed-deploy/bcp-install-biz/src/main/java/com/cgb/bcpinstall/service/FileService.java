package com.cgb.bcpinstall.service;

import com.alibaba.fastjson.JSON;
import com.cgb.bcpinstall.common.entity.OSEnum;
import com.cgb.bcpinstall.common.entity.RoleEnum;
import com.cgb.bcpinstall.common.entity.UpdateReasonEnum;
import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.util.FileUtil;
import com.cgb.bcpinstall.common.util.NetUtil;
import com.cgb.bcpinstall.common.util.Utils;
import com.cgb.bcpinstall.config.GlobalConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 文件服务类
 *
 * @author zheng.li
 * @date 2020/3/2 14:54
 */
@Service
@Slf4j
public class FileService {
    private static final String CERTS_FOLDER_NAME = "crypto-config";

    @Autowired
    private ModeService modeService;

    @Autowired
    protected GlobalConfig globalConfig;

    @Value("${init.config}")
    protected String initConfigFile;

    /**
     * 将需要的文件复制到安装目录
     *
     * @param ipList
     * @param roleList
     * @return
     */
    public void copyInstallFiles(List<String> ipList, List<RoleEnum> roleList, InitConfigEntity configEntity) {
        // 如果master节点不担任任何角色，则返回空
        if (roleList.isEmpty()) {
            return;
        }
        for (RoleEnum role : roleList) {
            for (String ip : ipList) {
                copyFiles(role, ip, modeService.getInstallPath(), configEntity);
            }
        }
    }

    public void copyFiles(RoleEnum role, String ip, String destPath, InitConfigEntity configEntity) {
        copyFiles(role, ip, null, destPath, null, configEntity, null);
    }

    /**
     * 将相应角色的文件复制到指定路径
     *
     * @param role
     * @param ip
     * @param srcFolderName
     * @param destPath
     */
    public void copyFiles(RoleEnum role, String ip, String srcFolderName, String destPath, String destFolderName, InitConfigEntity configEntity, List<String> hostArray) {
        FileUtil.makeFilePath(destPath, false);
        String srcRootPath = FileUtil.reviseDir(modeService.getInitDir());

        try {
            String dockerSrcFile = srcRootPath + "fabric-net" + File.separator + "dockerFile" + File.separator;
            if (!destPath.endsWith(File.separator)) {
                destPath = destPath + File.separator;
            }
            copyCertFiles(role, ip, destPath, configEntity);
            switch (role) {
                case ORDER:
                    String orderDir = dockerSrcFile + (StringUtils.isEmpty(srcFolderName) ? "order-" + ip : srcFolderName);
                    if (!new File(orderDir).exists()) {
                        log.info(String.format("目录 %s 不存在", orderDir));
                        break;
                    }
                    FileUtils.copyDirectory(new File(orderDir), new File(destPath + (StringUtils.isEmpty(destFolderName) ? "order" : destFolderName)));

                    // 需要复制创世块
                    if (NetUtil.getLocalIPList().stream().noneMatch(i -> i.equalsIgnoreCase(ip))) {
                        FileUtils.copyDirectory(new File(modeService.getInstallPath() + "channel-artifacts"), new File(destPath + "channel-artifacts"));
                    }
                    break;
                case PEER:
                    String peerDir = dockerSrcFile + (StringUtils.isEmpty(srcFolderName) ? "peer-" + ip : srcFolderName);
                    if (!new File(peerDir).exists()) {
                        log.info(String.format("目录 %s 不存在", peerDir));
                        break;
                    }
                    FileUtils.copyDirectory(new File(peerDir), new File(destPath + (StringUtils.isEmpty(destFolderName) ? "peer" : destFolderName)));
                    break;
            }
        } catch (Exception e) {
            log.error(String.format("为 %s 复制 %s 角色文件失败", ip, role.name()), e);
            e.printStackTrace();
        }
    }

    /**
     * 复制相应的证书文件
     *
     * @param role
     * @param ip
     * @param destPath
     */
    public void copyCertFiles(RoleEnum role, String ip, String destPath, InitConfigEntity configEntity) {
        log.info(String.format("为服务器 %s 角色 %s 复制证书文件, 目标目录: %s", ip, role.name(), destPath));

        if (!destPath.endsWith(File.separator)) {
            destPath = destPath + File.separator;
        }

        String certRootPath = destPath + CERTS_FOLDER_NAME + File.separator;
        FileUtil.makeFilePath(certRootPath, false);

        String midDirName01;
        String midDirName02;
        Map<String, String> hostConfig;
        if (role == RoleEnum.ORDER) {
            hostConfig = configEntity.getOrdererHostConfig();
            midDirName01 = "ordererOrganizations";
            midDirName02 = "orderers";
        } else {
            hostConfig = configEntity.getPeerHostConfig();
            midDirName01 = "peerOrganizations";
            midDirName02 = "peers";
        }

        for (String domain : hostConfig.keySet()) {
            String val = hostConfig.get(domain);
            int index = val.lastIndexOf(":");
            if (val.substring(0, index).endsWith(ip)) {
                int i = domain.indexOf(".");
                String relativePath = midDirName01 + File.separator + domain.substring(i + 1)
                        + File.separator + midDirName02 + File.separator + domain;

                String srcDir = modeService.getInitDir() + "fabric-net" + File.separator + "cryptoAndConfig" + File.separator + "crypto-config"
                        + File.separator + relativePath;
                try {
                    FileUtils.copyDirectory(new File(srcDir), new File(certRootPath + relativePath));
                } catch (IOException e) {
                    log.error(String.format("复制%s证书文件异常", role.name()), e);
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * 将证书文件复制到主节点
     */
    public void masterCopyCryptoConfig() {
        String srcDir = modeService.getInitDir() + "fabric-net" + File.separator + "cryptoAndConfig" + File.separator + "crypto-config"
                + File.separator;
        try {
            FileUtils.copyDirectory(new File(srcDir), new File(modeService.getInstallPath() + "crypto-config"));
        } catch (IOException e) {
            log.error("主节点复制证书文件异常", e);
            e.printStackTrace();
        }
    }

    /**
     * 将configtx.yaml文件复制到主节点
     */
    public void masterCopyConfigtxFile() {
        log.info("复制 configtx.yaml 文件到安装目录");
        FileUtil.makeFilePath(modeService.getInstallPath(), false);

        String srcRootPath = FileUtil.reviseDir(modeService.getInitDir());
        String srcFile = srcRootPath + "fabric-net" + File.separator + "cryptoAndConfig" + File.separator + "configtx.yaml";
        try {
            log.info("生成创世快-从" + srcFile + "复制到" + modeService.getInstallPath() + "configtx.yaml");
            FileUtils.copyFile(new File(srcFile), new File(modeService.getInstallPath() + "configtx.yaml"));
        } catch (IOException e) {
            log.error("复制 configtx.yaml 文件到安装目录异常", e);
            e.printStackTrace();
        }
    }

    /**
     * 为机器打包智能安装包
     *
     * @param serverAddress 机器IP地址
     * @param roleList      所充当的角色
     * @return
     */
    public String packInstallFiles(String serverAddress, List<RoleEnum> roleList, InitConfigEntity configEntity) {
        log.info(String.format("为服务器 %s 准备智能安装包, 该服务器承担的角色：%s", serverAddress, roleList.stream().map(Enum::name).collect(Collectors.joining(","))));

        if (configEntity == null) {
            try {
                Yaml yaml = new Yaml();
                configEntity = yaml.loadAs(new FileInputStream(new File(this.initConfigFile)), InitConfigEntity.class);
            } catch (Exception e) {
                log.error("安装过程发生异常", e);
                e.printStackTrace();
            }
        }

        // 建一个临时目录
        String tmpPath = System.getProperty("java.io.tmpdir");
        if (!tmpPath.endsWith(File.separator)) {
            tmpPath = tmpPath + File.separator;
        }
        String rootPath = tmpPath + UUID.randomUUID().toString().replaceAll("-", "") + File.separator;
        String packSrcPath = rootPath + "installFiles" + File.separator;
        FileUtil.makeFilePath(packSrcPath, true);

        for (RoleEnum role : roleList) {
            this.copyFiles(role, serverAddress, packSrcPath, configEntity);
        }

        // 打包
        try {
            String packFilePath = rootPath + "InstallPackage.tar.gz";

            byte[] data = Utils.generateTarGz(new File(packSrcPath), "", null);
            FileOutputStream os = new FileOutputStream(packFilePath, false);
            os.write(data);
            os.close();

            FileUtil.rmFile(new File(packSrcPath));

            return packFilePath;
        } catch (IOException e) {
            log.error("生成智能安装包时异常", e);
            e.printStackTrace();
        }

        return "";
    }

    /**
     * 为扩容节点打包所需安装文件
     *
     * @param serverAddress
     * @param folderName
     * @param roleEnum
     * @param configEntity
     * @return
     */
    public String packExtendNodeFiles(String serverAddress, String folderName, RoleEnum roleEnum, InitConfigEntity configEntity) {
        String role = "";
        switch (roleEnum) {
            case PEER:
                role = "peer";
                break;
            case ORDER:
                role = "orderer";
                break;
        }
        log.info(String.format("为新 %s 节点 %s 准备智能安装包", role, serverAddress));

        // 建一个临时目录
        String tmpPath = System.getProperty("java.io.tmpdir");
        if (!tmpPath.endsWith(File.separator)) {
            tmpPath = tmpPath + File.separator;
        }

        String rootPath = tmpPath + UUID.randomUUID().toString().replaceAll("-", "") + File.separator;
        String packSrcPath = rootPath + "installFiles" + File.separator;
        FileUtil.makeFilePath(packSrcPath, true);

        this.copyFiles(roleEnum, serverAddress, folderName, packSrcPath, folderName, configEntity, null);

        // 打包
        try {
            String packFilePath = rootPath + "InstallPackage.tar.gz";

            byte[] data = Utils.generateTarGz(new File(packSrcPath), "", null);
            FileOutputStream os = new FileOutputStream(packFilePath, false);
            os.write(data);
            os.close();

            FileUtil.rmFile(new File(packSrcPath));

            return packFilePath;
        } catch (IOException e) {
            log.error("生成智能安装包时异常", e);
            e.printStackTrace();
        }

        return "";
    }

    /**
     * 移除证书
     *
     * @param role
     * @param configEntity
     * @param nodeHostMap
     * @param isInitDir
     */
    public void removeCertFile(RoleEnum role, InitConfigEntity configEntity, Map<String, String> nodeHostMap, boolean isInitDir) {
        for (String nodeHost : nodeHostMap.keySet()) {
            String cryptoFilePath = "crypto-config" + File.separator + "%s" + File.separator + "%s" + File.separator + "%s" + File.separator + "%s";
            String peerCertPath = String.format(cryptoFilePath, "peerOrganizations", configEntity.getPeerDomain(), "peers", nodeHost);
            String ordererCertPath = String.format(cryptoFilePath, "ordererOrganizations", configEntity.getOrdererDomain(), "orderers", nodeHost);
            String rmPath;

            if (role == RoleEnum.PEER) {
                if (isInitDir) {
                    rmPath = modeService.getInitDir() + "fabric-net" + File.separator + "cryptoAndConfig" + File.separator + peerCertPath;
                } else {
                    rmPath = modeService.getInstallPath() + peerCertPath;
                }
            } else {
                if (isInitDir) {
                    rmPath = modeService.getInitDir() + "fabric-net" + File.separator + "cryptoAndConfig" + File.separator + ordererCertPath;
                } else {
                    rmPath = modeService.getInstallPath() + ordererCertPath;
                }
            }
            log.info("缩容节点，移除证书路径：" + rmPath);
            FileUtil.rmFile(new File(rmPath));
        }
    }
}
