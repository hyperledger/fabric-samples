package com.cgb.bcpinstall.common.fastJson;

import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alibaba.fastjson.serializer.SerializeWriter;
import com.cgb.bcpinstall.common.constant.DateConstant;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * class_name: JodaTimeDeserializer
 * package: com.midea.mobile.talkbar.common.utils
 * describe: fastjson 对 localdatetime格式化支持
 *
 * @author: zhun.xiao
 * @date: 2017/12/1
 * @time: 18:01
 **/
public class JodaTimeDeserializer implements ObjectSerializer {

    public static final JodaTimeDeserializer instance = new JodaTimeDeserializer();


    @Override
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.getWriter();
        String resultStr = object.toString();
        if (object == null) {
            out.writeNull();
            return;
        }
        if (fieldType == LocalDateTime.class) {
            LocalDateTime localDateTime = (LocalDateTime) object;
            resultStr = localDateTime.format(DateTimeFormatter.ofPattern(DateConstant.DATE_TIME_FORMATTER));
        } else if (fieldType == LocalDate.class) {
            LocalDate localDate = (LocalDate) object;
            resultStr = localDate.format(DateTimeFormatter.ofPattern(DateConstant.DATE_FORMATTER));
        } else if (fieldType == LocalTime.class) {
            LocalTime localTime = (LocalTime) object;
            resultStr = localTime.format(DateTimeFormatter.ofPattern(DateConstant.TIME_FORMATTER));
        }
        out.writeString(resultStr);
    }
}
