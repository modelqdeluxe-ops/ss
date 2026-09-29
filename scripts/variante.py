#!/usr/bin/env python3
"""Convierte una copia de app/ en una de las apps de Rumentis (la llama scripts/variantes.sh).

uso: variante.py <carpeta> <paquete> <nombre> <jefe|vaquero> <prueba 0|1> <color del ícono> [manifiesto de pagos]

- paquete y nombre propios: las cuatro apps se instalan juntas en el mismo teléfono;
- config.js dice qué app es y si es de prueba;
- el proveedor de archivos (compartir PDF y respaldos) lleva la autoridad del paquete nuevo;
- el ícono cambia de color de fondo para distinguirlas;
- la del vaquero no lleva el widget de Rumi;
- con manifiesto de pagos (solo la app del jefe de Google Play): permisos y actividades de Google Play Billing.
"""
import re, sys, pathlib
import xml.etree.ElementTree as ET

d, paquete, nombre, app, prueba, color = sys.argv[1:7]
pagos = sys.argv[7] if len(sys.argv) > 7 else ''
D = pathlib.Path(d)
BASE = 'hn.hato.ganadero'
A = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
ET.register_namespace('tools', 'http://schemas.android.com/tools')

# ---- manifiesto ----
mp = D / 'AndroidManifest.xml'
t = ET.parse(mp); m = t.getroot()
m.set('package', paquete)
ap = m.find('application')
for el in ap:
    n = el.get(A + 'name')
    if n and n.startswith('.'):
        el.set(A + 'name', BASE + n)   # las clases siguen en hn.hato.ganadero
    if el.tag == 'provider' and el.get(A + 'authorities') == BASE + '.archivos':
        el.set(A + 'authorities', paquete + '.archivos')
if app == 'vaquero':
    for el in list(ap):
        if el.tag == 'receiver' and el.get(A + 'name', '').endswith('RumiWidget'):
            ap.remove(el)
    # archivos del equipo: Rumentis abre los .rumentis (los reportes del personal); Rumentis Equipo, los .campo
    # (las actualizaciones de la administración)
    for el in m.iter('data'):
        if el.get(A + 'mimeType') == 'application/vnd.rumentis':
            el.set(A + 'mimeType', 'application/vnd.rumentis.campo')
        pp = el.get(A + 'pathPattern')
        if pp and pp.endswith('.rumentis'):
            el.set(A + 'pathPattern', pp[:-len('rumentis')] + 'campo')
if pagos:
    lib = ET.parse(pagos).getroot()
    ya = {(e.tag, e.get(A + 'name')) for e in m}
    for e in lib:
        if e.tag == 'uses-permission' and (e.tag, e.get(A + 'name')) not in ya:
            m.insert(0, e)
        elif e.tag == 'queries':
            q = m.find('queries')
            if q is None:
                q = ET.SubElement(m, 'queries')
            for c in e:
                q.append(c)
    la = lib.find('application')
    if la is not None:
        for c in la:
            ap.append(c)
    # las marcas ${applicationId} de la biblioteca
    s = ET.tostring(m, encoding='unicode').replace('${applicationId}', paquete)
    m = ET.fromstring(s); t = ET.ElementTree(m)
t.write(mp, encoding='utf-8', xml_declaration=True)

# ---- nombre ----
sp = D / 'res/values/strings.xml'
s = sp.read_text(encoding='utf-8')
s = re.sub(r'(<string name="app_name">)[^<]*(</string>)', lambda x: x.group(1) + nombre + x.group(2), s)
sp.write_text(s, encoding='utf-8')
# la app del personal lleva su nombre en el idioma del teléfono: Rumentis Equipo, Team o Equipe
if app == 'vaquero':
    otros = {'en': 'Team Test', 'pt': 'Equipe Teste'} if prueba == '1' else {'en': 'Rumentis Team', 'pt': 'Rumentis Equipe'}
    for q, n in otros.items():
        dq = D / ('res/values-' + q)
        dq.mkdir(exist_ok=True)
        (dq / 'strings.xml').write_text('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <string name="app_name">' + n + '</string>\n</resources>\n', encoding='utf-8')

# ---- color del ícono ----
cp = D / 'res/values/colors.xml'
s = cp.read_text(encoding='utf-8')
s = re.sub(r'(<color name="icono_fondo">)#[0-9a-fA-F]+(</color>)', lambda x: x.group(1) + color + x.group(2), s)
cp.write_text(s, encoding='utf-8')

# ---- autoridad del proveedor de archivos en el código ----
for f in (D / 'smali').rglob('Archivos*.smali'):
    s = f.read_text(encoding='utf-8')
    s2 = s.replace('"' + BASE + '.archivos"', '"' + paquete + '.archivos"').replace('"content://' + BASE + '.archivos/', '"content://' + paquete + '.archivos/')
    if s2 != s:
        f.write_text(s2, encoding='utf-8')

# ---- config.js ----
cj = D / 'assets/config.js'
s = cj.read_text(encoding='utf-8')
s2 = re.sub(r"window\.RUMENTIS=\{app:'[a-z]+',prueba:(true|false),", "window.RUMENTIS={app:'%s',prueba:%s," % (app, 'true' if prueba == '1' else 'false'), s, count=1)
assert s2 != s or ("app:'%s'" % app in s), 'no se pudo marcar config.js'
cj.write_text(s2, encoding='utf-8')
print('variante:', paquete, '·', nombre, '·', app, '· prueba' if prueba == '1' else '', '· pagos' if pagos else '')
