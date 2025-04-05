package com.code.hyperledger.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.code.hyperledger.models.Receta;

@RestController
public class Example {

    @GetMapping("/hello")
    public String sayHello() {
        return "Hello, World!";
    }
}
