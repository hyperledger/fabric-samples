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
