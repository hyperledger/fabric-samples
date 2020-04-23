package com.cgb.bcpinstall.common.log;


import com.cgb.bcpinstall.common.annotation.InvokeLog;
import com.cgb.bcpinstall.common.util.RequestIpUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;

/**
 * @program: InvokeLogAspect
 * @description: 日志切面处理类
 * @author: Zhun.Xiao
 * @create: 2018-10-29 17:01
 **/


@Aspect
@Component
public class InvokeLogAspect extends AbstractPrintLog {

    /* TODO 日志处理
   @Autowired
    private LogService logService;*/


    @Override
    protected void handleLog(ProceedingJoinPoint joinPoint, Object[] args, Object returnObj, long costTime) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        InvokeLog invokeLog = method.getAnnotation(InvokeLog.class);

        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String ip = RequestIpUtil.getIpAddr(request);

        String params = getParam(args);

        if (invokeLog.persistence()) {

            //TODO 日志持久化方案
            // logService.saveLog(ip, request.getRequestURI(), params, getPrintMsg(true, returnObj));
        }
        printLogMsg(invokeLog.name(), invokeLog.description(), invokeLog.printReturn(), joinPoint, args, returnObj, costTime);
    }

    // 定义切点Pointcut
    @Pointcut("@annotation(com.cgb.bcpinstall.common.annotation.InvokeLog)")
    public void excudePointcut() {
    }


    @Around("excudePointcut()")
    @Override
    public Object execute(ProceedingJoinPoint joinPoint) throws Throwable {
        return super.execute(joinPoint);
    }


}
