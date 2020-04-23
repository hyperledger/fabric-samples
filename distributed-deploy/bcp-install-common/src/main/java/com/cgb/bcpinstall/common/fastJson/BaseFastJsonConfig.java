package com.cgb.bcpinstall.common.fastJson;

import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import com.cgb.bcpinstall.common.response.StatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;


/**
 * class_name: BaseFastJsonConfig
 * package: com.midea.mobile.question.main.fastjson
 * describe: BaseFastJsonConfig
 *
 * @author: zhun.xiao
 * @date: 2018/1/15
 * @time: 16:16
 **/
public class BaseFastJsonConfig {
    private final static Logger logger = LoggerFactory.getLogger(BaseFastJsonConfig.class);

    /**
     * fastJson相关设置
     */
    public static FastJsonHttpMessageConverter fastJsonHttpMessageConverter() {
        logger.info("fastJsonHttpMessageConverter........");

        FastJsonHttpMessageConverter fastJsonHttpMessageConverter = new FastJsonHttpMessageConverter();

        List<MediaType> supportedMediaTypes = new ArrayList<MediaType>();
        supportedMediaTypes.add(MediaType.parseMediaType("text/plain;charset=utf-8"));
        supportedMediaTypes.add(MediaType.parseMediaType("text/html;charset=utf-8"));
        supportedMediaTypes.add(MediaType.parseMediaType("text/json;charset=utf-8"));
        supportedMediaTypes.add(MediaType.parseMediaType("application/json;charset=utf-8"));
        supportedMediaTypes.add(MediaType.parseMediaType("text/html;charset=utf-8"));
        supportedMediaTypes.add(MediaType.parseMediaType("*"));

        fastJsonHttpMessageConverter.setSupportedMediaTypes(supportedMediaTypes);
        fastJsonHttpMessageConverter.setFastJsonConfig(getFastJsonConfig());

        return fastJsonHttpMessageConverter;
    }

    /**
     * fastjson相关配置
     *
     * @return
     */
    private static FastJsonConfig getFastJsonConfig() {
        logger.info("getFastJsonConfig.....");
        FastJsonConfig fastJsonConfig = new FastJsonConfig();
        //serializerFeatureList中添加转换规则
        List<SerializerFeature> serializerFeatureList = new ArrayList<SerializerFeature>();
        //输出key时是否使用双引号
        serializerFeatureList.add(SerializerFeature.QuoteFieldNames);
        //是否输出值为null的字段
        serializerFeatureList.add(SerializerFeature.WriteMapNullValue);
        //数值字段如果为null,输出为0,而非null
        serializerFeatureList.add(SerializerFeature.WriteNullNumberAsZero);
        //List字段如果为null,输出为[],而非null
        serializerFeatureList.add(SerializerFeature.WriteNullListAsEmpty);
        //字符类型字段如果为null,输出为"",而非null
        serializerFeatureList.add(SerializerFeature.WriteNullStringAsEmpty);
        //Boolean字段如果为null,输出为false,而非null
        serializerFeatureList.add(SerializerFeature.WriteNullBooleanAsFalse);
        //null String不输出
        serializerFeatureList.add(SerializerFeature.WriteNullStringAsEmpty);
        //Date的日期转换器
        serializerFeatureList.add(SerializerFeature.WriteDateUseDateFormat);

        SerializerFeature[] serializerFeatures =
                serializerFeatureList.toArray(new SerializerFeature[serializerFeatureList.size()]);

        fastJsonConfig.setSerializerFeatures(serializerFeatures);
        fastJsonConfig.setSerializeConfig(new CustomSerializeConfig());
        ParserConfig.getGlobalInstance().putDeserializer(StatusCode.class, new StatusCodeDeserializer());
        ParserConfig.getGlobalInstance().addAccept("com.cgb.dp.common.entity");
        fastJsonConfig.getParserConfig().setAutoTypeSupport(true);
        return fastJsonConfig;

    }
}
