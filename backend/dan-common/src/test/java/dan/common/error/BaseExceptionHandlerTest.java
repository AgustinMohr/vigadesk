package dan.common.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Contrato del formato de error: {timestamp, status, code, message, path} y, en validacion, errors.
 * Es la suite unica del manejador; los servicios solo prueban el mapeo de sus excepciones de dominio.
 */
class BaseExceptionHandlerTest {

    static class Entrada {
        @NotBlank(message = "El nombre es obligatorio")
        public String nombre;
        @Positive(message = "La cantidad debe ser mayor a cero")
        public Integer cantidad;
    }

    @RestController
    static class ControladorDePrueba {
        @GetMapping("/no-encontrado")
        String noEncontrado() {
            throw new RecursoNoEncontradoException("Cliente 7 no encontrado");
        }

        @GetMapping("/prohibido")
        String prohibido() {
            throw new AccesoDenegadoException("No podes acceder a datos de otro cliente");
        }

        @GetMapping("/conflicto")
        String conflicto() {
            throw new ConflictoException("Estado invalido para la operacion");
        }

        @GetMapping("/no-procesable")
        String noProcesable() {
            throw new SolicitudNoProcesableException("La obra no esta habilitada");
        }

        @GetMapping("/no-disponible")
        String noDisponible() {
            throw new ServicioNoDisponibleException("No se pudo reservar el stock", new RuntimeException("secreto"));
        }

        @GetMapping("/argumento")
        String argumento() {
            throw new IllegalArgumentException("El descuento debe estar entre 0 y 100");
        }

        @GetMapping("/runtime")
        String runtime() {
            throw new RuntimeException("jdbc:postgresql://pg-dan:5432/db password=abc");
        }

        @GetMapping("/estado")
        String estado() {
            throw new IllegalStateException("detalle interno del estado");
        }

        @GetMapping("/subida")
        String subida() {
            throw new MaxUploadSizeExceededException(1024);
        }

        @GetMapping("/parametro")
        String parametro(@RequestParam Integer id) {
            return "ok" + id;
        }

        @PostMapping("/validar")
        String validar(@RequestBody @Valid Entrada entrada) {
            return "ok";
        }
    }

    @RestControllerAdvice
    static class Manejador extends BaseExceptionHandler {
    }

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ControladorDePrueba()).setControllerAdvice(new Manejador()).build();
    }

    @Test
    void apiException_ShouldKeepStatusCodeMessageAndPath() throws Exception {
        mvc.perform(get("/no-encontrado"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Cliente 7 no encontrado"))
                .andExpect(jsonPath("$.path").value("/no-encontrado"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors").doesNotExist());
        mvc.perform(get("/prohibido")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/conflicto")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mvc.perform(get("/no-procesable")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"));
    }

    @Test
    void serviceUnavailable_ShouldShowItsMessageButNeverTheCause() throws Exception {
        mvc.perform(get("/no-disponible"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("No se pudo reservar el stock"))
                .andExpect(jsonPath("$.message", not(containsString("secreto"))));
    }

    @Test
    void illegalArgument_ShouldBeBadRequestWithItsMessage() throws Exception {
        mvc.perform(get("/argumento"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("El descuento debe estar entre 0 y 100"));
    }

    @Test
    void runtimeException_ShouldBeInternalErrorWithGenericMessage() throws Exception {
        mvc.perform(get("/runtime"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value(BaseExceptionHandler.MENSAJE_ERROR_INTERNO))
                .andExpect(jsonPath("$.message", not(containsString("jdbc"))));
    }

    @Test
    void illegalState_ShouldNotLeakItsMessage() throws Exception {
        mvc.perform(get("/estado"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(BaseExceptionHandler.MENSAJE_ERROR_INTERNO));
    }

    @Test
    void uploadTooLarge_ShouldBe413() throws Exception {
        mvc.perform(get("/subida"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void validation_ShouldListFieldErrors() throws Exception {
        mvc.perform(post("/validar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"\",\"cantidad\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Hay campos invalidos"))
                .andExpect(jsonPath("$.errors.nombre").value("El nombre es obligatorio"))
                .andExpect(jsonPath("$.errors.cantidad").value("La cantidad debe ser mayor a cero"));
    }

    @Test
    void unreadableBody_ShouldBeBadRequest() throws Exception {
        mvc.perform(post("/validar").contentType(MediaType.APPLICATION_JSON).content("{no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("El cuerpo de la solicitud no es valido"))
                .andExpect(jsonPath("$.message", not(containsString("Jackson"))));
    }

    @Test
    void springMvcErrors_ShouldUseTheSameFormat() throws Exception {
        mvc.perform(get("/parametro"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.path").value("/parametro"));
        mvc.perform(get("/parametro").param("id", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/prohibido"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        mvc.perform(post("/validar").contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void unknownRoute_ShouldBe404NotFoundInsteadOf500() throws Exception {
        mvc.perform(get("/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
