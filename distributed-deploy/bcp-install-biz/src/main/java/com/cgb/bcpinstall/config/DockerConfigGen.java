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
package com.cgb.bcpinstall.config;

import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;

/**
 * @author zheng.li
 * @date 2020/3/12 15:07
 */
public interface DockerConfigGen {
    /**
     * 生成peer的docker-compose文件
     *
     * @param initConfig 配置信息
     * @return true--生成成功 false--生成失败
     */
    boolean peerComposeFileGen(InitConfigEntity initConfig);

    /**
     * 生成orderer的docker-compose文件
     *
     * @param initConfig 配置信息
     * @return true--生成成功 false--生成失败
     */
    boolean ordererComposeFileGen(InitConfigEntity initConfig);
}
