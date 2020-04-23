package com.cgb.bcpinstall.biz;

import com.cgb.bcpinstall.common.entity.init.InitConfigEntity;

/**
 * @author zheng.li
 * @date 2020/2/3 11:35
 */
public interface InstallMode {
    /**
     * 执行mode
     * @param configEntity
     */
    void run(InitConfigEntity configEntity);
}
