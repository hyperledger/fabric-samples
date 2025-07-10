package main.java.com.code.hyperledger.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResultadoPaginado<T> {
    private List<T> recetas;
    private String bookmark;
}
