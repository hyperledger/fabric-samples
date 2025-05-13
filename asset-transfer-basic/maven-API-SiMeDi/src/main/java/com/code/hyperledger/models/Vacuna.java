package com.code.hyperledger.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Vacuna {
    private String id;
    private String identifier;
    private String status;
    private String statusChange;
    private String statusReason;
    private String vaccinateCode;
    private String administradedProduct;
    private String manufacturer;
    private String lotNumber;
    private String expirationDate;
    private String patientDocumentNumber;
    private String reactions;
}

