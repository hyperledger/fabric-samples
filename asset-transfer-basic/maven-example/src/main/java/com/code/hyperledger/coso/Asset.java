package com.code.hyperledger.coso;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Asset {
    private String id;
    private String owner;
    private String prescripcionAnteriorId;
    private String status;
    private LocalDateTime statusChange;
    private String prioridad;
    private String medicacion;
    private String razon;
    private String notas;
    private String periodoDeTratamiento;
    private String instruccionesTratamiento;
    private String periodoDeValidez;
    private String dniPaciente;
    private LocalDate fechaDeAutorizacion;
    private int cantidad;
    private LocalDate expectedSupplyDuration;

}
