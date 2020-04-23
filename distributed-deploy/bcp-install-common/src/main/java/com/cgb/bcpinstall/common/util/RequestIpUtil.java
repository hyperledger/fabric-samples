package com.cgb.bcpinstall.common.util;


import javax.servlet.http.HttpServletRequest;

import static com.cgb.bcpinstall.common.constant.LoginConstant.*;


/**
 * class_name: RequestIpUtil
 * package: com.midea.mobile.uom.common.util
 * describe: 获取请求IP
 *
 * @author: zhun.xiao
 * @date: 2017/12/15
 * @time: 18:11
 **/
public class RequestIpUtil {

    /**
     * 获取IP地址,经过F5负载均衡、代理IP等服务器后的真实IP
     *
     * @param request 请求
     * @return String 真实的ip地址
     */
    public static String getIpAddr(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }
        String ip = request.getHeader(X_REAL_IP);
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader(X_FORWARDED_FOR);
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader(PROXY_CLIENT_IP);
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getHeader(WL_PROXY_CLIENT_IP);
        }
        if (ip == null || ip.length() == 0 || UNKNOWN.equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
