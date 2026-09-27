"""Ajusta multilingual-e5-base para que Rumi entienda preguntas de engorde.

Cada pregunta se acerca al texto de su tema (y FUERA / APP tienen su propio texto).
Uso: python3 modelo/embeddings/entrenar.py <modelo base> <salida> [--todo]
  --todo: entrena con todas las preguntas (sin separar evaluación), para la versión final.
"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
import torch
from datasets import Dataset
from sentence_transformers import SentenceTransformer, SentenceTransformerTrainer, SentenceTransformerTrainingArguments, losses
from sentence_transformers.training_args import BatchSamplers
from datos import guia, texto_tema, preguntas, dividir

CLASES_EXTRA = {
    'FUERA': 'Temas que no son de ganado de engorde ni de la app Rumentis: política, deportes, noticias, cocina, '
             'tecnología, salud de personas, dinero y trámites, estudios, entretenimiento, lechería, cerdos, aves y otros animales.',
    'APP': 'Órdenes y preguntas sobre mis propios datos en la app Rumentis: qué hago hoy, anota alimento, pesé el lote, '
           'vendí, cuántas cabezas tengo, cuánto voy a ganar, mi conversión, mis gastos, mi mejor lote, modo oscuro.',
}

base, salida = sys.argv[1], sys.argv[2]
todo = '--todo' in sys.argv
torch.set_num_threads(4)
G = guia()
pasaje = {e['t']: 'passage: ' + texto_tema(e) for e in G}
pasaje.update({k: 'passage: ' + v for k, v in CLASES_EXTRA.items()})
Q = preguntas()
tr, _ = (Q, []) if todo else dividir(Q)
filas = {'ancla': [], 'positivo': []}
for q, c in tr:
    filas['ancla'].append('query: ' + q)
    filas['positivo'].append(pasaje[c])
# cada tema también se acerca a su propio título (preguntas cortas tipo "¿qué es la acidosis?")
for e in G:
    filas['ancla'].append('query: ' + e['t'])
    filas['positivo'].append(pasaje[e['t']])
ds = Dataset.from_dict(filas).shuffle(seed=1)
print('pares de entrenamiento', len(ds), flush=True)

m = SentenceTransformer(base, device='cpu')
m.max_seq_length = 128
args = SentenceTransformerTrainingArguments(
    output_dir=salida + '-tmp', num_train_epochs=3, per_device_train_batch_size=64,
    learning_rate=3e-5, warmup_steps=0.1, batch_sampler=BatchSamplers.NO_DUPLICATES,
    logging_steps=10, save_strategy='no', report_to=[], use_cpu=True, seed=1)
t = SentenceTransformerTrainer(model=m, args=args, train_dataset=ds,
                               loss=losses.CachedMultipleNegativesRankingLoss(m, scale=30, mini_batch_size=16))
t.train()
m.save(salida)
print('guardado', salida, flush=True)
