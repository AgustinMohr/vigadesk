package isi.dan.msclientes.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import isi.dan.msclientes.model.Cliente;
import isi.dan.msclientes.model.UsuarioCliente;

class JwtServiceTest {

    private static final String SECRET = "test-secret-para-tests-de-vigadesk-1234567890";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 7200);
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Test
    void generateToken_ShouldEmbedUserData() {
        UsuarioCliente usuario = new UsuarioCliente();
        usuario.setEmail("admin@example.com");
        usuario.setNombre("Admin");
        usuario.setRol("ADMIN");

        String token = jwtService.generateToken(usuario);

        Claims claims = parse(token);
        assertThat(claims.getSubject()).isEqualTo("admin@example.com");
        assertThat(claims.get("nombre", String.class)).isEqualTo("Admin");
        assertThat(claims.get("rol", String.class)).isEqualTo("ADMIN");
        assertThat(claims.get("clienteId")).isNull();
        assertThat(claims.getExpiration()).isAfter(new Date());
    }

    @Test
    void generateToken_ForCliente_ShouldEmbedClienteIdAndDefaultRol() {
        Cliente cliente = new Cliente();
        cliente.setId(7);

        UsuarioCliente usuario = new UsuarioCliente();
        usuario.setEmail("juan@constructorasur.com");
        usuario.setNombre("Juan");
        usuario.setCliente(cliente);

        String token = jwtService.generateToken(usuario);

        Claims claims = parse(token);
        assertThat(claims.get("clienteId", Integer.class)).isEqualTo(7);
        assertThat(claims.get("rol", String.class)).isEqualTo("CLIENTE");
    }

    @Test
    void generateToken_ShouldHonorExpiration() {
        jwtService = new JwtService(SECRET, 3600);
        UsuarioCliente usuario = new UsuarioCliente();
        usuario.setEmail("a@b.com");
        usuario.setNombre("A");

        String token = jwtService.generateToken(usuario);

        Claims claims = parse(token);
        long diffSec = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
        assertThat(diffSec).isEqualTo(3600);
    }
}
