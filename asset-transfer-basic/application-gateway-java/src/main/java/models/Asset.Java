package models;

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

    public Asset(String id, String owner, String prescripcionAnteriorId, String status, LocalDateTime statusChange,
                 String prioridad, String medicacion, String razon, String notas, String periodoDeTratamiento,
                 String instruccionesTratamiento, String periodoDeValidez, String dniPaciente,
                 LocalDate fechaDeAutorizacion, int cantidad, LocalDate expectedSupplyDuration) {
        this.id = id;
        this.owner = owner;
        this.prescripcionAnteriorId = prescripcionAnteriorId;
        this.status = status;
        this.statusChange = statusChange;
        this.prioridad = prioridad;
        this.medicacion = medicacion;
        this.razon = razon;
        this.notas = notas;
        this.periodoDeTratamiento = periodoDeTratamiento;
        this.instruccionesTratamiento = instruccionesTratamiento;
        this.periodoDeValidez = periodoDeValidez;
        this.dniPaciente = dniPaciente;
        this.fechaDeAutorizacion = fechaDeAutorizacion;
        this.cantidad = cantidad;
        this.expectedSupplyDuration = expectedSupplyDuration;
    }

    // Getters
    public String getId() { return id; }
    public String getOwner() { return owner; }
    public String getPrescripcionAnteriorId() { return prescripcionAnteriorId; }
    public String getStatus() { return status; }
    public LocalDateTime getStatusChange() { return statusChange; }
    public String getPrioridad() { return prioridad; }
    public String getMedicacion() { return medicacion; }
    public String getRazon() { return razon; }
    public String getNotas() { return notas; }
    public String getPeriodoDeTratamiento() { return periodoDeTratamiento; }
    public String getInstruccionesTratamiento() { return instruccionesTratamiento; }
    public String getPeriodoDeValidez() { return periodoDeValidez; }
    public String getDniPaciente() { return dniPaciente; }
    public LocalDate getFechaDeAutorizacion() { return fechaDeAutorizacion; }
    public int getCantidad() { return cantidad; }
    public LocalDate getExpectedSupplyDuration() { return expectedSupplyDuration; }
}
