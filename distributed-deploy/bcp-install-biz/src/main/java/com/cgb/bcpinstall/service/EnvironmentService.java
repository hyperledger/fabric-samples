package com.cgb.bcpinstall.service;

import com.alibaba.fastjson.JSONObject;
import com.cgb.bcpinstall.common.entity.RoleEnum;
import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.util.FileUtil;
import com.cgb.bcpinstall.common.util.ProcessUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 环境设置服务类
 *
 * @author zheng.li
 * @date 2020/3/2 14:56
 */
@Service
@Slf4j
public class EnvironmentService {

    public void parseHostConfig(Map<String, String> hostConfig, Map<String, String> initFileConfig) {
        for (String host : initFileConfig.keySet()) {
            String ip = initFileConfig.get(host);
            int index = ip.lastIndexOf(":");
            ip = ip.substring(0, index);

            hostConfig.put(host, ip);
        }
    }

    /**
     * 获取角色需要设置 hosts 文件的内容
     *
     * @param role
     * @return
     */
    public Map<String, String> getRoleNeedSetHost(RoleEnum role, InitConfigEntity configEntity) {
        Map<String, String> hosts = new HashMap<>();

        switch (role) {
            case PEER:
                parseHostConfig(hosts, configEntity.getPeerHostConfig());
                break;

            case ORDER:
                parseHostConfig(hosts, configEntity.getOrdererHostConfig());
                break;

            default:
                break;
        }
        return hosts;
    }

    /**
     * 1. host文件修改
     * 2. 端口加入防火墙
     *
     * @param ports
     * @param hosts
     */
    public void envSet(List<String> ports, Map<String, String> hosts) {
        boolean append = true;

        if (CollectionUtils.isEmpty(hosts)) {
            return;
        }

        log.info(String.format("写入 hosts 文件: %s", JSONObject.toJSONString(hosts)));

        try {
            String hostContent = FileUtil.getFileContent("/etc/hosts");
            log.info("写入 hosts 文件,hostContent=" + hostContent);

            String[] hostConfigArray = hostContent.split("\n");

            StringBuilder oldHostBuilder = new StringBuilder();
            StringBuilder newHostBuilder = new StringBuilder();
            for (String host : hosts.keySet()) {
                String ip = hosts.get(host);
                if (!hostContent.contains(host)) {
                    newHostBuilder.append("\n").append(ip).append(" ").append(host);
                } else {
                    for (int i = 0; i < hostConfigArray.length; i++) {
                        String ipHost = ip + " " + host;
                        if (hostConfigArray[i].contains(host) && !hostConfigArray[i].equals(ipHost)) {
                            hostConfigArray[i] = ipHost;
                            append = false;
                        }
                    }
                }
            }

            if (!append) {
                oldHostBuilder.append(String.join("\n", hostConfigArray));
                log.info("写入 hosts 文件,hostConfigArray.build=" + oldHostBuilder.toString());
            }
            oldHostBuilder.append(newHostBuilder);
            FileOutputStream fos = new FileOutputStream(new File("/etc/hosts"), append);
            fos.write(oldHostBuilder.toString().getBytes());
            fos.close();
        } catch (IOException e) {
            log.error("写入/etc/hosts文件异常", e);
            e.printStackTrace();
        }

        // 防火墙
        if (ports.stream().noneMatch("2375"::equals)) {
            ports.add("2375");
        }
        log.info(String.format("端口加入防火墙: %s", ports.stream().collect(Collectors.joining(","))));
        for (String port : ports) {
            addPortIntoFirewall(port);
        }
    }

    public void addPortIntoFirewall(String port) {
        boolean succ = false;
        try {
            String cmd = String.format("firewall-cmd --permanent --zone=public --add-port=%s/tcp", port);
            ProcessUtil.Result res = ProcessUtil.execCmd(cmd, null, "./");
            if (res.getCode() == 0) {
                ProcessUtil.execCmd("firewall-cmd --reload", null, "./");
                succ = true;
            }
        } catch (Exception e) {
            log.error("调用 firewall-cmd 添加端口异常", e);
            e.printStackTrace();
        }

        if (!succ) {
            try {
                String cmd = String.format("iptables -I INPUT -p tcp --dport %s -j ACCEPT", port);
                ProcessUtil.Result res = ProcessUtil.execCmd(cmd, null, "./");
                if (res.getCode() == 0) {
                    ProcessUtil.execCmd("service iptables save", null, "./");
                }
            } catch (Exception e) {
                log.error("调用 iptables 添加端口异常", e);
                e.printStackTrace();
            }
        }
    }

    public void updateNewPeerHostPort(Map<String, String> diffPeerHostConfig) {
        Map<String, String> hosts = new HashMap<>(16);
        List<String> ports = new ArrayList<>();
        for (String host : diffPeerHostConfig.keySet()) {
            String ipPort = diffPeerHostConfig.get(host);
            String ip = ipPort.split(":")[0];
            String port = ipPort.split(":")[1];
            ports.add(port);
            hosts.put(host, ip);
        }
        this.envSet(ports, hosts);
    }

    public void removePortFromFirewall(String port) {
        boolean succ = false;
        try {
            String cmd = String.format("firewall-cmd --permanent --zone=public --remove-port=%s/tcp", port);
            ProcessUtil.Result res = ProcessUtil.execCmd(cmd, null, "./");
            if (res.getCode() == 0) {
                ProcessUtil.execCmd("firewall-cmd --reload", null, "./");
                succ = true;
            }
        } catch (Exception e) {
            log.error("调用 firewall-cmd 添加端口异常", e);
            e.printStackTrace();
        }

        if (!succ) {
            try {
                String cmd = String.format("iptables -D INPUT -p tcp --dport %s -j ACCEPT", port);
                ProcessUtil.Result res = ProcessUtil.execCmd(cmd, null, "./");
                if (res.getCode() == 0) {
                    ProcessUtil.execCmd("service iptables save", null, "./");
                }
            } catch (Exception e) {
                log.error("调用 iptables 添加端口异常", e);
                e.printStackTrace();
            }
        }
    }
}
