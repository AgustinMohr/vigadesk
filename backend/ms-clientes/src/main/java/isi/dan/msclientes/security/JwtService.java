package isi.dan.msclientes.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import isi.dan.msclientes.model.UsuarioCliente;

// Genera tokens JWT firmados (HS256) para los usuarios que loguean.
// El secreto debe ser el mismo en ms-clientes y en dan-gateway: aca se
// firma, en el gateway se verifica.
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:7200}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(UsuarioCliente usuario) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(usuario.getEmail())
                .claim("nombre", usuario.getNombre())
                .claim("rol", usuario.getRol() != null ? usuario.getRol() : "CLIENTE")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)));

        // El claim clienteId solo existe para usuarios CLIENTE (su empresa)
        if (usuario.getCliente() != null) {
            builder.claim("clienteId", usuario.getCliente().getId());
        }

        return builder.signWith(key).compact();
    }
}
