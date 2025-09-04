package com.code.hyperledger.controllers;

import com.code.hyperledger.Utils.Hashing;
import com.code.hyperledger.models.AssetIdDto;
import com.code.hyperledger.models.Receta;
import com.code.hyperledger.models.RecetaDto;
import com.code.hyperledger.services.RecetaService;
import main.java.com.code.hyperledger.models.ResultadoPaginado;

import main.java.com.code.hyperledger.models.RecetaRequestDto;

import org.hyperledger.fabric.client.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/recetas")
public class RecetaController {

    private static final Logger logger = LoggerFactory.getLogger(RecetaController.class);

    @Autowired
    private RecetaService recetaService;

    @PostMapping("/crear")
    public ResponseEntity<AssetIdDto> crear(@RequestBody Receta receta) {
        String now = LocalDateTime.now().toString();
        String dni = receta.getPatientDocumentNumber();
        String id = dni + now;
        String assetId = Hashing.sha256(id);
        receta.setId(assetId);

        AssetIdDto assetIdDto = new AssetIdDto();
        assetIdDto.setDni(dni);
        assetIdDto.setTimeStamp(now);

        try {
            recetaService.cargarReceta(receta);
            return new ResponseEntity<>(assetIdDto, HttpStatus.OK);
        } catch (CommitStatusException | EndorseException | CommitException | SubmitException e) {
            System.err.println("Error al cargar la receta: " + e.getMessage());
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
    }

    @PostMapping("/obtener")
    public ResponseEntity<RecetaDto> find(@RequestBody RecetaRequestDto requestBody) {
        logger.info("Received request to obtain receta with ID: {}", requestBody.getId()); 

        try {
            String id = requestBody.getId();
            logger.debug("Searching for receta with ID: {}", id); 

            Receta receta = recetaService.obtenerReceta(id);
            logger.debug("Receta found: {}", receta);

            RecetaDto recetaDto = mapToDto(receta);
            logger.info("Receta DTO created successfully for ID: {}", id); 

            return new ResponseEntity<>(recetaDto, HttpStatus.OK);
        } catch (IOException e) {
            logger.error("IOException occurred while obtaining receta with ID: {}", requestBody.getId(), e);
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        } catch (GatewayException e) {
            logger.error("GatewayException occurred while obtaining receta with ID: {}", requestBody.getId(), e);
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        } catch (Exception e) {
            logger.error("Unexpected error occurred while obtaining receta with ID: {}", requestBody.getId(), e); 
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PostMapping("/todas")
    public ResponseEntity<List<RecetaDto>> obtenerRecetasPorIds(@RequestBody Map<String, List<String>> requestBody) {
        try {
            List<String> ids = requestBody.get("ids");
            if (ids == null || ids.isEmpty()) {
                return new ResponseEntity<>(new ArrayList<>(), HttpStatus.OK);
            }

            List<Receta> recetas = recetaService.obtenerRecetasPorIds(ids);
            List<RecetaDto> recetasDto = new ArrayList<>();
            for (Receta receta : recetas) {
                recetasDto.add(mapToDto(receta));
            }

            System.out.println("Recetas obtenidas del service con los siguientes IDs:");

            return new ResponseEntity<>(recetasDto, HttpStatus.OK);

        } catch (IOException | GatewayException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            System.err.println("Error en submitTransaction: " + e.getMessage());
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/entregar")
    public ResponseEntity<Void> entregarReceta(@RequestBody Map<String, String> requestBody) {
        try {
            String id = requestBody.get("id");

            if (id == null || id.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            recetaService.entregarReceta(id);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (EndorseException | SubmitException | CommitStatusException | CommitException e) {
            e.printStackTrace(); 
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (GatewayException e) {
            e.printStackTrace(); 
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace(); 
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/firmar")
    public ResponseEntity<Void> firmarReceta(@RequestBody Map<String, String> requestBody) {
        try {
            String id = requestBody.get("id");
            String signature = requestBody.get("signature");

            if (id == null || id.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }

            System.out.println("\n--> Submit Transaction: FirmarReceta");

            recetaService.firmarReceta(id, signature);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (EndorseException | SubmitException | CommitStatusException | CommitException e) {
            e.printStackTrace(); 
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (GatewayException e) {
            e.printStackTrace(); 
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace(); 
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/todasTest")
    public List<Receta> obtenerTodasTest() throws Exception {
        return recetaService.obtenerTodasLasRecetas();
    }

    @PostMapping("/borrar")
    public ResponseEntity<RecetaDto> delete(@RequestBody Map<String, String> requestBody) {
        try {
            String id = requestBody.get("id");

            if (id == null || id.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            recetaService.borrarReceta(id);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (EndorseException | SubmitException | CommitStatusException | CommitException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (GatewayException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            e.printStackTrace(); 
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/obtener/paginado")
    public ResponseEntity<ResultadoPaginado<RecetaDto>> obtenerRecetasPaginado(
            @RequestParam String dni,
            @RequestParam List<String> estados,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(defaultValue = "") String bookmark) {
        try {
            ResultadoPaginado<RecetaDto> recetas = recetaService
                    .obtenerRecetasPorDniYEstadoPaginado(dni, estados, pageSize, bookmark);
            return new ResponseEntity<>(recetas, HttpStatus.OK);
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private RecetaDto mapToDto(Receta receta) {
        RecetaDto dto = new RecetaDto();
        dto.setId(receta.getId());
        dto.setIdentifier(receta.getIdentifier());
        dto.setOwner(receta.getOwner());
        dto.setPrescripcionAnteriorId(receta.getPrescripcionAnteriorId());
        dto.setStatus(receta.getStatus());
        dto.setStatusChange(receta.getStatusChange());
        dto.setPrioridad(receta.getPrioridad());
        dto.setMedicacion(receta.getMedicacion());
        dto.setRazon(receta.getRazon());
        dto.setNotas(receta.getNotas());
        dto.setPeriodoDeTratamiento(receta.getPeriodoDeTratamiento());
        dto.setInstruccionesTratamiento(receta.getInstruccionesTratamiento());
        dto.setPeriodoDeValidez(receta.getPeriodoDeValidez());
        dto.setPatientDocumentNumber(receta.getPatientDocumentNumber());
        dto.setFechaDeAutorizacion(receta.getFechaDeAutorizacion());
        dto.setCantidad(receta.getCantidad());
        dto.setExpectedSupplyDuration(receta.getExpectedSupplyDuration());
        dto.setMatricula(receta.getMatricula());
        dto.setPractitioner(receta.getPractitioner());
        dto.setPractitionerDocumentNumber(receta.getPractitionerDocumentNumber());
        dto.setSignature(receta.getSignature());
        return dto;
    }
}
