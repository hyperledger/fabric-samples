package com.code.hyperledger.controllers;

import com.code.hyperledger.coso.Receta;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Example {

    @GetMapping("/hello")
    public String sayHello() {
        Receta receta = new Receta();

        return "Hello, World!";
    }
}
