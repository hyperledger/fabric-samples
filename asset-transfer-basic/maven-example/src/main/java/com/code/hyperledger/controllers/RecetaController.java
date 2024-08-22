package com.code.hyperledger.controllers;

import com.code.hyperledger.coso.Receta;
import com.code.hyperledger.services.RecetaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController

@RequestMapping("/recetas")
public class RecetaController {

    @Autowired
    private RecetaService recetaService;

    //usar este endpoint
    @PostMapping("/crear")
    public String crear() {
        System.out.println("\n--> Submit Transaction: CreateAsset, creates new asset with all arguments");
        recetaService.crearReceta();

        return "Hello, World!";
    }

    @GetMapping("/obtener")
    public String sayHello() {
        com.code.hyperledger.coso.Receta receta = new com.code.hyperledger.coso.Receta();

        return "Hello, World!";
    }


}
