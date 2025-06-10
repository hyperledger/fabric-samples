package com.code.hyperledger.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RecetaDto {
    private String id;
    private String identifier;
    private String owner;
    private String prescripcionAnteriorId;
    private String status;
    // @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private String statusChange;
    private String prioridad;
    private String medicacion;
    private String razon;
    private String notas;
    private String periodoDeTratamiento;
    private String instruccionesTratamiento;
    private String periodoDeValidez;
    private String patientDocumentNumber;
    // @JsonFormat(pattern = "yyyy-MM-dd")
    private String fechaDeAutorizacion;
    private String cantidad;
    // @JsonFormat(pattern = "yyyy-MM-dd")
    private String expectedSupplyDuration;
    private String practitioner;
    private String practitionerDocumentNumber;
    private String signature;
}
