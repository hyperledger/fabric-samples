package com.cgb.bcpinstall.common.response;

import java.io.Serializable;

/**
 * @program: StatusCode
 * @description:
 * @author: Zhun.Xiao
 * @create: 2019-03-19 09:04
 **/
public interface StatusCode<T> extends Serializable {

    /**
     * 状态码
     *
     * @return
     */
    public T code();

    /**
     * 状态码描述
     *
     * @return
     */
    public String msg();


}
