package com.erp.erp_cloud.dto;



import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CityResponseDTO {
    private Long id;
    private String code;
    private String name;
}