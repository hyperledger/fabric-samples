package com.code.hyperledger.coso;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Receta {
    @JsonProperty("ID")
    private String id;
    @JsonProperty("Owner")
    private String owner;
    @JsonProperty("PrescripcionAnteriorId")
    private String prescripcionAnteriorId;
    @JsonProperty("Status")
    private String status;
    @JsonProperty("StatusChange")
    private LocalDateTime statusChange;
    @JsonProperty("Prioridad")
    private String prioridad;
    @JsonProperty("Medicacion")
    private String medicacion;
    @JsonProperty("Razon")
    private String razon;
    @JsonProperty("Notas")
    private String notas;
    @JsonProperty("PeriodoDeTratamiento")
    private String periodoDeTratamiento;
    @JsonProperty("InstruccionesTratamiento")
    private String instruccionesTratamiento;
    @JsonProperty("PeriodoDeValidez")
    private String periodoDeValidez;
    @JsonProperty("DniPaciente")
    private String dniPaciente;
    @JsonProperty("FechaDeAutorizacion")
    private LocalDate fechaDeAutorizacion;
    @JsonProperty("Cantidad")
    private int cantidad;
    @JsonProperty("ExpectedSupplyDuration")
    private LocalDate expectedSupplyDuration;
}
