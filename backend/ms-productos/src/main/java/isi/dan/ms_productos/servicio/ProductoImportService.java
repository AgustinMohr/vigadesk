package isi.dan.ms_productos.servicio;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import isi.dan.ms_productos.dao.ProductoRepository;
import isi.dan.ms_productos.dto.ImportResult;
import isi.dan.ms_productos.modelo.Categoria;
import isi.dan.ms_productos.modelo.Producto;
import isi.dan.ms_productos.modelo.UnidadMedida;

@Service
public class ProductoImportService {

    private final ProductoRepository productoRepository;
    private final Clock clock;

    public ProductoImportService(ProductoRepository productoRepository, Clock clock) {
        this.productoRepository = productoRepository;
        this.clock = clock;
    }

    @Transactional
    public ImportResult importar(InputStream csv, String actor) {
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
                BigDecimal precio = decimalRequerido(row, "precio");
                if (precio.signum() < 0) {
                    throw error(row, "precio no puede ser negativo");
                }

                String codigo = texto(row, "codigo");
                Optional<Producto> existente = codigo == null
                        ? Optional.empty()
                        : productoRepository.findFirstByCodigo(codigo);

                Producto producto = existente.orElseGet(Producto::new);
                producto.setCodigo(codigo);
                producto.setNombre(nombre);
                producto.setDescripcion(texto(row, "descripcion"));
                producto.setUnidadMedida(unidad(row));
                BigDecimal stockActual = decimalOpcional(row, "stockActual");
                BigDecimal stockMinimo = decimalOpcional(row, "stockMinimo");
                if (stockActual != null && stockActual.signum() < 0) {
                    throw error(row, "stockActual no puede ser negativo");
                }
                if (stockMinimo != null && stockMinimo.signum() < 0) {
                    throw error(row, "stockMinimo no puede ser negativo");
                }
                producto.setPrecio(precio);
                producto.setCosto(decimalOpcional(row, "costo"));
                producto.setStockActual(stockActual == null ? BigDecimal.ZERO : stockActual);
                producto.setStockMinimo(stockMinimo == null ? BigDecimal.ZERO : stockMinimo);
                if (producto.getDescuentoPromocional() == null) {
                    producto.setDescuentoPromocional(BigDecimal.ZERO);
                }
                Categoria categoria = categoria(row);
                if (categoria != null) {
                    producto.setCategoria(categoria);
                }
                producto.setHabilitado(true);
                producto.setActualizadoPor(actor);
                producto.setFechaActualizacion(Instant.now(clock));

                productoRepository.save(producto);
                if (existente.isPresent()) {
                    actualizados++;
                } else {
                    creados++;
                }
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("No se pudo leer el CSV de productos: " + e.getMessage(), e);
        }

        return new ImportResult(creados, actualizados,
                "Productos importados: " + creados + " creados, " + actualizados + " actualizados");
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

    private BigDecimal decimalRequerido(CSVRecord row, String column) {
        BigDecimal value = decimalOpcional(row, column);
        if (value == null) {
            throw error(row, "falta la columna " + column);
        }
        return value;
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

    private UnidadMedida unidad(CSVRecord row) {
        String value = texto(row, "unidadMedida");
        if (value == null) {
            return UnidadMedida.UNIDAD;
        }
        try {
            return UnidadMedida.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw error(row, "unidadMedida invalida: " + value);
        }
    }

    private Categoria categoria(CSVRecord row) {
        String value = texto(row, "categoria");
        if (value == null) {
            return null;
        }
        try {
            return Categoria.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw error(row, "categoria invalida: " + value);
        }
    }

    private IllegalArgumentException error(CSVRecord row, String message) {
        return new IllegalArgumentException("Fila " + row.getRecordNumber() + ": " + message);
    }
}
