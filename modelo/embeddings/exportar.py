"""Exporta el modelo de comprensión a la app.

- app/assets/rumi/entiende-NN.gguf: el modelo (GGUF Q8_0) en trozos de 90 MB.
- app/assets/rumi/referencias.bin: vectores int8 de temas y preguntas de ejemplo (+ escala por fila).
- app/assets/rumi/modelo.json: archivos, referencias, clases y umbrales.

Uso: python3 modelo/embeddings/exportar.py <modelo ajustado>
"""
import json, os, subprocess, sys, tempfile, time
import numpy as np
sys.path.insert(0, os.path.dirname(__file__))
from sentence_transformers import SentenceTransformer
from datos import guia, texto_tema, preguntas, RAIZ, CLASES_EXTRA

modelo = sys.argv[1]
llama = os.environ.get('LLAMA_CPP', os.path.expanduser('~/work/llama.cpp'))
out = RAIZ / 'app/assets/rumi'
for f in out.glob('*'):
    f.unlink()
out.mkdir(parents=True, exist_ok=True)

with tempfile.TemporaryDirectory() as tmp:
    gguf = os.path.join(tmp, 'entiende.gguf')
    subprocess.run([sys.executable, os.path.join(llama, 'convert_hf_to_gguf.py'), modelo, '--outtype', 'q8_0',
                    '--outfile', gguf], check=True, capture_output=True)
    subprocess.run(['split', '-b', '90M', '-d', '-a', '2', '--additional-suffix=.gguf', gguf, str(out / 'entiende-')], check=True)
archivos = sorted(f.name for f in out.glob('entiende-*.gguf'))

# Referencias: cada tema (su texto), FUERA y APP, y todas las preguntas de ejemplo.
m = SentenceTransformer(modelo, device='cpu')
G = guia()
Q = preguntas()
txt = ['passage: ' + texto_tema(e) for e in G] + ['passage: ' + v for v in CLASES_EXTRA.values()] + ['query: ' + q for q, _ in Q]
cls = [e['t'] for e in G] + list(CLASES_EXTRA) + [c for _, c in Q]
V = m.encode(txt, normalize_embeddings=True, batch_size=32).astype(np.float32)
clases = [e['t'] for e in G] + list(CLASES_EXTRA)
ci = {c: i for i, c in enumerate(clases)}
esc = np.abs(V).max(axis=1) / 127.0
q8 = np.round(V / esc[:, None]).astype(np.int8)
(out / 'referencias.bin').write_bytes(q8.tobytes() + esc.astype(np.float32).tobytes())

umbrales = json.load(open(RAIZ / 'modelo/embeddings/umbrales.json'))
man = {
    'version': time.strftime('%Y%m%d') + '-e5b-rumi',
    'base': 'intfloat/multilingual-e5-base (MIT), ajustado para Rumi',
    'parametros': 278043648,
    'archivos': archivos,
    'bytes': sum((out / f).stat().st_size for f in archivos),
    'umbrales': umbrales,
    'referencias': {'vectores': 'referencias.bin', 'filas': len(txt), 'dim': int(V.shape[1]),
                    'clases': clases, 'clase_de_fila': [ci[c] for c in cls]},
}
json.dump(man, open(out / 'modelo.json', 'w'), ensure_ascii=False)
print('modelo:', archivos, f"{man['bytes']/1e6:.0f} MB", '| referencias:', len(txt), 'filas,', len(clases), 'clases')
