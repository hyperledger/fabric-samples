package com.cgb.bcpinstall.main.config;

import com.cgb.bcpinstall.main.interceptor.RefererInterceptor;
import com.cgb.bcpinstall.main.interceptor.UserInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static com.cgb.bcpinstall.common.fastJson.BaseFastJsonConfig.fastJsonHttpMessageConverter;

@Slf4j
@Configuration
public class WebConfiguration implements WebMvcConfigurer {
    /**
     * 添加fastJsonHttpMessageConverter到converters
     */
    @Override
    public void configureMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("configureMessageConverters........");
        converters.add(fastJsonHttpMessageConverter());
    }

    @Bean
    public UserInterceptor userInterceptor() {
        return new UserInterceptor();
    }

    @Bean
    public RefererInterceptor refererInterceptor() {
        return new RefererInterceptor();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
//        registry.addInterceptor(userInterceptor())
//                .addPathPatterns("/v1/**")
//                .excludePathPatterns("/v1/sys/login/login/**")
//                .excludePathPatterns("/v1/sys/login/logout/**")
//                .excludePathPatterns("/v1/install/downloadFile/**");
//
        registry.addInterceptor(refererInterceptor()).addPathPatterns("/v1/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("swagger-ui.html")
                .addResourceLocations("classpath:/META-INF/resources/");

        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
