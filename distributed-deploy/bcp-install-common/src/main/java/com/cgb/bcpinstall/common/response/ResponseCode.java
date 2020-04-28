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

import java.util.HashMap;

public enum ResponseCode implements StatusCode<String> {

    // API_SUCCESS("10000", "接口调用成功"),
    // API_ERROR_1("10001", "设备IMEI码不存在"),
    // API_ERROR_2("10002", ""),
    // API_ERROR_3("10003", ""),
    // API_ERROR_4("10004", ""),
    // API_ERROR_5("10005", ""),
    // API_ERROR_6("10006", ""),
    // API_ERROR_7("10007", ""),
    // API_ERROR_8("10008", ""),

    API_SUCCESS("10000", "Interface call succeeded"),
    API_ERROR_1("10001", "Device IMEI code does not exist"),
    API_ERROR_2("10002", ""),
    API_ERROR_3("10003", ""),
    API_ERROR_4("10004", ""),
    API_ERROR_5("10005", ""),
    API_ERROR_6("10006", ""),
    API_ERROR_7("10007", ""),
    API_ERROR_8("10008", ""),

    /**
     * 系统错误
     */
    // SYSTEM_ERROR("20000", "系统开了点小差，请稍后再试"),
    SYSTEM_ERROR("20000", "Something went wrong with the system, please try again later"),

    /**
     * 参数错误
     */
    // PARAM_FAIL("30000", "参数错误"),
    PARAM_FAIL("30000", "Parameter error"),

    /**
     * 不符合业务处理，抛出的异常代码
     * 所有业务错误码，以4开头添加
     */
    // BIZ_FAIL("40000", "业务异常"),
    BIZ_FAIL("40000", "Abnormal business"),

    /**
     * 用户不存在
     */
    // USER_NOT_EXIST("40001", "用户不存在"),
    USER_NOT_EXIST("40001", "User does not exist"),

    // NO_STREAM("40002", "无流量记录"),
    NO_STREAM("40002", "No flow record"),

    // REPLICATED("40003", "重复数据"),
    REPLICATED("40003", "Duplicate data"),

    /**
     * 登陆成功
     */
    // LOGIN_SUCCESS("50000", "登陆成功"),
    LOGIN_SUCCESS("50000", "Login successfully"),

    /**
     * 登陆失败
     */
    // LOGIN_FAIL("50001", "登陆失败，用户名或者密码错误"),
    LOGIN_FAIL("50001", "Login failed, wrong username or password"),

    // NO_PERMISSION("50002", "用户无权限"),
    NO_PERMISSION("50002", "User has no permissions"),

    /**
     * 退出成功
     */
    // LOGOUT_SUCCESS("50003", "退出成功"),
    LOGOUT_SUCCESS("50003", "Logout successfully"),

    /**
     * 没有Token
     */
    // NO_TOKEN("50004", "没有Token"),
    NO_TOKEN("50004", "No Token"),

    /**
     * 没有Token
     */
    // TOKEN_ERROR("50005", "token验证失败"),
    TOKEN_ERROR("50005", "Token verification failed"),

    /**
     * 密码过期
     */
    // LOGIN_OVER_DUE("50006", "密码过期,请修改密码"),
    // LOGIN_ABOUT_OVER_DUE("50007", "密码即将过期"),
    LOGIN_OVER_DUE("50006", "Password expired, please change the password"),
    LOGIN_ABOUT_OVER_DUE("50007", "Password is about to expire"),

    // SSO_LOGIN_FAIL("50008", "单点登录失败"),
    SSO_LOGIN_FAIL("50008", "Single sign-on failed"),

    // NO_KEY("50009", "无法获取登录秘钥"),
    NO_KEY("50009", "Can't get login key"),

    /**
     * referer风险提示
     */
    // REFERER_ERROR("50010", "疑似referer风险，禁止访问"),
    REFERER_ERROR("50010", "Suspected referer risk, denied access"),

    /**
     * 执行成功
     */
    // SUCCESS("0", "操作成功"),
    SUCCESS("0", "Successful operation"),

    /**
     * 执行失败
     */
    // Fail("1", "操作失败"),
    Fail("1", "Operation failed"),

    // BOOTING("2", "启动中"),
    BOOTING("2", "Starting"),

    /**
     * umaa
     */

    // UMAA_SUCCESS("0000", "成功"),
    // UMAA_SYSTEM_ERROR("0001", "系统出现错误请联系管理员"),
    // UMAA_PARAMS_ERROR("0002", "输入参数有错误"),
    // UMAA_XML_ERROR("0003", "输入数据有误,不能解析的xml"),
    // UMAA_EVENTID_ERROR("0004", "输入的EventId有误"),
    // UMAA_INFO_LACK_ERROR("0005", "必填字段缺失"),
    // UMAA_NO_DATA_PORCESS_ERROR("0006", "没有需要处理的数据"),
    // UMAA_NO_TARTET_ERROR("0007", "找不到目标系统"),
    // UMAA_REPEAT_ERROR("0008", "状态已回写，无需再次处理"),
    // UMAA_NO_IMP_ERROR("0009", "该交易码对应的逻辑未实现"),
    // UMAA_TRADE_CODE_ERROR("0010", "请确认交易码是否正确，该交易请求不需要报文体"),
    // UMAA_UNKOWN_ERROR("0011", "远程校验tokenId失败");

    UMAA_SUCCESS("0000", "success"),
    UMAA_SYSTEM_ERROR("0001", "System error, please contact the administrator"),
    UMAA_PARAMS_ERROR("0002", "The input parameter is wrong"),
    UMAA_XML_ERROR("0003", "The input data is wrong, cannot be parsed xml"),
    UMAA_EVENTID_ERROR("0004", "The entered EventIdz field is wrong"),
    UMAA_INFO_LACK_ERROR("0005", "Required field is missing"),
    UMAA_NO_DATA_PORCESS_ERROR("0006", "No data to process"),
    UMAA_NO_TARTET_ERROR("0007", "No target system found"),
    UMAA_REPEAT_ERROR("0008", "Status has been written back, no need to process again"),
    UMAA_NO_IMP_ERROR("0009", "The logic corresponding to the transaction code is not implemented"),
    UMAA_TRADE_CODE_ERROR("0010", "Please confirm whether the transaction code is correct, the transaction request does not require a message body"),
    UMAA_UNKOWN_ERROR("0011", "Failed to verify tokenId remotely");

    public static HashMap<String, ResponseCode> codeMap;

    static {
        codeMap = new HashMap<String, ResponseCode>() {{
            put(UMAA_SUCCESS.getCode(), UMAA_SUCCESS);
            put(UMAA_SYSTEM_ERROR.getCode(), UMAA_SYSTEM_ERROR);
            put(UMAA_PARAMS_ERROR.getCode(), UMAA_PARAMS_ERROR);
            put(UMAA_XML_ERROR.getCode(), UMAA_XML_ERROR);
            put(UMAA_EVENTID_ERROR.getCode(), UMAA_EVENTID_ERROR);
            put(UMAA_INFO_LACK_ERROR.getCode(), UMAA_INFO_LACK_ERROR);
            put(UMAA_NO_DATA_PORCESS_ERROR.getCode(), UMAA_NO_DATA_PORCESS_ERROR);
            put(UMAA_NO_TARTET_ERROR.getCode(), UMAA_NO_TARTET_ERROR);
            put(UMAA_REPEAT_ERROR.getCode(), UMAA_REPEAT_ERROR);
            put(UMAA_NO_IMP_ERROR.getCode(), UMAA_NO_IMP_ERROR);
            put(UMAA_TRADE_CODE_ERROR.getCode(), UMAA_TRADE_CODE_ERROR);
            put(UMAA_UNKOWN_ERROR.getCode(), UMAA_UNKOWN_ERROR);
        }};
    }

    private String code;
    private String msg;

    ResponseCode(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static ResponseCode getByCode(String code) {
        return ResponseCode.SUCCESS;
    }

    public String code() {
        return this.code;
    }

    public String getCode() {
        return this.code;
    }

    public String getMsg() {
        return this.msg;
    }

    public String msg() {
        return this.msg;
    }

    @Override
    public String toString() {
        return String.valueOf(this.code);
    }

    /**
     * 判断是否相等
     *
     * @param targetResponseCode
     * @return
     */
    public Boolean equals(ResponseCode targetResponseCode) {
        return (this.code().equals(targetResponseCode.code()));
    }
}
