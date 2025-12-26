package com.erp.erp_cloud.entity;
import jakarta.persistence.*;
import lombok.*;


@Entity
@Table(name = "countries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Country {

    @Id
    private Integer id;

    @Column(length = 2, nullable = false, unique = true)
    private String iso2;

    @Column(length = 3, nullable = false, unique = true)
    private String iso3;

    @Column(name = "phone_prefix", nullable = false)
    private Integer phonePrefix;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(length = 16)
    private String continent;

    @Column(length = 32)
    private String subcontinent;

    @Column(name = "currency_code", length = 3)
    private String currencyCode;

    @Column(name = "currency_name", length = 100)
    private String currencyName;
}
