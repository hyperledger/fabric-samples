package com.cgb.bcpinstall.common.log;

import com.alibaba.fastjson.JSONObject;
import com.cgb.bcpinstall.common.entity.BaseEntity;
import com.cgb.bcpinstall.common.response.BaseResponse;
import com.cgb.bcpinstall.common.response.ResponseCode;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.util.StopWatch;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.File;

/**
 * @program: AbstractPrintLog
 * @description: 日志打印抽象类
 * @author: Zhun.Xiao
 * @create: 2018-10-29 17:01
 **/
@Component
public abstract class AbstractPrintLog {


    public static final Logger logger = LoggerFactory.getLogger(AbstractPrintLog.class);
    private static final String MSG = "\n --请求--\n --方法：{}\n --描述：{}\n --位置：{}\n --参数：{}\n --返回：{}\n --耗时：{} ms";
    /**
     * 服务响应超过2秒打印警告日志
     */
    private static final int DEFAULT_TIME_LIMIT = 2000;

    public Object execute(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] args = joinPoint.getArgs();

        if (logger.isDebugEnabled() || logger.isWarnEnabled()) {
            StopWatch clock = new StopWatch();
            clock.start();
            Object returnObj = null;
            try {
                return returnObj = joinPoint.proceed(args);
            } catch (Exception e) {
                throw e;
            } finally {
                clock.stop();
                long totalTimeMillis = clock.getTotalTimeMillis();
                handleLog(joinPoint, args, returnObj, totalTimeMillis);
            }
        } else {
            return joinPoint.proceed(args);
        }
    }

    /**
     * 日志处理
     *
     * @param joinPoint 位置
     * @param args      参数
     * @param returnObj 响应
     * @param costTime  耗时ms
     */
    protected abstract void handleLog(ProceedingJoinPoint joinPoint, Object[] args, Object returnObj, long costTime);

    /**
     * @param name            操作名称
     * @param description     描述
     * @param printReturn     是否打印响应
     * @param joinPoint       位置
     * @param args            参数
     * @param returnObj       响应
     * @param totalTimeMillis 耗时ms
     */
    protected void printLogMsg(String name, String description, boolean printReturn, JoinPoint joinPoint, Object[] args, Object returnObj, long totalTimeMillis) {
        // Object[] params = argsDemote(args);
        String params = getParam(args);

        /**
         * 返回码不是成功,或者执行超过1秒才记录日志
         */
//        if ( isSave(baseResponse,totalTimeMillis) ) {
//            sendLogToMongoDB(name, description, printReturn, joinPoint, args, returnObj, totalTimeMillis);
//        }

        if (totalTimeMillis < getTimeLimit()) {
            logger.info(MSG, new Object[]{name, description, joinPoint.getStaticPart(), params, getPrintMsg(printReturn, returnObj), totalTimeMillis});
        } else {
            logger.warn(MSG, new Object[]{name, description, joinPoint.getStaticPart(), params, getPrintMsg(printReturn, returnObj), totalTimeMillis});
        }
    }

    protected int getTimeLimit() {
        return DEFAULT_TIME_LIMIT;
    }

    protected String getPrintMsg(boolean printReturn, Object returnObj) {
        return printReturn ? ((returnObj != null) ? JSONObject.toJSONString(returnObj) : "null") : "[printReturn = false]";
    }

    protected Object[] argsDemote(Object[] args) {
        if (args == null || args.length == 0) {
            return new Object[]{};
        }
        Object[] params = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof ServletRequest || arg instanceof ServletResponse
                    || arg instanceof ModelMap || arg instanceof Model
                    || arg instanceof InputStreamSource || arg instanceof File || arg instanceof BaseEntity) {
                params[i] = args[i];
            } else {
                params[i] = args.toString();
            }
        }
        return params;
    }

    protected String getParam(Object[] args) {
        StringBuilder params = new StringBuilder();
        if (args != null && args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (null == args[i]) {
                    params.append("null").append(";");
                } else {
                    params.append(args[i].toString()).append(";");
                }
            }
        }
        return params.toString();
    }

    protected HttpServletRequest getHttpServletRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }

    private boolean isSave(BaseResponse baseResponse, long totalTimeMillis) {
        return baseResponse != null && !baseResponse.getCode().equals(ResponseCode.SUCCESS) || totalTimeMillis >= getTimeLimit();
    }

}
