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
package com.cgb.bcpinstall.common.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
public class ServerEntity implements Serializable {
    private static final long serialVersionUID = 320994761941192716L;

    /**
     * 所担任的角色
     */
    private RoleEnum role;
    /**
     * 从节点安装服务的url，格式: http://ip:port
     */
    private String httpUrl;
    /**
     * 从节点的IP地址
     */
    private String host;
    /**
     * 这个port指的是机器锁充当角色的对应端口
     */
    private List<String> rolePorts;

    private InstallStatusEnum status;
}
