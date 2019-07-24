/*
SPDX-License-Identifier: Apache-2.0
*/

package org.example.ledger.api;

import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;

public class State {
	private final String key;
	private final Map<String, String> properties;

	protected final Supplier<String> keyBuilder(String[] parts) {
		return () ->  Arrays.asList(parts).stream().map(part -> getProperty(part)).collect(Collectors.joining(":"));
	};

	public State(byte[] buffer) {
		this.properties = deserialize(buffer);
		List<String> keyParts = new ArrayList<String>();
        for (Field field: getStateFields()) {
            field.setAccessible(true);
        	try {
        		String value = properties.get(getPropertyName(field));
				field.set(this, value);
				boolean isKey = field.getAnnotation(Property.class).key();
				if(isKey) {
					keyParts.add(value);
				}
			} catch (IllegalArgumentException | IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        key = String.join(":", keyParts);
        System.out.println(key);
	}

	private static Map<String, String> deserialize(byte[] buffer) {
		JsonObject json = toJSON(buffer);
		Map<String, String> result = json.entrySet().stream().collect(
				Collectors.toMap(e -> e.getKey(), e -> jsonString(e.getValue())));
		return result;
	}

	private static String jsonString(JsonValue value) {
		if(value.getValueType() == JsonValue.ValueType.STRING) {
			return ((JsonString)value).getString();
		}
		return value.toString();
	}

	private static JsonObject toJSON(byte[] buffer) {
		String payload = new String(buffer);
		String payloadData;
	    try (JsonReader reader = Json.createReader(new StringReader(payload))) {
	        JsonObject json = reader.readObject();
	        JsonArray arr = json.getJsonArray("data");
	        byte[] codepoints = new byte[arr.size()];
	        for(int i = 0; i < arr.size(); i++) {
	        	codepoints[i] = (byte) ((JsonNumber)arr.get(i)).intValue();
	        }
	        payloadData = new String(codepoints);
	    }
	    try (JsonReader reader = Json.createReader(new StringReader(payloadData))) {
	        JsonObject json = reader.readObject();
	        return json;
	    }
	}

    private static String getPropertyName(java.lang.reflect.Field field) {
        String name = field.getAnnotation(Property.class).name();
        if (name.isEmpty()) {
            name = field.getName();
        }
        return name;
    }

    private String getProperty(String key) {
    	return properties.get(key);
    }

    private List<Field> getStateFields() {
		Class<? extends State> clazz = this.getClass().asSubclass(this.getClass());
		return Arrays.asList(clazz.getDeclaredFields())
			.stream()
			.filter(field -> field.isAnnotationPresent(Property.class))
			.collect(Collectors.toList());
    }

	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append(this.getClass().getName());
		builder.append(" [");
        for (Field field: getStateFields()) {
            field.setAccessible(true);
        	builder.append(field.getName());
        	builder.append("=");
        	try {
				builder.append(field.get(this));
			} catch (IllegalArgumentException | IllegalAccessException e) {
				// ignore for now
			}
        	builder.append(", ");
        }
        builder.setCharAt(builder.length() - 2, ']');
        return builder.toString();
	}

}
