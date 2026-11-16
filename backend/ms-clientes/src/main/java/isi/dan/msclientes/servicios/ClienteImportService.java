package isi.dan.msclientes.servicios;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import isi.dan.msclientes.dao.ClienteRepository;
import isi.dan.msclientes.dto.ImportResult;
import isi.dan.msclientes.model.Cliente;

@Service
public class ClienteImportService {

    @Autowired
    private ClienteRepository clienteRepository;

    @Value("${cliente.maximo-descubierto.default:10000}")
    private BigDecimal maximoDescubiertoDefault;

    @Transactional
    public ImportResult importar(InputStream csv) {
        int creados = 0;
        int actualizados = 0;

        try (Reader reader = new InputStreamReader(csv, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreHeaderCase(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord row : parser) {
                String nombre = requerido(row, "nombre");
                String email = requerido(row, "correoElectronico");
                BigDecimal maximo = decimalOpcional(row, "maximoDescubierto");
                BigDecimal saldoInicial = decimalOpcional(row, "saldoInicial");
                if (maximo != null && maximo.signum() < 0) {
                    throw error(row, "maximoDescubierto no puede ser negativo");
                }
                if (saldoInicial != null && saldoInicial.signum() < 0) {
                    throw error(row, "saldoInicial no puede ser negativo");
                }

                Optional<Cliente> existente = clienteRepository.findByCorreoElectronico(email);
                Cliente cliente = existente.orElseGet(Cliente::new);
                cliente.setNombre(nombre);
                cliente.setCorreoElectronico(email);
                cliente.setCuit(texto(row, "cuit"));
                cliente.setMaximoDescubierto(maximo == null ? maximoDescubiertoDefault : maximo);
                cliente.setSaldoInicial(saldoInicial == null ? BigDecimal.ZERO : saldoInicial);
                cliente.setHabilitado(true);

                clienteRepository.save(cliente);
                if (existente.isPresent()) {
                    actualizados++;
                } else {
                    creados++;
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el CSV de clientes: " + e.getMessage(), e);
        }

        return new ImportResult(creados, actualizados,
                "Clientes importados: " + creados + " creados, " + actualizados + " actualizados");
    }

    private String requerido(CSVRecord row, String column) {
        String value = texto(row, column);
        if (value == null) {
            throw error(row, "falta la columna " + column);
        }
        return value;
    }

    private String texto(CSVRecord row, String column) {
        if (!row.isMapped(column)) {
            return null;
        }
        String value = row.get(column);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BigDecimal decimalOpcional(CSVRecord row, String column) {
        String value = texto(row, column);
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.replace(",", "."));
        } catch (NumberFormatException e) {
            throw error(row, column + " no es un numero valido: " + value);
        }
    }

    private IllegalArgumentException error(CSVRecord row, String message) {
        return new IllegalArgumentException("Fila " + row.getRecordNumber() + ": " + message);
    }
}
