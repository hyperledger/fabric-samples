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
package com.cgb.bcpinstall.common.constant;

/**
 * 登陆相关
 */
public abstract class LoginConstant {
    /**
     * 未知ip unknow
     */
    public static final String UNKNOWN = "unknown";
    /**
     * 请求头中 X-Real-IP
     */
    public static final String X_REAL_IP = "X-Real-IP";
    /**
     * 请求头中 X-Forwarded-For
     */
    public static final String X_FORWARDED_FOR = "X-Forwarded-For";
    /**
     * 请求头中 Proxy-Client-IP
     */
    public static final String PROXY_CLIENT_IP = "Proxy-Client-IP";
    /**
     * 请求头中 WL-Proxy-Client-IP
     */
    public static final String WL_PROXY_CLIENT_IP = "WL-Proxy-Client-IP";

    public static final String CURRENT_USER_NAME = "current_user_name";
    public static final String CURRENT_USER_ID = "current_user_id";
    public static final String CURRENT_USER_TOKEN = "current_user_token";
    public static final String CURRENT_MSP = "current_msp";
}
