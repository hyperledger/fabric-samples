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

import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;
import com.cgb.bcpinstall.common.response.BaseResponse;
import com.cgb.bcpinstall.common.response.ResponseCode;
import com.cgb.bcpinstall.common.util.FileUtil;
import com.cgb.bcpinstall.config.ConfigFileGen;
import com.cgb.bcpinstall.service.InitConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;

@Slf4j
@Component
public class InitializeBiz {

    @Autowired
    private InitConfigService initConfigService;

    @Autowired
    private ConfigFileGen configFileGen;

    @Value("${init.config}")
    private String initConfigFile;

    @Value("${init.dir}")
    private String initDir;

    @Value("${init.yes}")
    private int doInit;

    public boolean needInit() {
        return this.doInit == 1;
    }

    public BaseResponse initialize() {
        BaseResponse response = new BaseResponse();
        if (!needInit()) {
            response.setCode(ResponseCode.Fail);
            return response;
        }

        this.initDir = FileUtil.reviseDir(this.initDir);
        this.initConfigFile = FileUtil.reviseDir(this.initConfigFile);

        try {
            InitConfigEntity configEntity = initConfigService.parseConfigFile(this.initConfigFile);
            if (configEntity == null) {
                response.setCode(ResponseCode.Fail);
                // response.setMsg("解析初始化配置文件失败");
                response.setMsg("Failed to parse the initialization configuration file");
                return response;
            }

            if (!initConfigService.isCorrectConfig(configEntity)) {
                // log.error("配置文件中相关配置项出错或为空");
                log.error("The relevant configuration items in the configuration file are wrong or empty");
                response.setCode(ResponseCode.Fail);
                // response.setMsg("配置文件中相关配置项出错或为空");
                response.setMsg("The relevant configuration items in the configuration file are wrong or empty");
                return response;
            }
            configFileGen.createConfigFile(configEntity);
        } catch (FileNotFoundException fe) {
            // log.error("文件不存在异常", fe);
            log.error("Exception file does not exist", fe);
            response.setCode(ResponseCode.Fail);
            fe.printStackTrace();
        } catch (Exception e) {
            // log.error("初始化发生异常", e);
            log.error("Exception occurred during initialization", e);
            response.setCode(ResponseCode.Fail);
            e.printStackTrace();
        }

        return response;
    }
}
