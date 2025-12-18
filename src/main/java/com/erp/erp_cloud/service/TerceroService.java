package com.erp.erp_cloud.service;

import com.erp.erp_cloud.dto.TerceroRequest;
import com.erp.erp_cloud.entity.Tercero;
import com.erp.erp_cloud.repository.TerceroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TerceroService {

    private final TerceroRepository terceroRepository;

    public Tercero create(TerceroRequest request) {

        // Validar duplicado
        Optional<Tercero> existente =
                terceroRepository.findByNumeroDocumento(request.getNumeroDocumento());

        if (existente.isPresent()) {
            throw new IllegalArgumentException(
                    "Ya existe un tercero con ese número de documento"
            );
        }

        Tercero tercero = new Tercero();



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
    public List<Tercero> listAll() {
        return terceroRepository.findAll();
    }

public Tercero getByNumeroDocumento(String numeroDocumento){

    return terceroRepository
            .findByNumeroDocumento(numeroDocumento)
            .orElseThrow(() -> new IllegalArgumentException(
                    "No existe un tercero con el número de documento " + numeroDocumento
            ));
}



}
