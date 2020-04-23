package com.cgb.bcpinstall.api.controller;

import com.cgb.bcpinstall.biz.InstallBiz;
import com.cgb.bcpinstall.common.annotation.InvokeLog;
import com.cgb.bcpinstall.common.entity.RoleRegEntity;
import com.cgb.bcpinstall.common.response.BaseResponse;
import com.cgb.bcpinstall.common.response.HttpInstallResponse;
import com.cgb.bcpinstall.service.ModeService;
import com.cgb.bcpinstall.service.RoleService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@RestController
@RequestMapping(value = "/v1/reg", produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
@Api("注册管理")
public class RegisterController {

    @Autowired
    private RoleService roleService;

    /**
     * 从节点向主节点注册角色，主节点会根据其 ip 地址找出其所有角色，并进行缓存
     *
     * @param roleRegEntity
     * @param request
     * @return
     */
    @Deprecated
    @PostMapping("/role")
    @ApiOperation(value = "注册从服务器角色")
    @InvokeLog(name = "registerServerRole", description = "注册从服务器角色")
    public HttpInstallResponse regRole(@RequestBody RoleRegEntity roleRegEntity, HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();

        log.info(String.format("从节点 %s 开始注册", remoteAddr));

        roleService.addServerRole(remoteAddr, roleRegEntity.getServerPort(),null);

        return new HttpInstallResponse();
    }
}
