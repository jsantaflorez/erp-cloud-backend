package com.erp.erp_cloud.entity;


import com.erp.erp_cloud.enums.TaxRegime;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(
        name = "t_empresas",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"nit", "id_pais"}),
                @UniqueConstraint(columnNames = {"tenant_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Company implements Serializable {

    private static final long serialVersionUID = 1L;

    // --------------------
    // IDENTIDAD
    // --------------------

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "empresa_id")
    private Long id;

    @Column(name = "razon_social", nullable = false, length = 150)
    private String razonSocial;

    @Column(name = "nombre_comercial", length = 150)
    private String nombreComercial;

    // --------------------
    // IDENTIFICACIÓN FISCAL
    // --------------------

    @Column(name = "nit", nullable = false, length = 20)
    private String nit;

    @Column(name = "digito_verificacion", length = 2)
    private String digitoVerificacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "regimen_fiscal", nullable = false, length = 50)
    private TaxRegime regimenFiscal;

    // --------------------
    // LOCALIZACIÓN
    // --------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_pais", nullable = false)
    private Country pais;

    @Column(name = "codigo_municipio", length = 10)
    private String codigoMunicipio; // Código DANE / equivalente internacional

    // --------------------
    // CONTACTO
    // --------------------

    @Column(length = 200)
    private String direccion;

    @Column(length = 50)
    private String telefono;

    @Column(nullable = false, length = 100)
    private String email;

    // --------------------
    // MULTI-TENANT / CLOUD
    // --------------------

    @Column(name = "tenant_id", nullable = false, length = 50)
    private String tenantId;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "logo_url")
    private String logoUrl;
}

