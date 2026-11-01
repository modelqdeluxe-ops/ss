/*
 * TF Client — Tierras Fantásticas
 * Copyright (c) 2026 Tierras Fantásticas. Todos los derechos reservados.
 *
 * Software propietario y exclusivo del servidor de Minecraft Tierras Fantásticas. Queda prohibido copiarlo, modificarlo,
 * descompilarlo, redistribuirlo o usarlo, entero o en parte, en otros servidores o proyectos sin permiso escrito de
 * Tierras Fantásticas. Ver LICENSE.
 */
package net.tierrasfantasticas.tfclient.pad;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Guía (1.3.63): cómo funciona el pad y cada app, explicado paso a paso. A la izquierda, los temas (Primeros pasos,
 * cada app con su icono y Ajustes); a la derecha, el del tema elegido: su cabecera con el icono, el nombre y ABRIR (va
 * directo a esa app) y debajo el texto con secciones, pasos numerados, puntos y consejos. Las dos columnas se bajan con
 * la rueda, arrastrando o con su barra. El texto está aquí (en el cliente): si cambia una app, cambia su tema.
 */
final class PadGuidePage extends PadPage {
    /** Qué es cada trozo del texto. */
    private static final int SECTION = 0, PARA = 1, STEP = 2, BULLET = 3, TIP = 4;

    private record Block(int kind, String text) {}

    /** Un tema: su app (la que abre ABRIR; "" ninguna), el nombre, el icono (icon_&lt;icono&gt;.png) y el texto. */
    private record Topic(String app, String name, String icon, List<Block> blocks) {}

    private static final List<Topic> TOPICS = new ArrayList<>();
    /** El tema que estaba abierto (se recuerda entre aperturas del pad). */
    private static int last;

    private int sel = last;
    private int listScroll, textScroll, listContent, textContent;
    /** Zonas (de la última vez que se dibujó): la lista, el texto, cada tema y ABRIR. */
    private int listX, listY, listW, listH, textX, textY, textW, textH;
    private final int[][] rows = new int[64][];
    private int[] open;

    PadGuidePage(TFPadScreen pad) {
        super(pad, "guia");
    }

    @Override
    String title() {
        return "GUÍA";
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------------------------------------------------

    private static final int ROW = 18;

    @Override
    void render(GuiGraphics g, double mx, double my, float partial) {
        int acc = PadSkin.accent(app);
        PadSkin.window(g, X, Y, W, H, acc);
        int bh = PadSkin.bannerHeight(1);
        PadSkin.banner(g, pad, X + 1, Y + 1, W - 2, bh, acc, "guia", List.of(PadSkin.tagline(app)));
        int top = Y + bh + 5, bottom = Y + H - 5;
        // la lista de temas
        listW = Math.max(104, Math.min(126, W / 4));
        listX = X + 5;
        listY = top;
        listH = bottom - top;
        listContent = TOPICS.size() * ROW;
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, listContent - listH)));
        PadUi.box(g, listX - 1, listY - 1, listW + 2, listH + 2, PadSkin.mix(0xFFC6DAEE, acc, 0.2F));
        g.fill(listX, listY, listX + listW, listY + listH, 0xFFF3F8FD);
        pad.clipPush(g, listX, listY, listW, listH);
        for (int i = 0; i < TOPICS.size(); i++) {
            Topic t = TOPICS.get(i);
            int y = listY + i * ROW - listScroll;
            rows[i] = null;
            if (y + ROW < listY || y > listY + listH) continue;
            boolean on = i == sel;
            boolean hover = !on && PadUi.inside(mx, my, listX, y, listW, ROW) && PadUi.inside(mx, my, listX, listY, listW, listH);
            int tc = PadSkin.accent(t.app().isEmpty() ? "guia" : t.app());
            if (on) {
                g.fillGradient(listX, y, listX + listW, y + ROW, PadSkin.mix(tc, PadSkin.WHITE, 0.25F), PadSkin.darken(tc, 0.85F));
                g.fill(listX, y, listX + listW, y + 1, PadSkin.lighten(tc, 70));
                g.fill(listX, y + ROW - 1, listX + listW, y + ROW, PadSkin.darken(tc, 0.6F));
            } else {
                if (hover) {
                    g.fill(listX, y, listX + listW, y + ROW, PadUi.tint(tc, 0.22F));
                    pad.hover("§guia" + i);
                }
                g.fill(listX + 3, y + ROW - 1, listX + listW - 3, y + ROW, 0x1418265C);
            }
            g.fill(listX, y + 1, listX + 2, y + ROW - 1, on ? PadSkin.WHITE : tc);
            PadSkin.iconCrisp(g, pad, t.icon(), listX + 13, y + ROW / 2F, 14);
            String name = PadUi.fitEnd(t.name(), listW - 26);
            if (on) {
                g.drawString(PadUi.font(), PadUi.bold(name), listX + 24, y + 6, PadSkin.darken(tc, 0.45F), false);
                g.drawString(PadUi.font(), PadUi.bold(name), listX + 23, y + 5, PadSkin.WHITE, false);
            } else {
                PadUi.text(g, name, listX + 23, y + 5, PadUi.TEXT);
            }
            rows[i] = new int[] {listX, y, listW, ROW};
        }
        pad.clipPop(g);
        PadSkin.scrollbar(g, listX + listW + 2, listY, listH, listH, listContent, listScroll, acc);
        PadUi.grabbable(listX + listW + 2, listY, listH, listH, listContent, () -> listScroll, v -> listScroll = v);
        // el tema: cabecera y texto
        Topic t = TOPICS.get(sel);
        int tc = PadSkin.accent(t.app().isEmpty() ? "guia" : t.app());
        int cx = listX + listW + 10, cw = X + W - 6 - cx;
        int hh = 30;
        PadSkin.medallion(g, cx, top, 28, PadSkin.deep(tc));
        PadSkin.iconCrisp(g, pad, t.icon(), cx + 14, top + 14, 24);
        open = null;
        int right = cx + cw;
        if (!t.app().isEmpty()) {
            String label = "ABRIR " + t.name().toUpperCase(java.util.Locale.ROOT);
            int bw = PadUi.buttonWidth(label);
            if (bw > cw / 2) {
                label = "ABRIR";
                bw = PadUi.buttonWidth(label);
            }
            int bx = right - bw, by = top + 6;
            boolean hover = PadUi.inside(mx, my, bx, by, bw, 16);
            if (hover) pad.hover("§guiaabrir");
            PadSkin.button(g, bx, by, bw, label, PadView.GREEN, hover, true, false);
            open = new int[] {bx, by, bw, 16};
            right = bx - 6;
        }
        PadSkin.shadowTitle(g, t.name(), cx + 34, top + 5, right - cx - 34, PadSkin.darken(tc, 0.7F), 0x2018265C);
        PadUi.text(g, PadUi.fitEnd(summary(t), right - cx - 34), cx + 34, top + 17, PadUi.MUTED);
        g.fill(cx, top + hh + 1, cx + cw, top + hh + 2, PadUi.tint(tc, 0.5F));
        g.fill(cx, top + hh + 2, cx + cw, top + hh + 3, PadSkin.WHITE);
        textX = cx;
        textY = top + hh + 5;
        textW = cw - 7;
        textH = bottom - textY;
        textContent = measure(t, textW) + 4;
        textScroll = Math.max(0, Math.min(textScroll, Math.max(0, textContent - textH)));
        pad.clipPush(g, textX, textY, textW + 1, textH);
        draw(g, t, textX, textY - textScroll, textW, tc);
        pad.clipPop(g);
        // un degradado abajo si sigue: se nota que hay más texto
        if (textScroll < textContent - textH) {
            PadSkin.vgrad(g, textX, textY + textH - 10, textX + textW, textY + textH, 0x00F4F9FE, 0xE6F4F9FE);
        }
        PadSkin.scrollbar(g, textX + textW + 3, textY, textH, textH, textContent, textScroll, tc);
        PadUi.grabbable(textX + textW + 3, textY, textH, textH, textContent, () -> textScroll, v -> textScroll = v);
    }

    /** La frase de debajo del nombre: el primer párrafo, recortado. */
    private static String summary(Topic t) {
        for (Block b : t.blocks()) if (b.kind() == PARA) return b.text();
        return "";
    }

    private static int lines(String s, int w) {
        return Math.max(1, PadUi.lines(s, w));
    }

    /** Cuánto ocupa el texto de un tema a ancho w. */
    private static int measure(Topic t, int w) {
        int h = 0;
        boolean first = true;
        for (Block b : t.blocks()) {
            h += height(b, w, first);
            first = false;
        }
        return h;
    }

    private static int height(Block b, int w, boolean first) {
        return switch (b.kind()) {
            case SECTION -> (first ? 0 : 6) + 14;
            case PARA -> lines(b.text(), w) * 10 + 5;
            case STEP -> Math.max(13, lines(b.text(), w - 16) * 10 + 3);
            case BULLET -> lines(b.text(), w - 11) * 10 + 2;
            default -> 16 + lines(b.text(), w - 12) * 10 + 9; // TIP
        };
    }

    private static void draw(GuiGraphics g, Topic t, int x, int y, int w, int tc) {
        int step = 0;
        boolean first = true;
        for (Block b : t.blocks()) {
            int h = height(b, w, first);
            switch (b.kind()) {
                case SECTION -> {
                    PadSkin.section(g, x, y + (first ? 0 : 6) + 2, w, b.text(), tc);
                    step = 0;
                }
                case PARA -> PadUi.wrap(g, b.text(), x, y + 1, w, PadUi.TEXT, 99);
                case STEP -> {
                    step++;
                    // el número en su moneda del color del tema
                    PadSkin.disc(g, x + 6, y + 5.5F, 6F, PadSkin.darken(tc, 0.62F));
                    PadSkin.disc(g, x + 6, y + 5.5F, 5F, tc);
                    String n = Integer.toString(step);
                    g.drawString(PadUi.font(), n, x + 6 - PadUi.font().width(n) / 2 + 1, y + 2, PadSkin.WHITE, false);
                    PadUi.wrap(g, b.text(), x + 16, y + 1, w - 16, PadUi.TEXT, 99);
                }
                case BULLET -> {
                    PadUi.diamond(g, x + 4, y + 4);
                    PadUi.wrap(g, b.text(), x + 11, y, w - 11, PadUi.TEXT, 99);
                }
                default -> { // TIP: tarjetita clara del color del tema, con «CONSEJO»
                    int bh = h - 5;
                    PadUi.box(g, x, y + 1, w, bh, PadUi.tint(tc, 0.55F));
                    g.fillGradient(x + 1, y + 2, x + w - 1, y + bh, PadUi.tint(tc, 0.16F), PadUi.tint(tc, 0.28F));
                    g.fill(x + 1, y + 2, x + 3, y + bh, tc);
                    g.drawString(PadUi.font(), PadUi.bold("CONSEJO"), x + 8, y + 5, PadSkin.darken(tc, 0.62F), false);
                    PadUi.wrap(g, b.text(), x + 8, y + 16, w - 12, PadUi.TEXT, 99);
                }
            }
            y += h;
            first = false;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Ratón
    // ---------------------------------------------------------------------------------------------------------------

    @Override
    boolean click(double mx, double my, int button) {
        if (button != 0) return false;
        if (open != null && PadUi.inside(mx, my, open[0], open[1], open[2], open[3])) {
            pad.openApp(TOPICS.get(sel).app());
            return true;
        }
        if (!PadUi.inside(mx, my, listX, listY, listW, listH)) return false;
        for (int i = 0; i < TOPICS.size(); i++) {
            int[] r = rows[i];
            if (r == null || !PadUi.inside(mx, my, r[0], r[1], r[2], r[3])) continue;
            if (i != sel) {
                sel = last = i;
                textScroll = 0;
                pad.sound("page", 0.6F);
            }
            return true;
        }
        return false;
    }

    @Override
    boolean scroll(double mx, double my, double delta) {
        if (mx < listX + listW + 6) listScroll -= (int) Math.signum(delta) * ROW * 2;
        else textScroll -= (int) Math.signum(delta) * 24;
        return true;
    }

    @Override
    boolean drag(double mx, double my, double dy) {
        if (PadUi.inside(mx, my, listX, listY, listW, listH)) listScroll += (int) Math.round(dy);
        else if (PadUi.inside(mx, my, textX, textY, textW, textH)) textScroll += (int) Math.round(dy);
        else return false;
        return true;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // El texto
    // ---------------------------------------------------------------------------------------------------------------

    private static Block h(String s) {
        return new Block(SECTION, s);
    }

    private static Block p(String s) {
        return new Block(PARA, s);
    }

    private static Block s(String s) {
        return new Block(STEP, s);
    }

    private static Block b(String s) {
        return new Block(BULLET, s);
    }

    private static Block tip(String s) {
        return new Block(TIP, s);
    }

    private static void topic(String app, String name, String icon, Block... blocks) {
        TOPICS.add(new Topic(app, name, icon, List.of(blocks)));
    }

    static {
        topic("", "Primeros pasos", "guia",
                p("El TF Pad es la tableta de Tierras Fantásticas: desde aquí haces casi todo sin escribir comandos."),
                h("Abrir y cerrar"),
                s("Pulsa C para abrirlo. La tecla se cambia en Opciones › Controles."),
                s("C lo cierra. Dentro de una app, Esc (o Retroceso) vuelve a la portada; en la portada, Esc cierra el pad."),
                b("La música y lo que estés bajando siguen aunque cierres el pad."),
                h("La barra de arriba"),
                b("Flecha azul: vuelve a la portada."),
                b("Sol o luna con la hora: la hora del mundo."),
                b("Fantastic Gacha Coin (verde): se ganan con el TF Pass y se gastan en el Gachapón."),
                b("Fantastic Coin (oro): para la Tienda, el GTS y los kits de pago."),
                b("Engranaje: los Ajustes del pad (sonidos, animaciones, volumen y color)."),
                b("Barritas verdes que bailan: hay música sonando. Púlsalas para ir a Música."),
                h("Moverse por las apps"),
                b("Las pestañas de arriba de cada app cambian de sección."),
                b("Las listas largas bajan con la rueda del ratón, arrastrando la lista o arrastrando su barra."),
                b("Los botones de abajo son las acciones. Los que no tienen vuelta atrás cambian a «¿SEGURO?»: púlsalo otra vez para confirmar."),
                b("Los avisos salen abajo, en una pastilla: verde si salió bien, roja si algo falló. Un clic la cierra."),
                tip("Pasa el ratón por encima de un objeto para ver su descripción, igual que en el inventario."));

        topic("musica", "Música", "musica",
                p("Un reproductor completo dentro del pad. Tu música se guarda en tu computadora y solo la oyes tú, salvo que actives el altavoz."),
                h("Añadir canciones con un link"),
                s("Sube la canción a un sitio que dé el archivo para descargar: Google Drive, Dropbox, GitHub o cualquier página que dé el archivo: MP3, MP4, M4A, AAC, OGG o WAV."),
                s("Google Drive: clic derecho en el archivo › Compartir › Acceso general: «Cualquier persona con el enlace» › Copiar enlace."),
                s("Dropbox: Compartir › Copiar enlace. El pad pone solo «dl=1» para que se baje directo."),
                s("En Música, pega el link en el campo de abajo (Ctrl+V) y pulsa AÑADIR. Verás la barra de descarga; al acabar, la canción sale en TODAS."),
                tip("Un link directo, al abrirlo en el navegador, descarga o reproduce el archivo. Los de YouTube, Spotify o SoundCloud no valen: son una página, no el archivo."),
                h("Agregar canciones desde tu computadora"),
                s("Copia tus canciones (MP3, MP4, M4A, AAC, OGG o WAV) en la carpeta .minecraft/tfclient/musica (también en subcarpetas)."),
                s("Abre Música: las nuevas se añaden solas, con su título, su artista y su portada si el archivo los trae."),
                h("Calidad"),
                b("MP3 de cualquier calidad: de 64 a 320 kbps, constante o variable (VBR), a 44,1 o 48 kHz. Suenan igual que el original."),
                b("MP4 y M4A (audio AAC, también HE-AAC), aunque el MP4 sea un vídeo: se oye su audio. Y AAC suelto, OGG y WAV."),
                b("FLAC todavía no: pásalo a M4A de 256 kbps o a MP3 de 320 kbps."),
                b("Hasta 250 MB por canción."),
                h("Escuchar"),
                b("Clic en una canción para que suene. Controles: aleatorio, anterior, play/pausa, siguiente y repetir (todas, una o ninguna)."),
                b("Arrastra la barra de tiempo para saltar a cualquier punto. Espacio = play/pausa; flechas ← → = 5 segundos atrás o adelante."),
                b("El volumen es la barra vertical; clic en el altavoz para silenciar."),
                b("La música sigue sonando con el pad cerrado."),
                h("Playlists"),
                s("Pulsa «+» al final de las pestañas, escribe el nombre y pulsa CREAR."),
                s("Dentro de la lista, pulsa ELEGIR CANCIONES, marca las que quieras y pulsa LISTO."),
                b("Un link pegado dentro de una lista se baja directo a esa lista."),
                b("El − saca una canción de la lista sin borrarla. La X (en TODAS) la borra del todo, con confirmación."),
                h("Portada y altavoz"),
                b("PORTADA pone una imagen a la canción que suena desde un link a un PNG o JPG."),
                b("El botón del altavoz, al final de los controles, hace que los jugadores a menos de 32 bloques oigan tu música."),
                b("Para oír los altavoces de otros, deja encendido «Oír los altavoces de otros» en Ajustes."));

        topic("oficios", "Oficios", "oficios",
                p("Elige un oficio y cobra monedas por lo que haces en el mundo: picar, talar, cosechar, pescar, cazar…"),
                h("Empezar"),
                s("Abre Oficios y pulsa la tarjeta de un oficio para ver de qué va."),
                s("Pulsa TRABAJAR AQUÍ. Trabajas en un oficio a la vez."),
                s("Juega normal: lo que cuenta para tu oficio suma monedas y experiencia. Se cobra solo cada poco; arriba ves cuánto llevas para el próximo pago."),
                h("Dentro de un oficio"),
                b("MISIONES: retos del oficio. Cuando estén hechos, pulsa COBRAR."),
                b("CÓMO SE GANA: qué acciones pagan y cuánto con tu nivel. A más nivel, más cobras."),
                b("PREMIOS: lo que te dan los próximos niveles."),
                h("Cambiar o dejar"),
                b("CAMBIARME y DEJAR piden un segundo clic. Tu nivel y tus misiones de cada oficio se quedan guardados."),
                tip("No cuentan los bloques que pusiste tú, los monstruos de spawner ni lo que hagas en creativo."));

        topic("misiones", "Misiones", "misiones",
                p("Retos para ti solo: 3 diarios y 3 semanales, con premio."),
                h("Cómo van"),
                s("Abre DIARIAS o SEMANALES y mira qué pide cada una y cuánto llevas."),
                s("Juega: romper, cosechar, matar, pescar, fabricar, fundir, criar… cuenta solo."),
                s("Cuando una esté completa, pulsa RECLAMAR (o RECLAMAR TODO)."),
                b("Las diarias cambian cada día y las semanales cada semana."),
                b("Cada misión completada da XP del TF Pass."),
                tip("No cuentan los bloques que pusiste tú, los monstruos de spawner ni el modo creativo."));

        topic("pase", "TF Pass", "pase",
                p("El pase de temporada, gratis para todos: sube de nivel y cobra premios, sobre todo Fantastic Gacha Coins."),
                h("Cómo se sube"),
                s("Completa misiones (diarias y semanales) y cazas: dan XP de pase."),
                s("Con la XP subes de nivel. La barra de arriba te dice cuánto falta para el siguiente."),
                s("Pulsa COBRAR en cada nivel listo (en verde) o RECLAMAR TODO."),
                b("La pista de niveles se mueve con la rueda, arrastrando o con las flechas. Se abre en tu nivel, marcado con «TÚ»."),
                b("Los niveles de 5 en 5 son hitos, con premios mejores (marco de oro)."),
                b("Abajo ves los premios del nivel que señalas con el ratón."),
                tip("Mira la cuenta atrás de la temporada: al acabar, los niveles vuelven a cero y lo que no hayas cobrado se pierde."));

        topic("gachapon", "Gachapón", "gachapon",
                p("Gira la ruleta con Fantastic Gacha Coins y gana un premio."),
                h("Girar"),
                s("Consigue Fantastic Gacha Coins con el TF Pass."),
                s("Pulsa GIRAR para una tirada o GIRAR X5 para varias de golpe. Cada botón dice lo que cuesta."),
                s("La ruleta se para en tu premio y te llega al momento. Aunque cierres el pad a mitad, ya es tuyo."),
                h("Saber qué puede tocar"),
                b("PREMIOS: todos los premios con su rareza y su probabilidad exacta."),
                b("HISTORIAL: lo último que te tocó."),
                b("La garantía te dice en cuántas tiradas tienes asegurada una rareza buena."));

        topic("cazas", "Cazas", "cazas",
                p("Objetivos de caza para todo el servidor: 4 criaturas que cambian cada 12 horas."),
                h("Cómo van"),
                s("Mira qué criaturas tocan y cuántas hay que cazar."),
                s("Cázalas: son las mismas para todos, pero cada uno lleva su cuenta."),
                s("Al completarla, pulsa RECLAMAR para cobrar el premio."),
                b("Las cazas también dan XP del TF Pass."),
                tip("Los monstruos de spawner no cuentan."));

        topic("recompensas", "Recompensas", "recompensas",
                p("Tu premio de cada día y el buzón de los regalos que te mandan."),
                h("DIARIA"),
                s("Entra cada día y pulsa RECLAMAR DÍA para el premio que toca."),
                s("Cada día seguido avanza tu racha: mira en el calendario qué trae cada día."),
                b("Si te saltas un día, la racha puede volver al día 1. Al acabar el calendario, empieza otra vez."),
                h("REGALOS"),
                b("Lo que otros jugadores te regalan desde Jugadores llega aquí, aunque estuvieras desconectado."),
                b("Pulsa RECOGER en cada regalo o RECOGER TODO. Necesitas sitio en el inventario para los objetos."));

        topic("tienda", "Tienda", "tienda",
                p("La tienda del servidor: compra y vende al momento con tus monedas."),
                h("Comprar"),
                s("En COMPRAR, elige una categoría."),
                s("Pulsa un objeto para ver su ficha."),
                s("Elige la cantidad: se cobra de tu saldo y el objeto va a tu inventario."),
                h("Vender"),
                s("En VENDER sale lo que llevas encima que la tienda compra, con su precio."),
                s("Pulsa VENDER: el dinero va a tu monedero."));

        topic("gts", "GTS", "gts",
                p("El mercado entre jugadores: vende lo tuyo al precio que quieras y compra lo de otros."),
                h("Comprar"),
                s("En MERCADO ves todo lo que está a la venta. Usa el buscador para encontrar algo."),
                s("Pulsa una fila para ver su ficha y COMPRAR."),
                h("Vender"),
                s("En VENDER, elige un objeto de tu inventario."),
                s("Escribe el precio. Sirven atajos: 500, 5k (5.000) o 2m (2.000.000)."),
                s("Pulsa PUBLICAR. Se queda a la venta unos días."),
                h("Tus ventas"),
                b("MIS VENTAS: lo que tienes a la venta. RETIRAR lo quita del mercado."),
                b("RECOGER: lo que no se vendió a tiempo. RECOGER TODO lo devuelve a tu inventario."),
                b("Lo que vendes se cobra solo en tu monedero."),
                tip("Lo comprado en la web y lo que va ligado a su dueño no se puede vender."));

        topic("monedero", "Monedero", "monedero",
                p("Tu saldo, lo que has cobrado y gastado, y mandar monedas a otro jugador."),
                h("Mandar monedas"),
                s("En el campo de abajo escribe el jugador y la cantidad, por ejemplo: Steve 500."),
                s("Pulsa PREPARAR y revisa el resumen."),
                s("Pulsa MANDAR para enviarlo, o NO para cancelar. No se puede deshacer."),
                b("Los atajos llevan a la Tienda, al GTS y a Oficios."));

        topic("viajes", "Viajes", "viajes",
                p("Viaja al spawn, a tu cama o a los puntos del servidor, o explora un sitio al azar."),
                h("Viajar"),
                s("Pulsa el destino."),
                s("Espera la cuenta atrás de 3 segundos sin moverte ni recibir daño: si te mueves, se cancela."),
                b("Después hay un tiempo de espera hasta el próximo viaje."),
                h("Explorar"),
                b("EXPLORAR te lleva a un sitio seguro al azar del mundo normal, lejos del spawn. Si sale «EN …», aún tienes que esperar."));

        topic("hogares", "Hogares", "hogares",
                p("Tus casas guardadas, para volver a ellas a un clic."),
                h("Guardar un hogar"),
                s("Ve al sitio que quieres guardar."),
                s("Escribe un nombre abajo y pulsa GUARDAR AQUÍ."),
                h("Usarlos"),
                b("IR te lleva (con la cuenta atrás de los viajes)."),
                b("BORRAR lo quita (pide un segundo clic)."),
                b("Cuántos puedes tener depende de tu rango."));

        topic("kits", "Kits", "kits",
                p("Equipo listo para reclamar: unos cada día, otros cada semana y otros una sola vez."),
                h("Reclamar"),
                s("Pulsa un kit para ver todo lo que trae."),
                s("Pulsa RECLAMAR (o PAGAR Y RECLAMAR si cuesta monedas)."),
                b("«EN 5 H» te dice cuánto falta para volver a reclamarlo; RECLAMADO, que era de una vez."),
                b("NECESITAS RANGO: es de un rango mayor que el tuyo."),
                b("Las monedas de un kit van directas a tu monedero."));

        topic("protecciones", "Protección", "protecciones",
                p("Protege tus terrenos para que nadie rompa ni use nada sin permiso."),
                h("Crear una zona"),
                s("Coloca una piedra de protección en el sitio que quieres proteger: la zona se crea a su alrededor."),
                s("Abre Protección: ahí salen todas tus zonas. Pulsa una para administrarla."),
                h("Dentro de una zona"),
                b("AJUSTES: cada opción es una pregunta con SÍ y NO; la que vale ahora sale en verde."),
                b("MIEMBROS: invita a quien pueda construir y usarlo todo."),
                b("BANEOS: quien no puede entrar (la barrera lo saca). DESBANEAR lo quita."),
                b("PARTÍCULAS: el efecto del borde y su densidad (MENOS / MÁS)."),
                b("MÁS: los mensajes al entrar y salir, el contorno, el grupo y eliminar la zona."),
                tip("Si estás dentro de una zona ajena, el pad te dice de quién es."));

        topic("clanes", "Clanes", "clanes",
                p("Tu clan, con su etiqueta junto a tu nombre, sus miembros y sus rangos."),
                h("Fundar un clan"),
                s("Escribe el nombre (3 a 20 letras) y pulsa FUNDAR."),
                s("Escribe la etiqueta (2 a 4 letras o números, por ejemplo TF) y pulsa FUNDAR."),
                h("Miembros"),
                b("Rangos: líder, colíder, veterano y miembro."),
                b("INVITAR (líder, colíderes y veteranos): escribe el nombre del jugador."),
                b("Si te invitan, pulsa UNIRSE o NO."),
                b("El líder ASCIENDE (miembro → veterano → colíder) y BAJA; con LÍDER (dos clics) le pasa el clan a otro."),
                b("ECHAR: el líder a los miembros; los colíderes a veteranos y miembros."),
                b("SALIR deja el clan; el líder puede DISOLVER CLAN. Los dos piden un segundo clic."),
                h("Ajustes y estilo (líder y colíderes)"),
                b("ESTILO › ETIQUETA: un color, un degradado (2 o 3 colores), arcoíris u onda; y la insignia del clan."),
                b("AJUSTES: si hay fuego amigo entre miembros y si los veteranos pueden invitar o sacar del banco."),
                h("Banco, cofre y estandarte"),
                b("BANCO: todos meten lo que llevan (METER); sacan el líder y los colíderes (y los veteranos si se activa)."),
                b("COFRE da el cofre del clan (uno por clan): quien lo usa abre el banco. Lo quitan el líder y los colíderes."),
                b("El estandarte del clan se compra en TIENDA: lleva la insignia y la etiqueta del clan."),
                h("Emote del clan"),
                b("Tecla B (cámbiala en Opciones › Controles): ¡por el clan! con la insignia, brillos y fanfarria."),
                h("Guerras y duelos"),
                b("Guerra por objetivos de trabajo y uno PvP: derrotar a los del clan rival (opcional, suma igual)."),
                b("/clan duelo <jugador>: al otro le salen los botones ACEPTAR y RECHAZAR en el chat."));

        topic("jugadores", "Jugadores", "jugadores",
                p("Quién está conectado, su ficha y regalarles cosas."),
                h("Ver a alguien"),
                b("Pulsa un jugador para ver su FICHA: rango, clan, oficios, misiones y fotos."),
                h("Regalar"),
                s("En su ficha, abre REGALAR."),
                s("Elige un objeto de tu inventario y pulsa REGALAR (pide un segundo clic), o escribe una cantidad de monedas y pulsa REGALAR."),
                s("Le llega a su buzón (Recompensas › REGALOS), aunque esté desconectado."),
                b("Hay un límite de regalos al día. Lo comprado en la web y lo ligado a su dueño no se puede regalar."));

        topic("comunidad", "Comunidad", "comunidad",
                p("La red social del servidor: las fotos de todos, con likes y reacciones."),
                h("Ver"),
                b("RECIENTES, POPULARES o MÍAS. Baja con la rueda o arrastrando."),
                b("Clic en una foto para verla en grande."),
                h("Reaccionar"),
                b("El corazón da o quita like."),
                b("REACCIONAR abre los emojis (casi 2.000, también animados), con buscador. Una reacción por foto: otra la cambia."),
                b("Pulsa una reacción que ya está para sumarte o quitarte. Las tuyas salen en oro."),
                h("Publicar"),
                s("Pulsa + FOTO: te lleva a la Cámara."),
                s("Haz la foto, escribe un texto y pulsa PUBLICAR."),
                b("BORRAR quita las tuyas; DENUNCIAR avisa al staff de una foto que no debería estar."));

        topic("camara", "Cámara", "camara",
                p("Haz fotos del juego y publícalas en Comunidad."),
                h("Hacer una foto"),
                s("Pulsa MODO FOTO: se esconde la interfaz y puedes moverte y mirar para encuadrar."),
                s("Clic izquierdo para disparar (no golpea a nada)."),
                s("Esc vuelve al pad, con la foto abierta."),
                h("Tus fotos"),
                b("Se guardan en tu computadora, en .minecraft/tfclient/fotos. CARPETA la abre."),
                b("Las flechas pasan de una a otra; BORRAR la elimina."),
                b("PUBLICAR la sube a Comunidad con un texto."));

        topic("ranking", "Ranking", "ranking",
                p("Los 10 mejores del servidor."),
                b("Pestañas: MISIONES, CAZAS, LIKES (de las fotos de Comunidad), EXPLORAR, DINERO (quién tiene más), MUERTES (quién muere más) y MOBS (quién mata más mobs)."),
                b("Si estás entre los 10 primeros, tu fila sale marcada."));

        topic("armario", "Armario", "armario",
                p("Tus skins y tus cosméticos, para cambiar cómo te ven los demás."),
                h("SKINS"),
                b("Arriba ves la skin que llevas, en 3D (solo la skin, sin armadura ni cosméticos)."),
                s("Pulsa AÑADIR SKIN."),
                s("Desde tu computadora: ABRIR CARPETA, copia ahí tus skins (PNG de 64x64), pulsa ACTUALIZAR y luego IMPORTAR en la que quieras."),
                s("De otro jugador: escribe su nombre de Minecraft y pulsa TRAER SU SKIN."),
                s("Pulsa una skin y luego PONÉRMELA. QUITÁRMELA vuelve a la tuya de siempre."),
                b("Guardas hasta 16. En cada una: brazos finos o normales, EXPORTAR, renombrar y BORRAR."),
                b("La ven todos los que tienen TF Client."),
                h("Cosméticos"),
                b("CABEZA, PECHO, PIERNAS, PIES y ESPALDA: lo que tienes de tus compras y tu rango. PONER y QUITAR."),
                b("Solo cambian cómo te ven: tu armadura de verdad sigue igual."));

        topic("efectos", "Efectos", "efectos",
                p("Efectos al matar, clases de skills y efectos de combate."),
                b("AL MATAR: efectos de kill gratis. Pulsa uno para EQUIPAR; QUITAR EFECTO lo quita."),
                b("SKILLS: las clases de skills que compraste en la web. ACTIVAR una (solo una a la vez); DESACTIVAR la apaga."),
                b("COMBATE: la estela de luz de tus golpes (estilo, color, duración, ancho...) con su vista previa. Es solo tuya y "
                        + "se guarda sola; RESTABLECER la deja como venía."),
                b("Todo se aplica al momento."));

        topic("rango", "Mi rango", "rango",
                p("Tu rango, lo que te trae y los demás rangos con sus ventajas."),
                b("Arriba, el tuyo con sus ventajas."),
                b("Debajo, todos los rangos: pulsa VER en uno para ver qué trae."),
                b("Los rangos se consiguen en la tienda web (app Web)."));

        topic("web", "Web", "web",
                p("Abre la página de Tierras Fantásticas en tu navegador: tienda, rangos, crates y cosméticos."),
                b("Lo que compres en la web te llega al juego solo."));

        topic("", "Ajustes", "admin",
                p("Tu pad, a tu manera. Se abre con el engranaje de arriba a la derecha."),
                b("Sonidos del pad: clics, pestañas y avisos."),
                b("Tic al pasar por encima: un sonido suave al pasar el ratón."),
                b("Animaciones: fondo animado, brillos y transiciones. Apágalas si tu computadora va lenta."),
                b("Volumen: el de los sonidos del pad (la música tiene el suyo, en Música)."),
                b("Oír los altavoces de otros: la música que ponen cerca de ti."),
                b("Tema del pad: el color del cristal (Cielo, Rosa, Lavanda, Menta, Atardecer, Oro, Fresa o Noche)."));
    }
}
