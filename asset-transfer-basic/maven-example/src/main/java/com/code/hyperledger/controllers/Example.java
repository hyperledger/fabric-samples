package com.code.hyperledger.controllers;

import com.code.hyperledger.coso.Asset;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class Example {

    @GetMapping("/hello")
    public String sayHello() {
        Asset asset = new Asset();

        return "Hello, World!";
    }
}
