package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.ThirdPartyRequest;
import com.erp.erp_cloud.entity.ThirdParty;
import com.erp.erp_cloud.repository.ThirdPartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ThirdPartyService {

    private final ThirdPartyRepository terceroRepository;

    public ThirdParty create(ThirdPartyRequest request) {

        // Validar duplicado
        Optional<ThirdParty> existente =
                terceroRepository.findByNumeroDocumento(request.getNumeroDocumento());

        if (existente.isPresent()) {
            throw new IllegalArgumentException(
                    "Ya existe un tercero con ese número de documento"
            );
        }

        ThirdParty tercero = new ThirdParty();



        tercero.setTipoDocumento(request.getTipoDocumento());
        tercero.setNumeroDocumento(request.getNumeroDocumento());
        tercero.setDigitoVerificacion(request.getDigitoVerificacion());
        tercero.setTipoPersona(request.getTipoPersona());
        tercero.setRegimen(request.getRegimen());
        tercero.setPrimerNombre(request.getPrimerNombre());
        tercero.setSegundoNombre(request.getSegundoNombre());
        tercero.setPrimerApellido(request.getPrimerApellido());
        tercero.setSegundoApellido(request.getSegundoApellido());
        tercero.setRazonSocial(request.getRazonSocial());
        tercero.setCorreo(request.getCorreo());
        tercero.setCelular(request.getCelular());
        tercero.setTelefono(request.getTelefono());
        tercero.setDireccion(request.getDireccion());
        tercero.setCodigoMunicipio(request.getCodigoMunicipio());

        return terceroRepository.save(tercero);
    }
    public List<ThirdParty> listAll() {
        return terceroRepository.findAll();
    }

public ThirdParty getByNumeroDocumento(String numeroDocumento){

    return terceroRepository
            .findByNumeroDocumento(numeroDocumento)
            .orElseThrow(() -> new IllegalArgumentException(
                    "No existe un tercero con el número de documento " + numeroDocumento
            ));
}



}
