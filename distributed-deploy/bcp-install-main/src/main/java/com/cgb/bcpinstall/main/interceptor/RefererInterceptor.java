package com.cgb.bcpinstall.main.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.cgb.bcpinstall.common.response.BaseResponse;
import com.cgb.bcpinstall.common.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

/**
 * @program: RefererInterceptor
 * @description:
 * @author: Zhun.Xiao
 * @create: 2019-11-29 18:30
 **/
@Slf4j
public class RefererInterceptor extends HandlerInterceptorAdapter {
    /**
     * 白名单
     */
    @Value("${whiteList.referer}")
    private String[] refererDomain = new String[]{};
    /**
     * 是否开启referer校验
     */
    private Boolean check = true;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
//        if (!check) {
//            return true;
//        }
//        String referer = req.getHeader("referer");
//        String host = req.getServerName();
//        // 验证非get请求
//        if (!"GET".equals(req.getMethod())) {
//            if (referer == null) {
//                // 状态置为404
//                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
//                return false;
//            }
//            java.net.URL url = null;
//            try {
//                url = new java.net.URL(referer);
//            } catch (MalformedURLException e) {
//                // URL解析异常，也置为404
//                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
//                return false;
//            }
//            // 首先判断请求域名和referer域名是否相同
//            if (!host.equals(url.getHost())) {
//                // 如果不等，判断是否在白名单中
//                if (refererDomain != null) {
//                    for (String s : refererDomain) {
//                        if (s.equals(url.getHost())) {
//                            return true;
//                        }
//                    }
//                }
//
//                resultInfo(resp, ResponseCode.REFERER_ERROR);
//                logger.error("------->>> referer校验风险 ，禁止访问");
//                return false;
//            }
//        }
        return true;
    }

    private void resultInfo(HttpServletResponse response, ResponseCode code) throws Exception {
        BaseResponse resp = new BaseResponse();
        resp.setCode(code);
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.write(JSONObject.toJSONString(resp, SerializerFeature.WriteEnumUsingToString));
        writer.flush();
        writer.close();
    }
}