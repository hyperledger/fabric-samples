package com.cgb.bcpinstall.common.util;

import com.cgb.bcpinstall.common.constant.ToolsConstant;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.concurrent.TimeUnit;

/**
 * @program: CacheUtil
 * @description: 服务器本地缓存工具
 * @author: Zhun.Xiao
 * @create: 2019-09-08 18:50
 **/
public class CacheUtil {

    /**###############  登录用户信息维护  #######################**/

    /**
     * cryptogen,configtxgen,configtxlator工具的二进制文件卢金
     */
    private static Cache<String, String> cacheToolsFilePath = CacheBuilder.newBuilder().build();

    public static void putCryptogenFilePath(String filepath) {
        cacheToolsFilePath.put(ToolsConstant.CRYPTOGEN, filepath);
    }

    public static String getCryptogenFilePath() {
        try {
            return cacheToolsFilePath.getIfPresent(ToolsConstant.CRYPTOGEN);
        } catch (Exception e) {
            return null;
        }
    }

    public static void putConfigtxgenFilePath(String filepath) {
        cacheToolsFilePath.put(ToolsConstant.CONFIGTXGEN, filepath);
    }

    public static String getConfigtxgenFilePath() {
        try {
            return cacheToolsFilePath.getIfPresent(ToolsConstant.CONFIGTXGEN);
        } catch (Exception e) {
            return null;
        }
    }

    public static void putConfigtxlatorFilePath(String filepath) {
        cacheToolsFilePath.put(ToolsConstant.CONFIGTXLATOR, filepath);
    }

    public static String getConfigtxlatorFilePath() {
        try {
            return cacheToolsFilePath.getIfPresent(ToolsConstant.CONFIGTXLATOR);
        } catch (Exception e) {
            return null;
        }
    }


}