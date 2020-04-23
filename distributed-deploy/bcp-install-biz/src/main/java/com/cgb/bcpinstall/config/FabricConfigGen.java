package com.cgb.bcpinstall.config;

import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;

/**
 * @author zheng.li
 * @date 2020/3/12 15:07
 */
public interface FabricConfigGen {
    /**
     * 生成configtx.yaml文件
     *
     * @param initConfig 配置信息
     * @return true--生成成功 false--生成失败
     */
    boolean configTxGen(InitConfigEntity initConfig);

    /**
     * 生成crypto-config.yaml文件
     *
     * @param initConfig 配置信息
     * @return true--生成成功 false--生成失败
     */
    boolean cryptoGen(InitConfigEntity initConfig);
}
