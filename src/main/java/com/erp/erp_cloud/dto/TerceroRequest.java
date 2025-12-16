package com.erp.erp_cloud.dto;



import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;



import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TerceroRequest {

    // --- Identificación ---

    // tipo_documento: VARCHAR(5) NOT NULL
    @NotBlank(message = "El tipo de documento es obligatorio.")
    @Size(max = 5, message = "El tipo de documento no puede exceder los 5 caracteres.")
    private String tipoDocumento;

    // numero_documento: VARCHAR(20) NOT NULL
    @NotBlank(message = "El número de documento es obligatorio.")
    @Size(max = 20, message = "El número de documento no puede exceder los 20 caracteres.")
    private String numeroDocumento;

    // digito_verificacion: TINYINT NULL
   @Min(value = 0, message = "El dígito de verificación debe ser 0 o mayor.")
    @Max(value = 9, message = "El dígito de verificación no debe ser mayor a 9.")
    private Byte digitoVerificacion; // Usamos Byte o Integer

    // --- Tipo y Régimen ---

    // tipo_persona: VARCHAR(20) NOT NULL
    @NotBlank(message = "El tipo de persona es obligatorio (ej: 'Natural' o 'Juridica').")
    @Size(max = 20, message = "El tipo de persona no puede exceder los 20 caracteres.")
    private String tipoPersona;

    // regimen: VARCHAR(50) NOT NULL
    @NotBlank(message = "El régimen es obligatorio.")
    @Size(max = 50, message = "El régimen no puede exceder los 50 caracteres.")
    private String regimen;

    // --- Nombres (Natural) o Razón Social (Jurídica) ---

    // primer_nombre: VARCHAR(50) NULL
    @Size(max = 50, message = "El primer nombre no puede exceder los 50 caracteres.")
    private String primerNombre;

    // segundo_nombre: VARCHAR(50) NULL
    @Size(max = 50, message = "El segundo nombre no puede exceder los 50 caracteres.")
    private String segundoNombre;

    // primer_apellido: VARCHAR(50) NULL
    @Size(max = 50, message = "El primer apellido no puede exceder los 50 caracteres.")
    private String primerApellido;

    // segundo_apellido: VARCHAR(50) NULL
    @Size(max = 50, message = "El segundo apellido no puede exceder los 50 caracteres.")
    private String segundoApellido;

    // razon_social: VARCHAR(150) NULL
    @Size(max = 150, message = "La razón social no puede exceder los 150 caracteres.")
    private String razonSocial;


    // --- Contacto y Ubicación ---

    // correo: VARCHAR(150) NULL
    @Email(message = "El formato del correo electrónico es inválido.")
    @Size(max = 150, message = "El correo electrónico no puede exceder los 150 caracteres.")
    private String correo;

    // celular: VARCHAR(20) NULL
    @Size(max = 20, message = "El número de celular no puede exceder los 20 caracteres.")
    private String celular;

    // telefono: VARCHAR(20) NULL
    @Size(max = 20, message = "El número de teléfono no puede exceder los 20 caracteres.")
    private String telefono;

    // direccion: VARCHAR(200) NULL
    @Size(max = 200, message = "La dirección no puede exceder los 200 caracteres.")
    private String direccion;

    // codigo_municipio: VARCHAR(10) NULL
    @Size(max = 10, message = "El código de municipio no puede exceder los 10 caracteres.")
    private String codigoMunicipio;

}