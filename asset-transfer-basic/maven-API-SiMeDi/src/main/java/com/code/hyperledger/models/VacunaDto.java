package com.code.hyperledger.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class VacunaDto {
    private String identificador;
    private String status;
    //@JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private String statusChange;
    private String statusReason;
    private String vaccinateCode;
    private String administradedProduct;
    private String manufacturer;
    private String lotNumber;
    private String expirationDate;
    private String patientDocumentNumber;
    private String reactions;
    private String matricula;
    private String practitioner;
    private String practitionerDocumentNumber;
}
