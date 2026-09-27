"""Evalúa un modelo de embeddings: acierto del tema y detección de FUERA.

Uso: python3 modelo/embeddings/evaluar.py <carpeta del modelo>
Clasifica por vecinos: los temas (texto de la Guía) y las preguntas de entrenamiento son las
referencias; la pregunta toma la clase del vecino más parecido.
"""
import sys
import numpy as np
from sentence_transformers import SentenceTransformer
sys.path.insert(0, __import__('os').path.dirname(__file__))
from datos import guia, texto_tema, preguntas, dividir

m = SentenceTransformer(sys.argv[1], device='cpu')
G = guia()
tr, ev = dividir(preguntas())
ref_txt = ['passage: ' + texto_tema(e) for e in G] + ['query: ' + q for q, _ in tr]
ref_cls = [e['t'] for e in G] + [c for _, c in tr]
R = m.encode(ref_txt, normalize_embeddings=True, batch_size=32)
E = m.encode(['query: ' + q for q, _ in ev], normalize_embeddings=True, batch_size=32)
S = E @ R.T
ok = fu_ok = fu_n = tema_n = tema_ok = 0
for i, (q, c) in enumerate(ev):
    p = ref_cls[int(S[i].argmax())]
    ok += p == c
    if c == 'FUERA':
        fu_n += 1; fu_ok += p == 'FUERA'
    elif c != 'APP':
        tema_n += 1; tema_ok += p == c
print(f'evaluación: {len(ev)} preguntas | total {ok/len(ev):.1%} | temas {tema_ok}/{tema_n} = {tema_ok/max(1,tema_n):.1%} | fuera {fu_ok}/{fu_n} = {fu_ok/max(1,fu_n):.1%}')
