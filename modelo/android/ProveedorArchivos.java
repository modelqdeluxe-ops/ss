package hn.hato.ganadero;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;

/**
 * Entrega a otras apps (WhatsApp, correo, Drive) los archivos que la app dejó en caché/compartir.
 * Solo lectura, solo esa carpeta y solo con el permiso temporal que da el Intent de compartir.
 */
public class ProveedorArchivos extends ContentProvider {
    @Override public boolean onCreate() { return true; }

    private File archivo(Uri uri) throws FileNotFoundException {
        String n = uri.getLastPathSegment();
        if (n == null || !n.equals(Archivos.limpiar(n))) throw new FileNotFoundException("nombre no válido");
        File f = new File(Archivos.carpeta(getContext()), n);
        if (!f.isFile()) throw new FileNotFoundException(n);
        return f;
    }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && mode.contains("w")) throw new FileNotFoundException("solo lectura");
        return ParcelFileDescriptor.open(archivo(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public String getType(Uri uri) {
        String n = uri.getLastPathSegment();
        return n == null ? null : Archivos.tipo(n, null);
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) {
        File f;
        try { f = archivo(uri); } catch (FileNotFoundException e) { return null; }
        String[] cols = projection != null ? projection : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor c = new MatrixCursor(cols, 1);
        Object[] fila = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) fila[i] = f.getName();
            else if (OpenableColumns.SIZE.equals(cols[i])) fila[i] = f.length();
        }
        c.addRow(fila);
        return c;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("solo lectura"); }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
