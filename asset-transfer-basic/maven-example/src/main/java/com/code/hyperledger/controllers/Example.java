package com.code.main.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Example {

    @GetMapping("/hello")
    public String sayHello() {
        return "Hello, World!";
    }
}