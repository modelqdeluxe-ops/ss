#!/usr/bin/env python3
"""Prepara el proyecto Gradle de la app del dueño (lo llama scripts/pagos.sh).

uso: preparar.py <carpeta de la app (variante)> <carpeta del proyecto Gradle>

- copia res/ sin public.xml y escribe ids.txt (--stable-ids) con los números de public.xml: el código smali usa
  esos números como constantes y tienen que ser los mismos;
- copia el manifiesto sin el atributo package ni los de la plataforma (Gradle pone el paquete).
"""
import re, shutil, sys, pathlib
import xml.etree.ElementTree as ET

app, d = map(pathlib.Path, sys.argv[1:3])
# con un tercer argumento: el public.xml completo de la app ya compilada por apktool (todos los recursos con su número)
completo = pathlib.Path(sys.argv[3]) if len(sys.argv) > 3 else None
shutil.copytree(app / 'res', d / 'res', dirs_exist_ok=True)
pub = d / 'res/values/public.xml'
ids = []
for e in ET.parse(completo or pub).getroot():
    if e.tag == 'public' and e.get('id'):
        ids.append('hn.hato.ganadero:%s/%s = %s' % (e.get('type'), e.get('name'), e.get('id')))
pub.unlink()
(d / 'ids.txt').write_text('\n'.join(ids) + '\n', encoding='utf-8')

A = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
t = ET.parse(app / 'AndroidManifest.xml'); m = t.getroot()
for k in list(m.attrib):
    if k == 'package' or k.startswith('platformBuild') or k.startswith(A + 'compileSdk'):
        del m.attrib[k]
(d / 'manifiesto').mkdir(parents=True, exist_ok=True)
t.write(d / 'manifiesto/AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
print('donante: %d recursos con número fijo' % len(ids))
