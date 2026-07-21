package isi.dan.ms_productos.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import isi.dan.ms_productos.aop.LogExecutionTime;
import isi.dan.ms_productos.modelo.Categoria;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    @GetMapping
    @LogExecutionTime
    public List<Categoria> getAll() {
        return List.of(Categoria.values());
    }
}
