package com.cgb.bcpinstall.api.controller;

import com.alibaba.fastjson.JSONObject;
import com.cgb.bcpinstall.biz.InstallBiz;
import com.cgb.bcpinstall.biz.RolesBiz;
import com.cgb.bcpinstall.common.annotation.InvokeLog;
import com.cgb.bcpinstall.common.entity.*;
import com.cgb.bcpinstall.common.response.BaseResponse;
import com.cgb.bcpinstall.common.response.HttpInstallResponse;
import com.cgb.bcpinstall.common.response.ResponseCode;
import com.cgb.bcpinstall.common.util.FileUtil;
import com.cgb.bcpinstall.service.FileService;
import com.cgb.bcpinstall.service.ModeService;
import com.cgb.bcpinstall.service.UpdateService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.*;
import java.net.URLEncoder;
import java.util.List;

@Slf4j
@RestController
@RequestMapping(value = "/v1/install", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
@Api("安装管理")
public class InstallController {
    @Autowired
    private RolesBiz rolesBiz;

    @Autowired
    private InstallBiz installBiz;

    @Autowired
    private ModeService modeService;

    @Autowired
    private FileService fileService;

    @Autowired
    private UpdateService updateService;

    /**
     * 从节点从主节点下载安装包
     * <p>
     * 压缩包内容格式（以下面格式进行组织目录，可以是其中一部分）：
     * |
     * |____crypto-config
     * | |_____ordererOrganizations
     * | |_____peerOrganizations
     * |____order
     * | |_____docker-compose-orderer.yaml
     * | |_____start-orderer.sh
     * |____peer
     * | |_____docker-compose-peer.yaml
     * | |_____start-peer.sh
     * |____backend
     * | |____bcp-app-mgr
     * | | |____resources
     * | | | |____networkconfigs
     * | | | |____users
     * | | | |____initData.yaml
     * | | |____start-backend.sh
     * |____web
     * | |____bcp-app-web
     * | | |____vue.config.js
     * | | |____start-web.sh
     *
     * @param request
     * @param response
     * @return
     */
    @RequestMapping("/getPackage/{os}")
    @ApiOperation(value = "下载安装文件")
    @InvokeLog(name = "getInstallPackage", description = "下载安装文件")
    public void getInstallData(@PathVariable("os") String osType, HttpServletRequest request, HttpServletResponse response) {
        String remoteAddr = request.getRemoteAddr();

        log.info(String.format("从节点 %s 开始下载安装包", remoteAddr));

        BaseResponse downloadResponse = new BaseResponse();

        OSEnum osEnum = null;
        try {
            osEnum = OSEnum.valueOf(osType);
        } catch (Exception e) {
            log.error("解析系统类型异常", e);
            e.printStackTrace();
        }
        if (osEnum == null) {
            log.error(String.format("从节点系统类型错误: %s", osType));

            downloadResponse.setCode(ResponseCode.Fail);
            downloadResponse.setMsg("不支持指定的系统类型: " + osType);
            setErrorResult(response, downloadResponse);
            return;
        }

        List<RoleEnum> roleList = rolesBiz.getRole(remoteAddr);

        // 根据角色准备不同的压缩包
        String filePath = fileService.packInstallFiles(remoteAddr, roleList, null);
        if (StringUtils.isEmpty(filePath)) {
            downloadResponse.setCode(ResponseCode.Fail);
            downloadResponse.setMsg("打包安装文件失败");
            setErrorResult(response, downloadResponse);
            return;
        }

        File downloadFile = new File(filePath);

        FileInputStream is = null;
        OutputStream os = null;

        try {
            // 配置文件下载
            response.setHeader("content-type", "application/octet-stream");
            response.setContentType("application/octet-stream");
            // 下载文件能正常显示中文
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode("InstallPackage.tar.gz", "UTF-8"));

            is = new FileInputStream(downloadFile);
            os = response.getOutputStream();

            IOUtils.copy(is, os);

            // 修改服务器对应的状态
            log.info(String.format("设置从节点 %s 状态为下载完成", remoteAddr));
            this.rolesBiz.setServerStatus(remoteAddr, InstallStatusEnum.DOWNLOADED);

        } catch (FileNotFoundException e) {
            log.error("下载文件不存在", e);
            e.printStackTrace();

            downloadResponse.setCode(ResponseCode.Fail);
            downloadResponse.setMsg("下载文件不存在");

            setErrorResult(response, downloadResponse);
        } catch (IOException e) {
            log.error("获取HttpServletResponse输出流发生异常", e);
            e.printStackTrace();

            downloadResponse.setCode(ResponseCode.Fail);
            downloadResponse.setMsg("获取HttpServletResponse输出流发生异常");

            setErrorResult(response, downloadResponse);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(os);
        }
    }

    @PostMapping("/pushPackage")
    @ApiOperation(value = "下载安装文件")
    @InvokeLog(name = "pushInstallPackage", description = "推送安装文件")
    public HttpInstallResponse pushInstallPackage(HttpServletRequest request) {
        log.info("准备接收主节点推送的安装包");

        this.installBiz.setMasterServer("http://" + request.getRemoteAddr() + ":8080");

        HttpInstallResponse response = new HttpInstallResponse();
        try {
            String filePath = this.installBiz.getInstallPackageFilePath();
            Part part = request.getPart("file");
            part.write(filePath);

            this.installBiz.installPackageReady();
        } catch (Exception e) {
            log.error("接收安装包异常", e);
            response.setCode(ResponseCode.Fail.getCode());
            e.printStackTrace();
        }

        return response;
    }

    private void setErrorResult(HttpServletResponse httpServletResponse, BaseResponse res) {
        httpServletResponse.setContentType("application/json; charset=utf-8");
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        try {
            PrintWriter writer = httpServletResponse.getWriter();
            writer.print(JSONObject.toJSONString(res));
            writer.close();
            httpServletResponse.flushBuffer();
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    /**
     * 主节点发送给从节点开始安装
     *
     * @param request
     * @return
     */
    @PostMapping("/start")
    @ApiOperation(value = "开始安装")
    @InvokeLog(name = "doInstall", description = "开始安装")
    public HttpInstallResponse doInstall(HttpServletRequest request) {
        HttpInstallResponse response = new HttpInstallResponse();
        log.info("从节点收到安装指令");
        try {
            //获取安装指令实例
            Part contentPart = request.getPart("content");
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            IOUtils.copy(contentPart == null ? request.getInputStream() : contentPart.getInputStream(), os);
            String content = os.toString();
            InstallCmd installCmd = JSONObject.parseObject(content, InstallCmd.class);
            this.installBiz.setMasterServer("http://" + request.getRemoteAddr() + ":8080");
            this.installBiz.slaveInstall(installCmd.getRole(), installCmd.getRolePorts(), installCmd.getHosts(), installCmd.getRoleFolderName());
        } catch (Exception e) {
            e.printStackTrace();
            response.setCode(ResponseCode.Fail.getCode());
        }
        return response;
    }

    @PostMapping("/backendStatus")
    @ApiOperation(value = "检查管理后台状态")
    @InvokeLog(name = "doCheckBackendStatus", description = "检查管理后台状态")
    public HttpInstallResponse checkBackendStatus(HttpServletRequest request) {
        HttpInstallResponse response = new HttpInstallResponse();
        String filePath = modeService.getInstallPath() + "fetchBackendInit.sh";
        try {
            Part part = request.getPart("file");
            part.write(filePath);
        } catch (IOException | ServletException e) {
            e.printStackTrace();
            response.setCode(ResponseCode.Fail.getCode());
            return response;
        }

        if (modeService.checkBackendInitFinished(null, filePath)) {
            response.setCode(ResponseCode.SUCCESS.getCode());
        } else {
            response.setCode(ResponseCode.BOOTING.getCode());
        }

        return response;
    }

    @PostMapping("/remove")
    @ApiOperation(value = "移除节点")
    @InvokeLog(name = "doRemove", description = "移除节点")
    public HttpInstallResponse doRemove(HttpServletRequest request) {
        HttpInstallResponse response = new HttpInstallResponse();
        try {
            Part contentPart = request.getPart("content");
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            IOUtils.copy(contentPart == null ? request.getInputStream() : contentPart.getInputStream(), os);
            String content = os.toString();
            RemoveCmd removeCmd = JSONObject.parseObject(content, RemoveCmd.class);
            String filePath = modeService.getInstallPath();
            if (!filePath.endsWith(File.separator)) {
                filePath = filePath + File.separator;
            }
            if (removeCmd.getRole() == RoleEnum.PEER || removeCmd.getRole() == RoleEnum.ORDER) {
                filePath = filePath + "stopNode.sh";
                Part part = request.getPart("file");
                part.write(filePath);
            }
            log.info("从节点收到移除指令");
            String domain = removeCmd.getRole() == RoleEnum.ORDER ? removeCmd.getOrdererDomain() : removeCmd.getPeerDomain();
            updateService.removeNode(removeCmd.getRole(), domain, removeCmd.getHostNames(), removeCmd.getPorts());
        } catch (Exception e) {
            e.printStackTrace();
            response.setCode(ResponseCode.Fail.getCode());
        }
        return new HttpInstallResponse();
    }

    /**
     * 主要用于节点更新
     *
     * @param request
     * @return
     */
    @PostMapping("/update")
    @ApiOperation(value = "更新节点")
    @InvokeLog(name = "doUpdate", description = "更新节点")
    public HttpInstallResponse doUpdate(HttpServletRequest request) {
        HttpInstallResponse response = new HttpInstallResponse();
        try {
            Part contentPart = request.getPart("content");
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            IOUtils.copy(contentPart == null ? request.getInputStream() : contentPart.getInputStream(), os);
            String content = os.toString();
            UpdateCmd cmd = JSONObject.parseObject(content, UpdateCmd.class);
            Part part = request.getPart("file");
            installBiz.handleUpdate(cmd, part);
        } catch (Exception e) {
            log.error("接收更新指令异常", e);
            response.setCode(ResponseCode.Fail.getCode());
            e.printStackTrace();
        }

        return response;
    }

    /**
     * 主节点接收从节点发过来的调用
     *
     * @param result
     * @param request
     * @return
     */
    @PostMapping("/finished")
    @ApiOperation(value = "完成安装")
    @InvokeLog(name = "installFinished", description = "完成安装")
    public HttpInstallResponse installFinished(@RequestBody InstallResult result, HttpServletRequest request) {
        log.info(String.format("从节点 %s 完成安装", request.getRemoteAddr()));

        modeService.updateInstallResult(request.getRemoteAddr(), result, null);
        return new HttpInstallResponse();
    }

    /**
     * 主节点向从节点发起的调用，通知安装结束
     *
     * @param result
     * @return
     */
    @PostMapping("/end")
    @ApiOperation(value = "结束安装")
    @InvokeLog(name = "endInstall", description = "结束安装")
    public HttpInstallResponse endInstall(@RequestBody EndCmd result) {
        log.info("从节点收到结束安装指令");

        this.installBiz.doEnd();
        return new HttpInstallResponse();
    }
}
