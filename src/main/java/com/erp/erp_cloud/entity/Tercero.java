package com.erp.erp_cloud.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(
        name = "t_terceros",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_numero_documento", columnNames = "numero_documento")
        }
)
@Data
public class Tercero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tercero")
    private Long idTercero;

    /**
     * Editable en caso de error de digitación
     */
    @Column(name = "numero_documento", length = 20, nullable = false)
    private String numeroDocumento;

    @Column(name = "tipo_documento", length = 5, nullable = false)
    private String tipoDocumento;

    @Column(name = "digito_verificacion")
    private Byte digitoVerificacion;

    @Column(name = "tipo_persona", length = 20, nullable = false)
    private String tipoPersona;

    @Column(name = "regimen", length = 50, nullable = false)
    private String regimen;

    @Column(name = "primer_nombre", length = 50)
    private String primerNombre;

    @Column(name = "segundo_nombre", length = 50)
    private String segundoNombre;

    @Column(name = "primer_apellido", length = 50)
    private String primerApellido;

    @Column(name = "segundo_apellido", length = 50)
    private String segundoApellido;

    @Column(name = "razon_social", length = 150)
    private String razonSocial;

    @Column(name = "correo", length = 150)
    private String correo;

    @Column(name = "celular", length = 20)
    private String celular;

    @Column(name = "telefono", length = 20)
    private String telefono;

    @Column(name = "direccion", length = 200)
    private String direccion;

    @Column(name = "codigo_municipio", length = 10)
    private String codigoMunicipio;
}
