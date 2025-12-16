package com.erp.erp_cloud.model;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity
@Table(name = "t_terceros")

public class Tercero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)



    private Long idTercero;

    private String tipoDocumento;
    private String numeroDocumento;
    private Integer digitoVerificacion;

    private String tipoPersona;   // NATURAL / JURIDICA
    private String regimen;       // SIMPLE / ORDINARIO / ESPECIAL / ETC

    private String primerNombre;
    private String segundoNombre;
    private String primerApellido;
    private String segundoApellido;

    private String razonSocial;

    private String correo;
    private String celular;
    private String telefono;
    private String direccion;

    private String codigoMunicipio;
}


