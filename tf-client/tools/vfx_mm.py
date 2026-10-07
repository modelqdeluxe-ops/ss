"""Lee las líneas de mecánicas de MythicMobs (los .yml de los packs de VFX) para sacar sonidos, partículas y tiempos.

Una línea es «- nombre{a=1;b=x} @objetivo{...} ~disparador ?condición». Aquí solo hace falta separar el nombre, los
argumentos, el objetivo y el disparador; las listas anidadas ([ - ... ]) se dejan como texto.
"""
import re

import yaml

ALIASES = {
    'p': 'particle', 'particle': 'particle', 'a': 'amount', 'amount': 'amount', 'hs': 'hs', 'vs': 'vs',
    'speed': 'speed', 's': 'speed', 'y': 'y', 'repeat': 'repeat', 'repeatinterval': 'repeati', 'repeati': 'repeati',
    'delay': 'delay', 'material': 'material', 'm': 'material', 'mat': 'material', 'color': 'color', 'c': 'color',
    'color1': 'color1', 'color2': 'color2', 'size': 'size', 'v': 'volume', 'volume': 'volume', 'pitch': 'pitch',
}


def split_top(s, sep):
    out, depth, cur, quote = [], 0, '', False
    for ch in s:
        if ch == '"':
            quote = not quote
        if not quote:
            if ch in '[{':
                depth += 1
            elif ch in ']}':
                depth -= 1
        if ch == sep and depth == 0 and not quote:
            out.append(cur)
            cur = ''
        else:
            cur += ch
    out.append(cur)
    return out


def parse_args(s):
    args = {}
    for part in split_top(s, ';'):
        if '=' in part:
            k, v = part.split('=', 1)
            args[k.strip().lower()] = v.strip().strip('"')
    return args


LINE = re.compile(r'^\s*([\w:.\-]+)\s*(\{.*?\})?\s*(.*)$', re.S)


def parse_line(line):
    """→ (mecánica en minúsculas, args, objetivo, args del objetivo, disparador)"""
    line = str(line).strip()
    if line.startswith('-'):
        line = line[1:].strip()
    # nombre{...}: busca la llave que cierra al mismo nivel
    m = re.match(r'^([\w:.\-]+)', line)
    if not m:
        return None
    name = m.group(1)
    rest = line[len(name):]
    args = {}
    if rest.startswith('{'):
        depth = 0
        for i, ch in enumerate(rest):
            depth += ch == '{'
            depth -= ch == '}'
            if depth == 0:
                args = parse_args(rest[1:i])
                rest = rest[i + 1:]
                break
    target, targs, trigger = '', {}, ''
    tm = re.search(r'@([\w]+)(\{[^}]*\})?', rest)
    if tm:
        target = tm.group(1).lower()
        if tm.group(2):
            targs = parse_args(tm.group(2)[1:-1])
    gm = re.search(r'~(\w+)', rest)
    if gm:
        trigger = gm.group(1).lower()
    name = name.lower()
    if name.startswith('e:'):
        name = 'effect:' + name[2:]
    return name, args, target, targs, trigger


def load(path):
    with open(path, encoding='utf-8') as f:
        return yaml.safe_load(f) or {}


def skills_of(entry):
    if not isinstance(entry, dict):
        return []
    return [parse_line(l) for l in (entry.get('Skills') or []) if parse_line(l)]
