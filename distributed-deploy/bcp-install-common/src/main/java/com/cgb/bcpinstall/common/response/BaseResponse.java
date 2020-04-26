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
package com.cgb.bcpinstall.common.response;

import io.swagger.annotations.ApiModelProperty;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.io.Serializable;

/**
 * @program: BaseResponse
 * @description: BaseResponse
 * @author: Zhun.Xiao
 * @create: 2018-10-29 14:20
 **/
public class BaseResponse<T> implements Serializable {
    private static final ResponseCode SUCCESS;

    static {
        SUCCESS = ResponseCode.SUCCESS;
    }

    @ApiModelProperty(value = "返回码")
    private StatusCode code;
    @ApiModelProperty(value = "返回消息")
    private String msg;
    @ApiModelProperty(value = "返回对象")
    private T data;

    public BaseResponse() {
        this.setCode(SUCCESS);
        this.msg = SUCCESS.msg();
    }

    public BaseResponse(T data) {
        this();
        this.data = data;
    }

    public BaseResponse(T data, String callback) {
        this(data);
    }


    public StatusCode getCode() {
        return code;
    }

    public void setCode(ResponseCode code) {
        this.code = code;
        this.msg = code.getMsg();
    }

    public String getMsg() {
        return this.msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return this.data;
    }

    public void setData(T data) {
        this.data = data;
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("code", code.code())
                .append("msg", msg)
                .append("data", data)
                .toString();
    }


    public String toJsonString() {
        return "{" +
                "\"msg\":\"" + msg + "\"," +
                "\"code\":\"" + code.code() + "\"," +
                "\"data\":\"" + data + "\"" +
                '}';
    }

}
