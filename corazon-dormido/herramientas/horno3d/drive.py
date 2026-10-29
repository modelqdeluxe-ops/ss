# drive.py <id_carpeta> <destino> [filtro_regex]: baja una carpeta pública de Google Drive (recursiva)
import re, sys, os, subprocess
def listar(fid):
    html = subprocess.run(['curl', '-sL', '--max-time', '60', '-A', 'Mozilla/5.0', f'https://drive.google.com/drive/folders/{fid}'], capture_output=True, text=True).stdout
    out = []
    for m in re.finditer(r'\\x22([A-Za-z0-9_-]{25,45})\\x22,\\x5b\\x22' + re.escape(fid) + r'\\x22\\x5d,\\x22(.*?)\\x22,\\x22(.*?)\\x22', html):
        out.append((m.group(1), m.group(2).encode().decode('unicode_escape'), m.group(3).encode().decode('unicode_escape')))
    return out
def bajar(fid, dest, filtro, prof=0):
    os.makedirs(dest, exist_ok=True)
    for i, nombre, mime in listar(fid):
        ruta = os.path.join(dest, nombre)
        if 'folder' in mime:
            print('  ' * prof + nombre + '/'); bajar(i, ruta, filtro, prof + 1)
        elif re.search(filtro, ruta, re.I):
            if not os.path.exists(ruta):
                subprocess.run(['curl', '-sL', '--max-time', '300', '-o', ruta, f'https://drive.usercontent.google.com/download?id={i}&export=download&confirm=t'])
            print('  ' * prof + nombre, os.path.getsize(ruta))
bajar(sys.argv[1], sys.argv[2], sys.argv[3] if len(sys.argv) > 3 else '.')
