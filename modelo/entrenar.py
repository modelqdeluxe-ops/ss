"""Ajusta Qwen2.5-0.5B-Instruct (494M parámetros) para Rumi con LoRA, en CPU.

Uso: python3 modelo/entrenar.py --base ~/work/qwen --salida ~/work/rumi-hf
La pérdida solo cuenta las respuestas de Rumi, no la Guía ni la pregunta.
"""
import argparse, json, math, random, time
from pathlib import Path

import torch
import torch.nn.functional as F
from peft import LoraConfig, get_peft_model
from transformers import AutoModelForCausalLM, AutoTokenizer

ap = argparse.ArgumentParser()
ap.add_argument('--base', required=True)
ap.add_argument('--salida', required=True)
ap.add_argument('--datos', default=str(Path(__file__).parent / 'datos' / 'train.jsonl'))
ap.add_argument('--epocas', type=int, default=3)
ap.add_argument('--lote', type=int, default=2)
ap.add_argument('--acum', type=int, default=4)
ap.add_argument('--lr', type=float, default=2e-4)
ap.add_argument('--rango', type=int, default=32)
args = ap.parse_args()

torch.manual_seed(0)
random.seed(0)
torch.set_num_threads(4)

tok = AutoTokenizer.from_pretrained(args.base)
modelo = AutoModelForCausalLM.from_pretrained(args.base, dtype=torch.float32)
modelo = get_peft_model(modelo, LoraConfig(
    r=args.rango, lora_alpha=2 * args.rango, lora_dropout=0.05, task_type='CAUSAL_LM',
    target_modules=['q_proj', 'k_proj', 'v_proj', 'o_proj', 'gate_proj', 'up_proj', 'down_proj']))
modelo.print_trainable_parameters()


def codificar(ej):
    msgs = ej['messages']
    previo = tok.apply_chat_template(msgs[:-1], add_generation_prompt=True, tokenize=False)
    completo = tok.apply_chat_template(msgs, tokenize=False)
    ids_p = tok(previo, add_special_tokens=False)['input_ids']
    ids = tok(completo, add_special_tokens=False)['input_ids']
    assert ids[:len(ids_p)] == ids_p
    etiquetas = [-100] * len(ids_p) + ids[len(ids_p):]
    return ids, etiquetas


datos = [codificar(json.loads(l)) for l in open(args.datos, encoding='utf8') if l.strip()]
print('ejemplos', len(datos), 'tokens promedio', sum(len(d[0]) for d in datos) // len(datos))

base = modelo.base_model.model
params = [p for p in modelo.parameters() if p.requires_grad]
opt = torch.optim.AdamW(params, lr=args.lr, weight_decay=0.0)
pasos_totales = args.epocas * math.ceil(len(datos) / (args.lote * args.acum))
calentar = max(1, pasos_totales // 20)
sched = torch.optim.lr_scheduler.LambdaLR(
    opt, lambda s: min(1, (s + 1) / calentar) * 0.5 * (1 + math.cos(math.pi * min(1, s / pasos_totales))))


def lotes():
    # agrupa por largo parecido para no rellenar de más
    orden = sorted(range(len(datos)), key=lambda i: len(datos[i][0]) + random.random() * 40)
    grupos = [orden[i:i + args.lote] for i in range(0, len(orden), args.lote)]
    random.shuffle(grupos)
    return grupos


modelo.train()
paso = 0
t0 = time.time()
for ep in range(args.epocas):
    perdida_ep, n_ep = 0.0, 0
    for k, g in enumerate(lotes()):
        L = max(len(datos[i][0]) for i in g)
        ids = torch.full((len(g), L), tok.pad_token_id)
        lab = torch.full((len(g), L), -100)
        att = torch.zeros((len(g), L), dtype=torch.long)
        for j, i in enumerate(g):
            a, b = datos[i]
            ids[j, :len(a)] = torch.tensor(a)
            lab[j, :len(b)] = torch.tensor(b)
            att[j, :len(a)] = 1
        with torch.autocast('cpu', dtype=torch.bfloat16):
            h = base.model(input_ids=ids, attention_mask=att).last_hidden_state
            m = lab[:, 1:] != -100
            logits = base.lm_head(h[:, :-1][m]).float()
            perdida = F.cross_entropy(logits, lab[:, 1:][m])
        (perdida / args.acum).backward()
        perdida_ep += perdida.item()
        n_ep += 1
        if (k + 1) % args.acum == 0:
            torch.nn.utils.clip_grad_norm_(params, 1.0)
            opt.step()
            sched.step()
            opt.zero_grad()
            paso += 1
            if paso % 10 == 0:
                print(f'época {ep + 1} paso {paso}/{pasos_totales} pérdida {perdida_ep / n_ep:.4f} '
                      f'{(time.time() - t0) / 60:.1f} min', flush=True)
    print(f'== época {ep + 1} pérdida media {perdida_ep / n_ep:.4f}', flush=True)
    modelo.save_pretrained(f'{args.salida}-lora-ep{ep + 1}')

modelo.save_pretrained(args.salida + '-lora')
fusionado = modelo.merge_and_unload()
fusionado.save_pretrained(args.salida, safe_serialization=True)
tok.save_pretrained(args.salida)
print('guardado en', args.salida, f'{(time.time() - t0) / 60:.1f} min')
