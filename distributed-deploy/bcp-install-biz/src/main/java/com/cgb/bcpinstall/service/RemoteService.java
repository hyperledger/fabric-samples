package com.cgb.bcpinstall.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.cgb.bcpinstall.biz.RolesBiz;
import com.cgb.bcpinstall.common.entity.*;
import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.response.HttpInstallResponse;
import com.cgb.bcpinstall.common.response.ResponseCode;
import com.cgb.bcpinstall.common.util.HttpClientUtil;
import com.cgb.bcpinstall.common.util.NetUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;

/**
 * 远程调用服务类
 *
 * @author zheng.li
 * @date 2020/3/2 14:58
 */
@Service
@Slf4j
public class RemoteService {
    @Autowired
    private ModeService modeService;

    @Autowired
    private EnvironmentService environmentService;

    @Autowired
    private FileService fileService;

    @Autowired
    protected HttpClientUtil httpClient;

    @Autowired
    protected RolesBiz rolesBiz;


    /**
     * 主节点给从节点发送安装指令
     *
     * @param server
     * @param role
     * @return
     */
    public HttpInstallResponse sendInstallCommand(ServerEntity server, RoleEnum role, String folderName, InitConfigEntity configEntity) {
        log.info(String.format("主节点给从节点 %s 发送安装角色：%s", server.getHost(), role.name()));
        try {
            Map<String, String> revisedHosts = new HashMap<>();
            Map<String, String> hosts = environmentService.getRoleNeedSetHost(role, configEntity);
            for (String h : hosts.keySet()) {
                String ip = hosts.get(h);
                if ("127.0.0.1".equalsIgnoreCase(ip)) {
                    revisedHosts.put(h, NetUtil.getMyNormalIP());
                } else {
                    revisedHosts.put(h, ip);
                }
            }
            //设置从节点安装信息实例
            InstallCmd entity = new InstallCmd();
            entity.setHosts(revisedHosts);
            entity.setIpAddress(server.getHost());
            entity.setRole(role);
            entity.setRolePorts(server.getRolePorts());
            entity.setRoleFolderName(folderName);
            //发生安装指令
            String result = this.httpClient.sendFileAndJson(server.getHttpUrl() + "/v1/install/start", "", JSONObject.toJSONString(entity));
            if (result.isEmpty()) {
                log.error("注册角色返回结果为空");
            } else {
                log.info("注册角色返回结果: " + result);
                return JSON.parseObject(result, HttpInstallResponse.class);
            }
        } catch (Exception e) {
            log.error("发送安装指令异常", e);
            e.printStackTrace();
        }

        return modeService.createFailedHttpResponse();
    }

    /**
     * 向从节点推送安装包
     *
     * @param remoteAddr
     * @param configEntity
     */
    public void pushSlaveInstallPackage(String remoteAddr, InitConfigEntity configEntity) {
        pushSlaveInstallPackage(remoteAddr, null, configEntity);
    }

    public void pushSlaveInstallPackage(String remoteAddr, String packFilePath, InitConfigEntity configEntity) {
        List<RoleEnum> roleList = this.rolesBiz.getRole(remoteAddr);
        String filePath = StringUtils.isEmpty(packFilePath) ? fileService.packInstallFiles(remoteAddr, roleList, configEntity) : packFilePath;
        //重试次数初始化为1
        int retryCount = 1;
        //设置重试次数
        int retryTotal = 8;
        do {
            String result = httpClient.uploadFile("http://" + remoteAddr + ":8080/v1/install/pushPackage", filePath);
            if (!StringUtils.isEmpty(result)) {
                HttpInstallResponse response = JSONObject.parseObject(result, HttpInstallResponse.class);
                if (ResponseCode.SUCCESS.getCode().equals(response.getCode())) {
                    log.info(String.format("向从节点 %s 推送安装包成功", remoteAddr));
                    this.rolesBiz.setServerStatus(remoteAddr, InstallStatusEnum.DOWNLOADED);
                    break;
                }
            }
            if (retryCount == retryTotal) {
                break;
            }
            log.info(String.format("向从节点 %s 推送安装包失败，稍后重试...", remoteAddr));
            try {
                Thread.sleep(8000);
            } catch (Exception e) {
                e.printStackTrace();
            }
            retryCount++;
        } while (true);

    }

    /**
     * 通知节点安装完成结束
     *
     * @param serverUrl
     */
    public void notifyNodesToEnd(Set<String> serverUrl) {
        for (String url : serverUrl) {
            if (!url.endsWith("/")) {
                url = url + "/";
            }
            EndCmd cmd = new EndCmd();
            cmd.setSuccess(true);
            try {
                int retryCount = 1;
                int retryTotal = 7;
                do {
                    String result = this.httpClient.postJson(url + "v1/install/end", JSONObject.toJSONString(cmd), false);
                    if (!StringUtils.isEmpty(result)) {
                        HttpInstallResponse response = JSONObject.parseObject(result, HttpInstallResponse.class);
                        if (ResponseCode.SUCCESS.getCode().equals(response.getCode())) {
                            log.info(String.format("发送结束指令给 %s 成功", url));
                            break;
                        }
                    }
                    if (retryCount == retryTotal) {
                        log.info("发送结束指令超时");
                        break;
                    }
                    log.info(String.format("发送结束指令给 %s 失败，稍后重试...", url));
                    try {
                        Thread.sleep(5000);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    retryCount++;
                } while (true);
            } catch (IOException e) {
                log.error(String.format("发送结束指令给 %s 异常", url));
                e.printStackTrace();
            }
        }
    }
}
