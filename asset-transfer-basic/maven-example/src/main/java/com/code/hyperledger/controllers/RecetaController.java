package com.code.hyperledger.controllers;

import com.code.hyperledger.coso.Receta;
import com.code.hyperledger.services.RecetaService;
import org.hyperledger.fabric.client.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;

@RestController

@RequestMapping("/recetas")
public class RecetaController {

    @Autowired
    private RecetaService recetaService;

    @PostMapping("/crear")
    public ResponseEntity<Receta> crear(Receta receta) {
        System.out.println("\n--> Submit Transaction: CreateAsset, creates new asset with all arguments");

        String assetId = "asset" + Instant.now().toEpochMilli();
        receta.setId(assetId);
        try {
            recetaService.cargarReceta(receta);
        } catch (CommitStatusException | EndorseException | CommitException | SubmitException e) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);

        }

        return new ResponseEntity<>(receta, HttpStatus.OK);
    }

    @PostMapping("/obtener")
    public ResponseEntity<Receta> find(String id) {
        try {
            return new ResponseEntity<>(recetaService.obtenerReceta(id), HttpStatus.OK);
        } catch (IOException | GatewayException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }
}
