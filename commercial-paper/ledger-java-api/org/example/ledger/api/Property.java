package org.example.ledger.api;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(value=RetentionPolicy.RUNTIME)
public @interface Property {
	 public String name() default "";
	 public boolean key() default false;
}
