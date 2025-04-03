package com.code.hyperledger.controllers;

import com.code.hyperledger.coso.Receta;
import com.code.hyperledger.coso.RecetaDto;
import com.code.hyperledger.coso.AssetIdDto;
import com.code.hyperledger.Utils.Hashing;
import com.code.hyperledger.services.RecetaService;
import org.hyperledger.fabric.client.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

@RestController

@RequestMapping("/recetas")
public class RecetaController {

    @Autowired
    private RecetaService recetaService;

    @PostMapping("/crear")
    public ResponseEntity<AssetIdDto> crear(@RequestBody Receta receta) {
        System.out.println("\n--> Submit Transaction: CreateAsset, creates new asset with all arguments");

        var now = LocalDateTime.now().toString();
        var dni = receta.getDniPaciente();
        var id = dni + now;
        String assetId = Hashing.sha256(id);
        receta.setId(assetId);
        var assetIdDto = new AssetIdDto();
        assetIdDto.setDni(dni);
        assetIdDto.setTimeStamp(now);
        try {
            recetaService.cargarReceta(receta);
        } catch (CommitStatusException | EndorseException | CommitException | SubmitException e) {
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);

        }

        return new ResponseEntity<>(assetIdDto, HttpStatus.OK);
    }

    @PostMapping("/obtener")
    public ResponseEntity<RecetaDto> find(@RequestBody Map<String, String> requestBody) {
        try {
            System.out.println("requestbody: " + requestBody);
            String id = requestBody.get("id");
            System.out.println("id: " + id);
            Receta receta = recetaService.obtenerReceta(id);
            RecetaDto recetaDto = new RecetaDto();
            
            recetaDto.setOwner(receta.getOwner());
            recetaDto.setPrescripcionAnteriorId(receta.getPrescripcionAnteriorId());
            recetaDto.setStatus(receta.getStatus());
            recetaDto.setStatusChange(receta.getStatusChange());
            recetaDto.setPrioridad(receta.getPrioridad());
            recetaDto.setMedicacion(receta.getMedicacion());
            recetaDto.setRazon(receta.getRazon());
            recetaDto.setNotas(receta.getNotas());
            recetaDto.setPeriodoDeTratamiento(receta.getPeriodoDeTratamiento());
            recetaDto.setInstruccionesTratamiento(receta.getInstruccionesTratamiento());
            recetaDto.setPeriodoDeValidez(receta.getPeriodoDeValidez());
            recetaDto.setDniPaciente(receta.getDniPaciente());
            recetaDto.setFechaDeAutorizacion(receta.getFechaDeAutorizacion());
            recetaDto.setCantidad(receta.getCantidad());
            recetaDto.setExpectedSupplyDuration(receta.getExpectedSupplyDuration());
            
            return new ResponseEntity<>(recetaDto, HttpStatus.OK);
        } catch (IOException | GatewayException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        }
    }

    @PostMapping("/todas")
    public ResponseEntity<List<RecetaDto>> obtenerRecetasPorIds(@RequestBody Map<String, List<String>> requestBody) {
        try {
            List<String> ids = requestBody.get("ids");
            if (ids == null || ids.isEmpty()) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            
            System.out.println("IDs solicitados: " + ids);
            List<Receta> recetas = recetaService.obtenerRecetasPorIds(ids);
            List<RecetaDto> recetasDto = new ArrayList<>();
            
            for (Receta receta : recetas) {
                RecetaDto recetaDto = new RecetaDto();
                recetaDto.setOwner(receta.getOwner());
                recetaDto.setPrescripcionAnteriorId(receta.getPrescripcionAnteriorId());
                recetaDto.setStatus(receta.getStatus());
                recetaDto.setStatusChange(receta.getStatusChange());
                recetaDto.setPrioridad(receta.getPrioridad());
                recetaDto.setMedicacion(receta.getMedicacion());
                recetaDto.setRazon(receta.getRazon());
                recetaDto.setNotas(receta.getNotas());
                recetaDto.setPeriodoDeTratamiento(receta.getPeriodoDeTratamiento());
                recetaDto.setInstruccionesTratamiento(receta.getInstruccionesTratamiento());
                recetaDto.setPeriodoDeValidez(receta.getPeriodoDeValidez());
                recetaDto.setDniPaciente(receta.getDniPaciente());
                recetaDto.setFechaDeAutorizacion(receta.getFechaDeAutorizacion());
                recetaDto.setCantidad(receta.getCantidad());
                recetaDto.setExpectedSupplyDuration(receta.getExpectedSupplyDuration());
                
                recetasDto.add(recetaDto);
            }
            
            return new ResponseEntity<>(recetasDto, HttpStatus.OK);
        } catch (IOException | GatewayException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
