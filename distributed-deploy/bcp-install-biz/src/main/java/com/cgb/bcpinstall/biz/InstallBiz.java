package com.cgb.bcpinstall.biz;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cgb.bcpinstall.common.entity.*;
import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.response.HttpInstallResponse;
import com.cgb.bcpinstall.common.response.ResponseCode;
import com.cgb.bcpinstall.common.util.*;
import com.cgb.bcpinstall.config.GlobalConfig;
import com.cgb.bcpinstall.db.CheckPointDb;
import com.cgb.bcpinstall.service.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import javax.servlet.http.Part;
import java.io.*;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
public class InstallBiz {

    private static final String INSTALL_PACKAGE_FILE_NAME = "InstallPackage.tar.gz";

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private InstallService installService;

    @Autowired
    private InitConfigService initConfigService;

    @Autowired
    private GlobalConfig globalConfig;

    @Autowired
    private HttpClientUtil httpClient;

    @Autowired
    private CheckPointDb checkPointDb;

    @Autowired
    private ModeService modeService;

    @Value("${init.config}")
    private String initConfigFile;

    @Value("${install.mode}")
    private String installMode;

    @Value("${server.port}")
    private String httpServerPort;

    private String masterServer;

    private InitConfigEntity configEntity;

    private AtomicBoolean finished = new AtomicBoolean(false);

    private Thread task;

    public void start() {
        this.task = new Thread(this::doInstallThread);
        this.task.start();
    }

    public String getInstallPackageFilePath() {
        FileUtil.makeFilePath(modeService.getInstallPath(), false);
        return modeService.getInstallPath() + INSTALL_PACKAGE_FILE_NAME;
    }

    public void setMasterServer(String server) {
        this.masterServer = server;
    }

    /**
     * 安装线程的主函数
     */
    private void doInstallThread() {
        this.initConfigFile = FileUtil.reviseDir(this.initConfigFile);
        // http 服务端口添加防火墙
        environmentService.addPortIntoFirewall(this.httpServerPort);
        // 主节点
        if (this.globalConfig.getMaster() == 1) {
            File configFile = new File(this.initConfigFile);
            if (!configFile.exists() || !configFile.isFile()) {
                log.error(String.format("initconfig.propertise(传入的路径：%s)文件不存在, 请按任意键结束，重新运行", this.initConfigFile));
                this.finished.set(true);
                return;
            }

            try {
                this.configEntity = initConfigService.parseConfigFile(this.initConfigFile);
                if (configEntity == null) {
                    log.error("解析初始化配置文件失败");
                    this.doEnd();
                    return;
                }
                //校验配置信息格式
                if (!initConfigService.isCorrectConfig(configEntity)) {
                    this.doEnd();
                    return;
                }
                if (this.configEntity.getOrdererHostConfig().size() < 3) {
                    log.error("orderer 节点不能少于3个，请重新编辑配置文件，结束安装");
                    this.doEnd();
                    return;
                }
                if (this.configEntity.getPeerHostConfig().size() < 2) {
                    log.error("peer 节点不能少于2个，请重新编辑配置文件，结束安装");
                    this.doEnd();
                    return;
                }
                InstallMode installModeAction = null;
                if ("newInstall".equalsIgnoreCase(this.installMode)) {
                    installModeAction = (InstallMode) SpringUtil.getBean("newInstallBiz");
                } else {
                    if (checkModifyInstall()) {
                        installModeAction = (InstallMode) SpringUtil.getBean("updateNetworkBiz");
                    } else {
                        System.out.println("在配置文件中没有找到需要更新的信息");
                    }
                }
                if (installModeAction != null) {
                    installModeAction.run(configEntity);
                    this.doEnd();
                }

            } catch (Exception e) {
                log.error("安装过程发生异常", e);
                e.printStackTrace();
            }
        }
    }

    /**
     * 根据现有安装的记录计算增量内容
     *
     * @return true 为增量/减量安装，false 为全新安装
     */
    private boolean checkModifyInstall() {
        try {
            return !this.checkPointDb.nodesTableEmpty();
        } catch (SQLException e) {
            log.error("查询本地数据库失败", e);
            e.printStackTrace();
        }

        return false;
    }

    /**
     * 启动从节点安装线程
     *
     * @param role
     */
    public void slaveInstall(RoleEnum role, List<String> ports, Map<String, String> hosts, String roleFolderName) {
        new Thread(() -> {
            log.info(String.format("从节点开始进行角色 %s 的安装", role.name()));
            if (this.startInstall(role, ports, hosts, roleFolderName)) {
                InstallResult result = new InstallResult();
                result.setRole(role);
                result.setSuccess(true);
                try {
                    do {
                        String res = httpClient.postJson(this.masterServer + "/v1/install/finished", JSONObject.toJSONString(result), false);
                        if (!StringUtils.isEmpty(res)) {
                            HttpInstallResponse response = JSONObject.parseObject(res, HttpInstallResponse.class);
                            if (ResponseCode.SUCCESS.getCode().equals(response.getCode())) {
                                log.info(String.format("向主节点报告安装 %s 状态成功", role.name()));
                                break;
                            }
                        }
                        log.info(String.format("向主节点报告安装 %s 状态失败，稍后重试...", role.name()));
                        try {
                            Thread.sleep(5000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    } while (true);
                } catch (Exception e) {
                    log.error("向主服务器发送安装完成状态异常", e);
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private boolean startInstall(RoleEnum role, List<String> rolePorts, Map<String, String> hosts, String roleFolderName) {
        return installService.startRole(role, rolePorts, hosts, roleFolderName);
    }

    public boolean isFinished() {
        return this.finished.get();
    }

    public void installPackageReady() {
        String packageFilePath = modeService.getInstallPath() + INSTALL_PACKAGE_FILE_NAME;
        FileUtil.tarGzDecompress(packageFilePath, modeService.getInstallPath(), false);
    }

    /**
     * 修改 orderer 容器配置后重启 docker，由 InstallController 调用
     *
     * @param updateCmd
     */
    public void updateOrderers(UpdateCmd updateCmd) {
        for (String orderName : updateCmd.getPeerHostConfig().keySet()) {
            String ipPort = updateCmd.getPeerHostConfig().get(orderName);
            String ip = ipPort.substring(0, ipPort.lastIndexOf(":"));
            if ("127.0.0.1".equals(ip)) {
                ip = NetUtil.getMyNormalIP();
            }
            updateOrderConfig(orderName, ip, updateCmd.getCurrentHost());
        }
    }

    private void updateOrderConfig(String newOrderName, String ip, String currentOrdererName) {

        try {
            ProcessUtil.Result result = ProcessUtil.execCmd("docker exec " + currentOrdererName + " bash updateOrdererHost.sh " + newOrderName + " " + ip, null, modeService.getInstallPath());
            if (result.getCode() == 0) {
                log.info(String.format("更新 docker 容器 %s 的 Hosts 成功: ", currentOrdererName));
            } else {
                log.error(String.format("更新 docker 容器 %s 的 Hosts 失败: " + result.getData(), currentOrdererName));
            }
        } catch (Exception e) {
            log.error(String.format("更新 docker 容器 %s 的 HostsPath 异常", currentOrdererName), e);
            e.printStackTrace();
        }
    }


    public void handleUpdate(UpdateCmd cmd, Part part) {
        try {
            if (cmd.getRole() == RoleEnum.ORDER) {
                String filePath = "/var/run/updateOrdererHost.sh";
                File updateHostFile = new File(filePath);
                if (updateHostFile.exists()) {
                    updateHostFile.delete();
                }
                part.write(filePath);
                this.updateOrderers(cmd);
            }
            this.doEnd();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doEnd() {
        this.finished.set(true);
        System.out.println("*****安装服务已完成*****");
    }

}
