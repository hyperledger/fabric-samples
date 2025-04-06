package com.code.hyperledger.controllers;

import com.code.hyperledger.Utils.Hashing;
import com.code.hyperledger.models.AssetIdDto;
import com.code.hyperledger.models.Vacuna;
import com.code.hyperledger.services.VacunaService;
import org.hyperledger.fabric.client.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vacunas")
public class VacunaController {

    @Autowired
    private VacunaService vacunaService;

    @PostMapping("/crear")
    public ResponseEntity<AssetIdDto> crearVacuna(@RequestBody Vacuna vacuna) {
        System.out.println("\n--> Submit Transaction: CrearVacuna");

        String now = LocalDateTime.now().toString();
        String dni = vacuna.getDniPaciente();
        String id = dni + now;
        String assetId = Hashing.sha256(id);
        vacuna.setId(assetId);

        AssetIdDto assetIdDto = new AssetIdDto();
        assetIdDto.setDni(dni);
        assetIdDto.setTimeStamp(now);

        try {
            vacunaService.registrarVacuna(vacuna);
            return new ResponseEntity<>(assetIdDto, HttpStatus.OK);
        } catch (CommitStatusException | EndorseException | CommitException | SubmitException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/obtener")
    public ResponseEntity<Vacuna> obtenerVacuna(@RequestBody Map<String, String> requestBody) {
        try {
            String id = requestBody.get("id");
            if (id == null || id.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            Vacuna vacuna = vacunaService.obtenerVacuna(id);
            return new ResponseEntity<>(vacuna, HttpStatus.OK);
        } catch (IOException | GatewayException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        }
    }

    @GetMapping("/todas")
    public ResponseEntity<List<Vacuna>> obtenerTodasLasVacunas() {
        try {
            List<Vacuna> vacunas = vacunaService.obtenerTodasLasVacunas();
            return new ResponseEntity<>(vacunas, HttpStatus.OK);
        } catch (IOException | GatewayException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/filtrar")
    public ResponseEntity<List<Vacuna>> obtenerVacunasPorDniYEstado(@RequestBody Map<String, String> filtros) {
        try {
            String dni = filtros.get("dni");
            String estado = filtros.get("estado"); // puede venir null o vacío

            if (dni == null || dni.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            List<Vacuna> vacunas = vacunaService.obtenerVacunasPorDniYEstado(dni, estado);
            return new ResponseEntity<>(vacunas, HttpStatus.OK);
        } catch (IOException | GatewayException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
