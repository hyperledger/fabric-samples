/*
 *  Copyright CGB Corp All Rights Reserved.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
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

        // log.info(String.format("从节点 %s 开始注册", remoteAddr));
        log.info(String.format("Slave node %s starts to regist", remoteAddr));

        roleService.addServerRole(remoteAddr, roleRegEntity.getServerPort(),null);

        return new HttpInstallResponse();
    }
}
