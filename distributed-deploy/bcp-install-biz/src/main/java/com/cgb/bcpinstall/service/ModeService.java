package com.cgb.bcpinstall.service;

import com.cgb.bcpinstall.biz.RolesBiz;
import com.cgb.bcpinstall.common.entity.*;
import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.response.HttpInstallResponse;
import com.cgb.bcpinstall.common.response.ResponseCode;
import com.cgb.bcpinstall.common.util.*;
import com.cgb.bcpinstall.db.CheckPointDb;
import com.cgb.bcpinstall.db.table.NodeDO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.sql.SQLException;
import java.util.*;

/**
 * @author zheng.li
 * @date 2020/2/3 14:26
 */
@Service
@Slf4j
public class ModeService {

    @Autowired
    protected RolesBiz rolesBiz;

    @Autowired
    protected CheckPointDb checkPointDb;

    @Value("${init.config}")
    protected String initConfigFile;

    @Value("${init.dir}")
    protected String initDir;

    @Value("${install.path}")
    protected String installPath;

    public HttpInstallResponse createFailedHttpResponse() {
        HttpInstallResponse response = new HttpInstallResponse();
        response.setCode(ResponseCode.Fail.getCode());
        return response;
    }


    /**
     * @return true -- 初始化完成
     */
    public boolean checkBackendInitFinished(String host, String shPath) {
        String logFilePath = this.getInstallPath() + "bcp-app-mgr-" + host + File.separator + "log.out";
        try {
            ProcessUtil.Result result = ProcessUtil.execCmd("sh ./fetchBackendInit.sh " + logFilePath, null, shPath);
            log.info(StringUtils.isEmpty(result.getData()) ? "后台初始化中，请耐心等待............" : "后台初始化成功");
            return !StringUtils.isEmpty(result.getData());
        } catch (Exception e) {
            log.error("查询管理后台日志异常", e);
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 更新从节点安装结果
     *
     * @param remoteAddress
     * @param result
     */
    public void updateInstallResult(String remoteAddress, InstallResult result, InitConfigEntity configEntity) {
        if (configEntity == null) {
            File configFile = new File(this.initConfigFile);
            Yaml yaml = new Yaml();
            try {
                configEntity = yaml.loadAs(new FileInputStream(configFile), InitConfigEntity.class);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }
        if (result.isSuccess()) {
            log.info(String.format("节点 %s 已完成 %s 角色的安装", remoteAddress, result.getRole().name()));

            // 加入数据库
            switch (result.getRole()) {
                case ORDER:
                    checkAndInsertDb(configEntity.getOrdererHostConfig(), remoteAddress, RoleEnum.ORDER, configEntity.getOrgMSPID());
                    break;
                case PEER:
                    checkAndInsertDb(configEntity.getPeerHostConfig(), remoteAddress, RoleEnum.PEER, configEntity.getOrgMSPID());
                    break;
            }
        }
        this.rolesBiz.updateInstallResult(remoteAddress, result);
    }

    public void checkAndInsertDb(Map<String, String> hostConfig, String ip, RoleEnum role, String orgMspId) {
        for (String host : hostConfig.keySet()) {
            String hIp = hostConfig.get(host);
            int index = hIp.lastIndexOf(":");
            String port = hIp.substring(index + 1);
            hIp = hIp.substring(0, index);

            if (hIp.equalsIgnoreCase(ip)) {
                NodeDO nodeDO = new NodeDO();
                nodeDO.setOrgMspId(orgMspId);
                nodeDO.setRole(role);
                nodeDO.setHostName(host);
                nodeDO.setIp(ip);
                nodeDO.setPort(Integer.parseInt(port));
                nodeDO.setStatus(InstallStatusEnum.SUCCESS);
                try {
                    this.checkPointDb.addNodeRecord(nodeDO);
                } catch (SQLException e) {
                    log.error(String.format("添加节点 %s 角色 %s 到数据库失败", ip, role.name().toLowerCase()), e);
                    e.printStackTrace();
                }
            }
        }
    }

    public String getInitDir() {
        return this.initDir.endsWith(File.separator) ? this.initDir : this.initDir + File.separator;
    }

    public String getInstallPath() {
        String installPath = this.installPath.endsWith(File.separator) ? this.installPath : this.installPath + File.separator;
        return installPath;
    }
}
