package com.cgb.bcpinstall.common.fastJson;

import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.cgb.bcpinstall.common.response.ResponseCode;

import java.lang.reflect.Type;

public class StatusCodeDeserializer implements ObjectDeserializer {
    @Override
    public <T> T deserialze(DefaultJSONParser defaultJSONParser, Type type, Object o) {
        JSONLexer lexer = defaultJSONParser.getLexer();
        String value = lexer.stringVal();
        for (ResponseCode responseCode : ResponseCode.values()) {
            if (value.equals(responseCode.code())) {
                return (T) responseCode;
            }
        }
        throw new RuntimeException("no status code: " + value);
    }

    @Override
    public int getFastMatchToken() {
        return 0;
    }
}
