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
package com.cgb.bcpinstall.main;

import com.cgb.bcpinstall.biz.InitializeBiz;
import com.cgb.bcpinstall.biz.InstallBiz;
import com.cgb.bcpinstall.common.constant.ToolsConstant;
import com.cgb.bcpinstall.common.response.BaseResponse;
import com.cgb.bcpinstall.common.response.ResponseCode;
import com.cgb.bcpinstall.common.util.CacheUtil;
import com.cgb.bcpinstall.common.util.FileUtil;
import com.cgb.bcpinstall.common.util.OSinfo;
import com.cgb.bcpinstall.common.util.SpringUtil;
import com.cgb.bcpinstall.config.GlobalConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import springfox.documentation.swagger2.annotations.EnableSwagger2;

import java.io.File;
import java.util.Scanner;

@Slf4j
@SpringBootApplication(scanBasePackages = "com.cgb.bcpinstall", exclude = DataSourceAutoConfiguration.class)
@EnableTransactionManagement
@EnableWebMvc
@EnableAsync
@EnableScheduling
@ServletComponentScan
@EnableConfigurationProperties
@EnableSwagger2
public class MainApplication {
    public static void main(String[] args) {
        ApplicationContext app = SpringApplication.run(MainApplication.class, args);
        SpringUtil.setApplicationContext(app);
        // log.info("bcp-install 安装服务已启动...");
        log.info("bcp-install installation service has started...");
        GlobalConfig config = (GlobalConfig) SpringUtil.getBean("globalConfig");
        if (config.getMaster() == 1) {
            // log.info("本结点是主节点");
            log.info("This node is the master node");
            InitializeBiz initializeBiz = (InitializeBiz) SpringUtil.getBean("initializeBiz");
            if (initializeBiz.needInit()) {
                BaseResponse response = initializeBiz.initialize();
                if (!response.getCode().equals(ResponseCode.SUCCESS)) {
                    log.error(response.getMsg());
                } else {
                    // log.info("初始化完成");
                    log.info("Initialization completed");
                }
                SpringApplication.exit(app, () -> 0);
                return;
            }
        }
        InstallBiz installBiz = (InstallBiz) SpringUtil.getBean("installBiz");
        installBiz.start();
        while (true) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (installBiz.isFinished()) {
                SpringApplication.exit(app, () -> 0);
                break;
            }
        }
    }

    @Component
    public static class Runner implements CommandLineRunner {

        @Value("${init.dir}")
        private String initDir;

        @Override
        public void run(String... args) {
            // log.info("bcp-app-install 完成资源初始化");
            log.info("bcp-app-install completed the resource initialization");
            initToolsFilePath();
        }

        /**
         * 根据不同的操作系统初始化工具文件路径
         */
        private void initToolsFilePath() {
            String dir = FileUtil.reviseDir(this.initDir);

            if (!dir.endsWith(File.separator)) {
                dir = dir + File.separator;
            }
            String toolsPath = dir + "tools/";
            if (OSinfo.isWindows()) {
                CacheUtil.putCryptogenFilePath(toolsPath + "windows" + "/" + ToolsConstant.CRYPTOGEN + ".exe");
                CacheUtil.putConfigtxgenFilePath(toolsPath + "windows" + "/" + ToolsConstant.CONFIGTXGEN + ".exe");
                CacheUtil.putConfigtxlatorFilePath(toolsPath + "windows" + "/" + ToolsConstant.CONFIGTXLATOR + ".exe");
            }
            if (OSinfo.isMacOSX()) {
                CacheUtil.putCryptogenFilePath(toolsPath + "mac" + "/" + ToolsConstant.CRYPTOGEN);
                CacheUtil.putConfigtxgenFilePath(toolsPath + "mac" + "/" + ToolsConstant.CONFIGTXGEN);
                CacheUtil.putConfigtxlatorFilePath(toolsPath + "mac" + "/" + ToolsConstant.CONFIGTXLATOR);
            }
            if (OSinfo.isLinux()) {
                CacheUtil.putCryptogenFilePath(toolsPath + "linux" + "/" + ToolsConstant.CRYPTOGEN);
                CacheUtil.putConfigtxgenFilePath(toolsPath + "linux" + "/" + ToolsConstant.CONFIGTXGEN);
                CacheUtil.putConfigtxlatorFilePath(toolsPath + "linux" + "/" + ToolsConstant.CONFIGTXLATOR);
            }
            // log.info("***  " + ToolsConstant.CRYPTOGEN + "工具本地路径为：" + CacheUtil.getCryptogenFilePath());
            // log.info("***  " + ToolsConstant.CONFIGTXGEN + "工具本地路径为：" + CacheUtil.getConfigtxgenFilePath());
            // log.info("***  " + ToolsConstant.CONFIGTXLATOR + "工具本地路径为：" + CacheUtil.getConfigtxlatorFilePath());

            log.info("***  " + ToolsConstant.CRYPTOGEN + "The local path of the tool is:" + CacheUtil.getCryptogenFilePath());
            log.info("***  " + ToolsConstant.CONFIGTXGEN + "The local path of the tool is:" + CacheUtil.getConfigtxgenFilePath());
            log.info("***  " + ToolsConstant.CONFIGTXLATOR + "The local path of the tool is:" + CacheUtil.getConfigtxlatorFilePath());
        }
    }
}
