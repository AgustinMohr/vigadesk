package isi.dan.ms.pedidos.servicio;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import isi.dan.ms.pedidos.modelo.DetallePedido;
import isi.dan.ms.pedidos.modelo.Entrega;
import isi.dan.ms.pedidos.modelo.EntregaItem;
import isi.dan.ms.pedidos.modelo.Pedido;

@Service
public class PedidoPdfService {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private final String corralonNombre;
    private final String corralonCuit;
    private final String corralonDireccion;
    private final String corralonTelefono;

    public PedidoPdfService(
            @Value("${corralon.nombre:Corralon de Materiales}") String corralonNombre,
            @Value("${corralon.cuit:30-00000000-0}") String corralonCuit,
            @Value("${corralon.direccion:Direccion del corralon}") String corralonDireccion,
            @Value("${corralon.telefono:000-000-0000}") String corralonTelefono) {
        this.corralonNombre = corralonNombre;
        this.corralonCuit = corralonCuit;
        this.corralonDireccion = corralonDireccion;
        this.corralonTelefono = corralonTelefono;
    }

    public byte[] generarPedido(Pedido pedido) {
        return generar(documento -> {
            encabezado(documento, "PEDIDO N° " + pedido.getNumeroPedido());
            datosPedido(documento, pedido);

            PdfPTable table = new PdfPTable(new float[]{4.2f, 1.2f, 1.5f, 1.2f, 1.6f});
            table.setWidthPercentage(100);
            header(table, "Producto", "Cantidad", "P. unitario", "Desc.", "Subtotal");
            for (DetallePedido linea : pedido.getDetalle()) {
                String producto = linea.getProducto().getNombre()
                        + " (" + unidad(linea) + ")";
                row(table, producto, cantidad(linea.getCantidad()),
                        money(linea.getPrecioUnitario()),
                        linea.getDescuento() == null ? "0%" : cantidad(linea.getDescuento()) + "%",
                        money(linea.getPrecioFinal()));
            }
            documento.add(table);
            documento.add(new Paragraph(" "));
            Paragraph total = new Paragraph("TOTAL: " + money(pedido.getTotal()),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
            total.setAlignment(Element.ALIGN_RIGHT);
            documento.add(total);
            firma(documento, "Firma y aclaracion del cliente");
        });
    }

    public byte[] generarRemito(Pedido pedido, Entrega entrega) {
        return generar(documento -> {
            encabezado(documento, "REMITO N° " + entrega.getNumeroRemito());
            datosPedido(documento, pedido);
            documento.add(new Paragraph("Receptor: " + entrega.getReceptor(),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
            documento.add(new Paragraph("Fecha de entrega: " + DATE_FORMAT.format(entrega.getFecha())));
            if (entrega.getObservaciones() != null && !entrega.getObservaciones().isBlank()) {
                documento.add(new Paragraph("Observaciones: " + entrega.getObservaciones()));
            }
            documento.add(new Paragraph(" "));

            Map<String, DetallePedido> lineas = new HashMap<>();
            for (DetallePedido detalle : pedido.getDetalle()) {
                lineas.put(detalle.getLineaId(), detalle);
            }

            PdfPTable table = new PdfPTable(new float[]{4.5f, 1.5f, 1.5f, 1.5f});
            table.setWidthPercentage(100);
            header(table, "Producto", "Pedida", "Entregada", "Unidad");
            for (EntregaItem item : entrega.getItems()) {
                DetallePedido linea = lineas.get(item.getLineaId());
                row(table, item.getProductoNombre(),
                        linea == null ? "-" : cantidad(linea.getCantidad()),
                        cantidad(item.getCantidad()),
                        linea == null ? "-" : unidad(linea));
            }
            documento.add(table);
            firma(documento, "Recibi conforme - Firma y aclaracion");
        });
    }

    private byte[] generar(Contenido contenido) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, output);
            document.open();
            contenido.escribir(document);
            document.close();
            return output.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el PDF", e);
        }
    }

    private void encabezado(Document document, String titulo) throws Exception {
        Paragraph nombre = new Paragraph(corralonNombre,
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
        nombre.setAlignment(Element.ALIGN_CENTER);
        document.add(nombre);

        Paragraph datos = new Paragraph(
                "CUIT: " + corralonCuit + "   |   " + corralonDireccion + "   |   Tel: " + corralonTelefono,
                FontFactory.getFont(FontFactory.HELVETICA, 9));
        datos.setAlignment(Element.ALIGN_CENTER);
        document.add(datos);
        document.add(new Paragraph(" "));

        Paragraph t = new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
        t.setAlignment(Element.ALIGN_CENTER);
        document.add(t);
        document.add(new Paragraph(" "));
    }

    private void datosPedido(Document document, Pedido pedido) {
        document.add(new Paragraph("Cliente: " + pedido.getCliente().getNombre()));
        document.add(new Paragraph("Obra: "
                + (pedido.getObra() == null ? "-" : pedido.getObra().getDireccion())));
        document.add(new Paragraph("Fecha: " + DATE_FORMAT.format(pedido.getFecha())));
        document.add(new Paragraph("Estado: " + pedido.getEstado()));
        document.add(new Paragraph(" "));
    }

    private void firma(Document document, String leyenda) throws Exception {
        document.add(new Paragraph(" "));
        document.add(new Paragraph(" "));
        PdfPTable firma = new PdfPTable(1);
        firma.setWidthPercentage(45);
        PdfPCell cell = new PdfPCell(new Phrase(leyenda));
        cell.setBorder(com.lowagie.text.Rectangle.TOP);
        cell.setPaddingTop(6);
        firma.addCell(cell);
        firma.setHorizontalAlignment(Element.ALIGN_RIGHT);
        document.add(firma);
    }

    private void header(PdfPTable table, String... values) {
        Font font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        for (String value : values) {
            PdfPCell cell = new PdfPCell(new Phrase(value, font));
            cell.setGrayFill(0.92f);
            table.addCell(cell);
        }
    }

    private void row(PdfPTable table, String... values) {
        for (String value : values) {
            table.addCell(new Phrase(value, FontFactory.getFont(FontFactory.HELVETICA, 9)));
        }
    }

    private String unidad(DetallePedido linea) {
        if (linea.getProducto() == null || linea.getProducto().getUnidadMedida() == null) {
            return "UNIDAD";
        }
        return linea.getProducto().getUnidadMedida();
    }

    private String cantidad(BigDecimal value) {
        if (value == null) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    private String money(BigDecimal value) {
        if (value == null) {
            return "$0,00";
        }
        return NumberFormat.getCurrencyInstance(new Locale("es", "AR")).format(value);
    }

    @FunctionalInterface
    private interface Contenido {
        void escribir(Document document) throws Exception;
    }
}
