package com.cgb.bcpinstall.main.interceptor;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.cgb.bcpinstall.common.context.RequestContextHandler;
import com.cgb.bcpinstall.common.response.BaseResponse;
import com.cgb.bcpinstall.common.response.ResponseCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.List;


/**
 * @program: UserInterceptor
 * @description:
 * @author: Zhun.Xiao
 * @create: 2018-11-26 11:37
 **/
@Slf4j
public class UserInterceptor implements HandlerInterceptor {

    /**
     * 判断用户是否有访问菜单权限
     *
     * @param
     * @param uri
     * @return
     */
    public static boolean HasPermission(List<String> menus, String uri) {
        if (uri.indexOf("?") > 0) {
            uri = uri.substring(0, uri.indexOf("?"));
        }
        final String u = uri;
        if (menus == null) {
            return false;
        }
        return menus.parallelStream().anyMatch(menu ->
                menu.equals(u)
        );


    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        return true;
        // 如果不是映射到方法直接通过
//        if (!(handler instanceof HandlerMethod)) {
//            return true;
//        }

//
//        // 根据token获取用户 保存ThreadLocal
//        String accessToken = request.getHeader(LoginConstant.ACCESS_TOKEN);
//
//        if (StringUtils.isNotBlank(accessToken)) {
//            accessToken = URLDecoder.decode(accessToken, "UTF-8");
//            /*SysUserDO usertemp = new SysUserDO();
//            usertemp.setUpdId((long) 0);
//            usertemp.setId((long) 1);
//            redisValueService.set(LoginConstant.LOGIN_TOKENS + token, usertemp, 100000);*/
//
//            System.out.println("UserInterceptor->preHandle:" + accessToken);
//            //从本地缓存读取token对应用户信息
//
//            RequestContextHandler.setCurrentUserToken(accessToken);
//            logger.info("--> token 校验成功");
//            return true;
//
//        } else {
//            logger.info("---> interceptor token is null");
//            resultInfo(response, ResponseCode.NO_TOKEN);
//            return false;
//        }
//

    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        RequestContextHandler.remove();
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        RequestContextHandler.remove();

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
