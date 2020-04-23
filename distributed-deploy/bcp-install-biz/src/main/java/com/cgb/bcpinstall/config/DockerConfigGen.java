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
