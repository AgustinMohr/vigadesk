db = db.getSiblingDB('pedidosdb');

db.pedidos.insertMany([
  // =========================================================================
  //  CLIENTE 1 — "Constructora del Sur" (maximoDescubierto ≈ 75000)
  //  ACEPTADO + EN_PREPARACION suman ~43990 → disponible ~31010
  // =========================================================================

  // --- P1: EN_PREPARACION, crédito OK + stock OK (aporta 19950 al gastado)
  {
    _id: "1",
    fecha: ISODate("2024-11-01T10:00:00Z"),
    numeroPedido: 100001,
    usuario: "jperez",
    observaciones: "Pedido de materiales para obra en Av. Siempre Viva",
    cliente: { _id: 1, nombre: "Constructora del Sur", correoElectronico: "contacto@constsur.com", cuit: "30-12345678-9" },
    obra: { _id: 1, direccion: "Av. Siempre Viva 742" },
    total: 19950,
    estado: "EN_PREPARACION",
    detalle: [
      { producto: { _id: 1, nombre: "Cemento Portland CPC 50 kg", descripcion: "Cemento Portland normal para construcción civil", precio: 8500.00 }, cantidad: 2, precioUnitario: 8500.00, descuento: 0, precioFinal: 17000.00 },
      { producto: { _id: 41, nombre: "Rodillo de Pete 23 cm", descripcion: "Rodillo para pintura", precio: 450.00 }, cantidad: 3, precioUnitario: 450.00, descuento: 0, precioFinal: 1350.00 },
      { producto: { _id: 63, nombre: "Codo PVC 45\u00b0 110 mm", descripcion: null, precio: 320.00 }, cantidad: 5, precioUnitario: 320.00, descuento: 0, precioFinal: 1600.00 }
    ]
  },

  // --- P2: EN_PREPARACION, crédito OK + stock OK (aporta 13940 al gastado)
  {
    _id: "2",
    fecha: ISODate("2024-11-05T14:30:00Z"),
    numeroPedido: 100002,
    usuario: "jperez",
    observaciones: "Placas y perfiles para tabiques interiores",
    cliente: { _id: 1, nombre: "Constructora del Sur", correoElectronico: "contacto@constsur.com", cuit: "30-12345678-9" },
    obra: { _id: 1, direccion: "Av. Siempre Viva 742" },
    total: 13940,
    estado: "EN_PREPARACION",
    detalle: [
      { producto: { _id: 2, nombre: "Placa de Yeso 12.7 mm 1.20 x 2.40 m", descripcion: "Placa de yeso est\u00e1ndar para tabiques interiores", precio: 4200.00 }, cantidad: 3, precioUnitario: 4200.00, descuento: 10, precioFinal: 11340.00 },
      { producto: { _id: 3, nombre: "Perfil Met\u00e1lico U 34x34x3000 mm", descripcion: "Perfil met\u00e1lico galvanizado para estructura de tabiques", precio: 650.00 }, cantidad: 4, precioUnitario: 650.00, descuento: 0, precioFinal: 2600.00 }
    ]
  },

  // --- P3: ACEPTADO, crédito OK pero stock pendiente (aporta 10100 al gastado)
  {
    _id: "3",
    fecha: ISODate("2024-11-10T08:15:00Z"),
    numeroPedido: 100003,
    usuario: "jperez",
    observaciones: "Materiales para terminaciones - stock a confirmar",
    cliente: { _id: 1, nombre: "Constructora del Sur", correoElectronico: "contacto@constsur.com", cuit: "30-12345678-9" },
    obra: { _id: 2, direccion: "Calle Falsa 123" },
    total: 10100,
    estado: "ACEPTADO",
    detalle: [
      { producto: { _id: 5, nombre: "Yeso Premium 40 kg", descripcion: "Yeso fino para terminaciones interiores", precio: 2200.00 }, cantidad: 3, precioUnitario: 2200.00, descuento: 0, precioFinal: 6600.00 },
      { producto: { _id: 40, nombre: "Pintura L\u00e1tex Blanco 20L - Sin m\u00ednimo", descripcion: "Pintura al l\u00e1tex para interiores", precio: 3500.00 }, cantidad: 1, precioUnitario: 3500.00, descuento: 0, precioFinal: 3500.00 }
    ]
  },

  // --- P4: ENTREGADO (cuenta para credito hasta que se registre un pago)
  {
    _id: "4",
    fecha: ISODate("2024-10-15T09:00:00Z"),
    numeroPedido: 100004,
    usuario: "jperez",
    observaciones: "Pedido completado y entregado",
    cliente: { _id: 1, nombre: "Constructora del Sur", correoElectronico: "contacto@constsur.com", cuit: "30-12345678-9" },
    obra: { _id: 1, direccion: "Av. Siempre Viva 742" },
    total: 25000,
    estado: "ENTREGADO",
    detalle: [
      { producto: { _id: 1, nombre: "Cemento Portland CPC 50 kg", descripcion: "Cemento Portland normal para construcci\u00f3n civil", precio: 8500.00 }, cantidad: 2, precioUnitario: 8500.00, descuento: 0, precioFinal: 17000.00 },
      { producto: { _id: 3, nombre: "Perfil Met\u00e1lico U 34x34x3000 mm", descripcion: "Perfil met\u00e1lico galvanizado para estructura de tabiques", precio: 650.00 }, cantidad: 10, precioUnitario: 650.00, descuento: 0, precioFinal: 6500.00 },
      { producto: { _id: 72, nombre: "Tornillo para Durlock 1\" x 1000u - Alta rotaci\u00f3n", descripcion: "Tornillo para fijaci\u00f3n de placas de yeso", precio: 1500.00 }, cantidad: 1, precioUnitario: 1500.00, descuento: 0, precioFinal: 1500.00 }
    ]
  },

  // --- P5: CANCELADO (NO cuenta para el gastado, libera stock al cancelarse)
  {
    _id: "5",
    fecha: ISODate("2024-10-20T11:00:00Z"),
    numeroPedido: 100005,
    usuario: "admin",
    observaciones: "Cancelado por cambios en la obra",
    cliente: { _id: 1, nombre: "Constructora del Sur", correoElectronico: "contacto@constsur.com", cuit: "30-12345678-9" },
    obra: { _id: 2, direccion: "Calle Falsa 123" },
    total: 4900,
    estado: "CANCELADO",
    detalle: [
      { producto: { _id: 50, nombre: "Masilla Pl\u00e1stica 1 kg - Sin descuento", descripcion: "Masilla para reparaci\u00f3n de superficies", precio: 900.00 }, cantidad: 3, precioUnitario: 900.00, descuento: 0, precioFinal: 2700.00 },
      { producto: { _id: 51, nombre: "Enduido Pl\u00e1stico 2 kg - Descuento cero", descripcion: "Enduido para interiores", precio: 1100.00 }, cantidad: 2, precioUnitario: 1100.00, descuento: 0, precioFinal: 2200.00 }
    ]
  },

  // --- P6: RECHAZADO (histórico — crédito excedido: total >> 75000)
  {
    _id: "6",
    fecha: ISODate("2024-11-12T16:45:00Z"),
    numeroPedido: 100006,
    usuario: "sgonzalez",
    observaciones: "RECHAZADO autom\u00e1ticamente: el total supera el m\u00e1ximo descubierto del cliente",
    cliente: { _id: 1, nombre: "Constructora del Sur", correoElectronico: "contacto@constsur.com", cuit: "30-12345678-9" },
    obra: { _id: 1, direccion: "Av. Siempre Viva 742" },
    total: 99999999.99,
    estado: "RECHAZADO",
    detalle: [
      { producto: { _id: 11, nombre: "Viga IPN 200 x 12 m - Acero", descripcion: "Viga de acero laminado para estructuras", precio: 99999999.99 }, cantidad: 1, precioUnitario: 99999999.99, descuento: 0, precioFinal: 99999999.99 }
    ]
  },

  // =========================================================================
  //  CLIENTE 999 — Inexistente en ms-clientes
  //  Dato historico del flujo anterior (fail-open). Hoy el alta falla cerrada.
  // =========================================================================

  // --- P7: RECIBIDO historico (cliente.id=999 no existe en ms-clientes)
  {
    _id: "7",
    fecha: ISODate("2024-11-15T07:30:00Z"),
    numeroPedido: 100007,
    usuario: "guest",
    observaciones: "Pedido de prueba con cliente inexistente - qued\u00f3 en RECIBIDO",
    cliente: { _id: 999, nombre: "Cliente Fantasma SAC", correoElectronico: "fake@nowhere.com", cuit: "30-99999999-9" },
    obra: { _id: 3, direccion: "Ruta 9 km 52 - Obra Norte" },
    total: 1600,
    estado: "RECIBIDO",
    detalle: [
      { producto: { _id: 63, nombre: "Codo PVC 45\u00b0 110 mm", descripcion: null, precio: 320.00 }, cantidad: 5, precioUnitario: 320.00, descuento: 0, precioFinal: 1600.00 }
    ]
  },

  // =========================================================================
  //  CLIENTE 2 — "Hormigones del Norte" (maximoDescubierto ≈ 30000)
  //  ACEPTADO + EN_PREPARACION suman ~29780 → disponible ~220
  //  Prácticamente sin margen para nuevos pedidos.
  // =========================================================================

  // --- P8: ACEPTADO, crédito OK pero stock insuficiente (aporta 18000 al gastado)
  {
    _id: "8",
    fecha: ISODate("2024-11-18T10:00:00Z"),
    numeroPedido: 100008,
    usuario: "jperez",
    observaciones: "Mortero en cantidad - stock a confirmar",
    cliente: { _id: 2, nombre: "Hormigones del Norte", correoElectronico: "ventas@hormigonesnorte.com", cuit: "30-87654321-0" },
    obra: { _id: 3, direccion: "Ruta 9 km 52 - Obra Norte" },
    total: 18000,
    estado: "ACEPTADO",
    detalle: [
      { producto: { _id: 4, nombre: "Mortero Pegamento Cer\u00e1mico 25 kg", descripcion: "Mortero adhesivo para colocaci\u00f3n de cer\u00e1micos", precio: 1800.00 }, cantidad: 10, precioUnitario: 1800.00, descuento: 0, precioFinal: 18000.00 }
    ]
  },

  // --- P9: ACEPTADO, crédito justo (aporta 10900 al gastado)
  {
    _id: "9",
    fecha: ISODate("2024-11-20T09:15:00Z"),
    numeroPedido: 100009,
    usuario: "admin",
    observaciones: "Cemento y guantes para obra",
    cliente: { _id: 2, nombre: "Hormigones del Norte", correoElectronico: "ventas@hormigonesnorte.com", cuit: "30-87654321-0" },
    obra: { _id: 3, direccion: "Ruta 9 km 52 - Obra Norte" },
    total: 10900,
    estado: "ACEPTADO",
    detalle: [
      { producto: { _id: 1, nombre: "Cemento Portland CPC 50 kg", descripcion: "Cemento Portland normal para construcci\u00f3n civil", precio: 8500.00 }, cantidad: 1, precioUnitario: 8500.00, descuento: 0, precioFinal: 8500.00 },
      { producto: { _id: 43, nombre: "Guantes de Trabajo Cuero - Stock exacto", descripcion: "Guantes de cuero para construcci\u00f3n", precio: 1200.00 }, cantidad: 2, precioUnitario: 1200.00, descuento: 0, precioFinal: 2400.00 }
    ]
  },

  // --- P10: EN_PREPARACION, crédito al límite (aporta 880 al gastado)
  {
    _id: "10",
    fecha: ISODate("2024-11-22T14:00:00Z"),
    numeroPedido: 100010,
    usuario: "admin",
    observaciones: "Pedido peque\u00f1o — cr\u00e9dito casi agotado",
    cliente: { _id: 2, nombre: "Hormigones del Norte", correoElectronico: "ventas@hormigonesnorte.com", cuit: "30-87654321-0" },
    obra: { _id: 3, direccion: "Ruta 9 km 52 - Obra Norte" },
    total: 880,
    estado: "EN_PREPARACION",
    detalle: [
      { producto: { _id: 42, nombre: "Disco de Corte 4 1/2\" - Stock cr\u00edtico", descripcion: "Disco de corte para amoladora", precio: 350.00 }, cantidad: 2, precioUnitario: 350.00, descuento: 0, precioFinal: 700.00 },
      { producto: { _id: 31, nombre: "Cinta de Empapelar 50 m - Stock m\u00ednimo", descripcion: "Rollo de cinta para juntas de placas de yeso", precio: 180.00 }, cantidad: 1, precioUnitario: 180.00, descuento: 0, precioFinal: 180.00 }
    ]
  },

  // --- P11: ENTREGADO (cuenta para credito hasta que se registre un pago)
  {
    _id: "11",
    fecha: ISODate("2024-09-01T08:00:00Z"),
    numeroPedido: 100011,
    usuario: "admin",
    observaciones: "Pedido hist\u00f3rico completado",
    cliente: { _id: 2, nombre: "Hormigones del Norte", correoElectronico: "ventas@hormigonesnorte.com", cuit: "30-87654321-0" },
    obra: { _id: 3, direccion: "Ruta 9 km 52 - Obra Norte" },
    total: 25780,
    estado: "ENTREGADO",
    detalle: [
      { producto: { _id: 13, nombre: "Ladrillo Hueco 18x18x33 Cer\u00e1mico x 1u", descripcion: "Ladrillo cer\u00e1mico para mamposter\u00eda", precio: 10000.00 }, cantidad: 2, precioUnitario: 10000.00, descuento: 0, precioFinal: 20000.00 },
      { producto: { _id: 52, nombre: "Fieltro Asf\u00e1ltico 10 m\u00b2 - 15% off", descripcion: "Fieltro asf\u00e1ltico para cubiertas", precio: 3400.00 }, cantidad: 2, precioUnitario: 3400.00, descuento: 15, precioFinal: 5780.00 }
    ]
  },

  // --- P12: CANCELADO (NO cuenta para el gastado)
  {
    _id: "12",
    fecha: ISODate("2024-09-15T13:00:00Z"),
    numeroPedido: 100012,
    usuario: "sgonzalez",
    observaciones: "Cancelado por el cliente",
    cliente: { _id: 2, nombre: "Hormigones del Norte", correoElectronico: "ventas@hormigonesnorte.com", cuit: "30-87654321-0" },
    obra: { _id: 3, direccion: "Ruta 9 km 52 - Obra Norte" },
    total: 3600,
    estado: "CANCELADO",
    detalle: [
      { producto: { _id: 30, nombre: "Pegamento Cer\u00e1mico 20 kg - Sin stock", descripcion: "Producto nuevo sin stock inicial", precio: 1200.00 }, cantidad: 3, precioUnitario: 1200.00, descuento: 0, precioFinal: 3600.00 }
    ]
  },

  // =========================================================================
  //  CLIENTE 3 — "Techos y Cielorrasos SRL" (maximoDescubierto ≈ 100000)
  //  ACEPTADO + EN_PREPARACION suman ~80300 → disponible ~19700
  //  Cliente con límite alto pero ya comprometido.
  // =========================================================================

  // --- P13: EN_PREPARACION, varios productos (aporta 54150 al gastado)
  {
    _id: "13",
    fecha: ISODate("2024-11-25T11:00:00Z"),
    numeroPedido: 100013,
    usuario: "jperez",
    observaciones: "Materiales para obra nueva - pedido grande",
    cliente: { _id: 3, nombre: "Techos y Cielorrasos SRL", correoElectronico: "obras@techoscielorrasos.com", cuit: "30-11111111-1" },
    obra: { _id: 2, direccion: "Calle Falsa 123" },
    total: 54150,
    estado: "EN_PREPARACION",
    detalle: [
      { producto: { _id: 1, nombre: "Cemento Portland CPC 50 kg", descripcion: "Cemento Portland normal para construcci\u00f3n civil", precio: 8500.00 }, cantidad: 3, precioUnitario: 8500.00, descuento: 0, precioFinal: 25500.00 },
      { producto: { _id: 2, nombre: "Placa de Yeso 12.7 mm 1.20 x 2.40 m", descripcion: "Placa de yeso est\u00e1ndar para tabiques interiores", precio: 4200.00 }, cantidad: 5, precioUnitario: 4200.00, descuento: 10, precioFinal: 18900.00 },
      { producto: { _id: 3, nombre: "Perfil Met\u00e1lico U 34x34x3000 mm", descripcion: "Perfil met\u00e1lico galvanizado para estructura de tabiques", precio: 650.00 }, cantidad: 15, precioUnitario: 650.00, descuento: 0, precioFinal: 9750.00 }
    ]
  },

  // --- P14: ACEPTADO, crédito OK pero stock pendiente (aporta 26150 al gastado)
  {
    _id: "14",
    fecha: ISODate("2024-11-28T16:30:00Z"),
    numeroPedido: 100014,
    usuario: "admin",
    observaciones: "Morteros, yeso y pintura - stock a confirmar",
    cliente: { _id: 3, nombre: "Techos y Cielorrasos SRL", correoElectronico: "obras@techoscielorrasos.com", cuit: "30-11111111-1" },
    obra: { _id: 2, direccion: "Calle Falsa 123" },
    total: 26150,
    estado: "ACEPTADO",
    detalle: [
      { producto: { _id: 4, nombre: "Mortero Pegamento Cer\u00e1mico 25 kg", descripcion: "Mortero adhesivo para colocaci\u00f3n de cer\u00e1micos", precio: 1800.00 }, cantidad: 5, precioUnitario: 1800.00, descuento: 0, precioFinal: 9000.00 },
      { producto: { _id: 5, nombre: "Yeso Premium 40 kg", descripcion: "Yeso fino para terminaciones interiores", precio: 2200.00 }, cantidad: 4, precioUnitario: 2200.00, descuento: 0, precioFinal: 8800.00 },
      { producto: { _id: 41, nombre: "Rodillo de Pete 23 cm", descripcion: "Rodillo para pintura", precio: 450.00 }, cantidad: 3, precioUnitario: 450.00, descuento: 0, precioFinal: 1350.00 },
      { producto: { _id: 40, nombre: "Pintura L\u00e1tex Blanco 20L - Sin m\u00ednimo", descripcion: "Pintura al l\u00e1tex para interiores", precio: 3500.00 }, cantidad: 2, precioUnitario: 3500.00, descuento: 0, precioFinal: 7000.00 }
    ]
  },

  // =========================================================================
  //  CLIENTE 4 — "Pinturas y Acabados SA" (maximoDescubierto ≈ 20000)
  //  ACEPTADO + EN_PREPARACION suman ~10000 → disponible ~10000
  //  Cliente con límite bajo pero con margen.
  // =========================================================================

  // --- P15: RECHAZADO (histórico — excedió el límite de 20000)
  {
    _id: "15",
    fecha: ISODate("2024-11-30T10:00:00Z"),
    numeroPedido: 100015,
    usuario: "sgonzalez",
    observaciones: "RECHAZADO — el total supera el m\u00e1ximo descubierto del cliente (20000)",
    cliente: { _id: 4, nombre: "Pinturas y Acabados SA", correoElectronico: "compras@pinturasyacabados.com", cuit: "30-22222222-2" },
    obra: { _id: 1, direccion: "Av. Siempre Viva 742" },
    total: 25000,
    estado: "RECHAZADO",
    detalle: [
      { producto: { _id: 70, nombre: "Provisionable - Stock cero inicial", descripcion: "Producto listo para recibir provisiones", precio: 5000.00 }, cantidad: 5, precioUnitario: 5000.00, descuento: 0, precioFinal: 25000.00 }
    ]
  },

  // --- P16: ACEPTADO, pedido pequeño (aporta 1000 al gastado)
  {
    _id: "16",
    fecha: ISODate("2024-12-01T09:00:00Z"),
    numeroPedido: 100016,
    usuario: "admin",
    observaciones: "Pedido peque\u00f1o de prueba",
    cliente: { _id: 4, nombre: "Pinturas y Acabados SA", correoElectronico: "compras@pinturasyacabados.com", cuit: "30-22222222-2" },
    obra: { _id: 1, direccion: "Av. Siempre Viva 742" },
    total: 1000,
    estado: "ACEPTADO",
    detalle: [
      { producto: { _id: 80, nombre: "Check - Stock suficiente (1000 disp)", descripcion: "Producto con alto stock para pruebas de verificaci\u00f3n", precio: 100.00 }, cantidad: 10, precioUnitario: 100.00, descuento: 0, precioFinal: 1000.00 }
    ]
  },

  // --- P17: EN_PREPARACION, crédito OK + stock OK (aporta 9000 al gastado)
  {
    _id: "17",
    fecha: ISODate("2024-12-02T14:00:00Z"),
    numeroPedido: 100017,
    usuario: "admin",
    observaciones: "Checks de stock y producto agotado",
    cliente: { _id: 4, nombre: "Pinturas y Acabados SA", correoElectronico: "compras@pinturasyacabados.com", cuit: "30-22222222-2" },
    obra: { _id: 1, direccion: "Av. Siempre Viva 742" },
    total: 9000,
    estado: "EN_PREPARACION",
    detalle: [
      { producto: { _id: 81, nombre: "Check - Stock exacto (10 disp)", descripcion: "Producto con stock justo para pruebas", precio: 200.00 }, cantidad: 20, precioUnitario: 200.00, descuento: 0, precioFinal: 4000.00 },
      { producto: { _id: 60, nombre: "Agotado sin alerta - Todo en 0", descripcion: "Producto sin stock y sin m\u00ednimo definido", precio: 5000.00 }, cantidad: 1, precioUnitario: 5000.00, descuento: 0, precioFinal: 5000.00 }
    ]
  }
]);

// Indice de idempotencia: un reintento con la misma key devuelve el pedido
// existente en lugar de duplicarlo.
db.pedidos.createIndex({ idempotencyKey: 1 }, { unique: true, sparse: true });

// Soporte para busquedas por cliente y estado.
db.pedidos.createIndex({ 'cliente._id': 1, estado: 1, habilitado: 1 });

// Normaliza el seed historico con el modelo de entregas parciales.
db.pedidos.find({}).forEach(function (pedido) {
  var changed = false;
  (pedido.detalle || []).forEach(function (linea, index) {
    if (!linea.lineaId) {
      linea.lineaId = String(pedido._id) + '-' + index;
      changed = true;
    }
    if (linea.cantidadEntregada === undefined || linea.cantidadEntregada === null) {
      linea.cantidadEntregada = 0;
      changed = true;
    }
  });
  if (!pedido.entregas) {
    pedido.entregas = [];
    changed = true;
  }
  if (!pedido.auditoria) {
    pedido.auditoria = [];
    changed = true;
  }
  if (changed) {
    db.pedidos.replaceOne({ _id: pedido._id }, pedido);
  }
});

// Correlativo unico de numero de pedido. Arranca despues del seed historico.
db.secuencias.updateOne(
  { _id: 'pedido' },
  { $max: { valor: 100017 } },
  { upsert: true }
);

db.secuencias.updateOne(
  { _id: 'remito' },
  { $max: { valor: 0 } },
  { upsert: true }
);
