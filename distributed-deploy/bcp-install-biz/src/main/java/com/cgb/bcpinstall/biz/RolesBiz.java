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
package com.cgb.bcpinstall.biz;

import com.cgb.bcpinstall.common.entity.InstallResult;
import com.cgb.bcpinstall.common.entity.InstallStatusEnum;
import com.cgb.bcpinstall.common.entity.RoleEnum;
import com.cgb.bcpinstall.common.entity.ServerEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class RolesBiz {
    // 角色对机器URL的map
    private Map<RoleEnum, List<ServerEntity>> rolesMap = new HashMap<>();

    synchronized public void addRole(RoleEnum role, String httpUrl, String roleIp, List<String> rolePorts) {
        List<ServerEntity> servers;

        if (this.rolesMap.containsKey(role)) {
            servers = this.rolesMap.get(role);
        } else {
            servers = new ArrayList<>();
            this.rolesMap.put(role, servers);
        }

        if (servers.stream().noneMatch(s -> s.getHost().equalsIgnoreCase(roleIp))) {
            ServerEntity serverEntity = new ServerEntity();

            serverEntity.setRole(role);
            serverEntity.setHttpUrl(httpUrl);
            serverEntity.setHost(roleIp);
            serverEntity.setRolePorts(rolePorts);
            serverEntity.setStatus(InstallStatusEnum.REGISTERED);
            servers.add(serverEntity);
        }
    }

    public List<RoleEnum> getRole(String roleIp) {
        List<RoleEnum> roleList = new ArrayList<>();

        for (RoleEnum roleEnum: this.rolesMap.keySet()) {
            if (this.rolesMap.get(roleEnum).stream().anyMatch(s -> s.getHost().equals(roleIp))) {
                roleList.add(roleEnum);
            }
        }

        return roleList;
    }

    synchronized public void setServerStatus(String remoteAddress, InstallStatusEnum status) {
        this.rolesMap.values().forEach(c -> c.forEach(s -> {
            if (s.getHost().equals(remoteAddress)) {
                s.setStatus(status);
            }
        }));
    }

    public Map<RoleEnum, List<ServerEntity>> getRolesMap() {
        return this.rolesMap;
    }

    synchronized public void updateInstallResult(String remoteAddress, InstallResult result) {
        for (RoleEnum role: this.rolesMap.keySet()) {
            if (role == result.getRole()) {
                List<ServerEntity> serverEntities = this.rolesMap.get(role);
                for (ServerEntity server: serverEntities) {
                    if (server.getHost().endsWith(remoteAddress)) {
                        server.setStatus(result.isSuccess() ? InstallStatusEnum.SUCCESS : InstallStatusEnum.FAILED);
                    }
                }

                break;
            }
        }
    }
}
