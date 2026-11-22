package isi.dan.ms.pedidos.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import isi.dan.ms.pedidos.modelo.EstadoPedido;
import isi.dan.ms.pedidos.modelo.Pedido;

public interface PedidoRepository extends MongoRepository<Pedido, String> {

    @Query("{'_id': ?0, 'habilitado': {'$ne': false}}")
    Optional<Pedido> findByIdActivo(String id);

    @Query("{'cliente._id': ?0, 'estado': {'$in': ?1}, 'habilitado': {'$ne': false}}")
    List<Pedido> findByCliente_IdAndEstadoInActivos(Integer clienteId, List<EstadoPedido> estados);

    @Query("{'cliente._id': ?0, 'idempotencyKey': ?1}")
    Optional<Pedido> findByClienteIdAndIdempotencyKey(Integer clienteId, String idempotencyKey);
}
