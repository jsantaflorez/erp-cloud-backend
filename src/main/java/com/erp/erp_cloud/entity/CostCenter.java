package com.erp.erp_cloud.entity;



import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;


import java.io.Serializable;

import java.util.List;

@Entity
@Table(name = "cost_centers")
@Data
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class CostCenter implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    private Integer level;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "allows_movement")
    private boolean allowsMovement; // Indica si se puede usar en asientos

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    @JsonIgnoreProperties("children")
    private CostCenter parent;

    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
    private List<CostCenter> children;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore // Para evitar errores de serialización
    private Company company;
}