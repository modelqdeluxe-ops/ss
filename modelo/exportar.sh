#!/usr/bin/env bash
# Convierte el modelo ajustado (formato Hugging Face) a GGUF, lo cuantiza y lo parte
# en trozos para la app: app/assets/rumi/rumi-*.gguf + app/assets/rumi/modelo.json
#
# Uso: modelo/exportar.sh ~/work/rumi-hf [q8_0|q6_k|q4_k_m]
set -euo pipefail
HF="$1"
QUANT="${2:-q8_0}"
RAIZ="$(cd "$(dirname "$0")/.." && pwd)"
LLAMA="${LLAMA_CPP:-$HOME/work/llama.cpp}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

python3 "$LLAMA/convert_hf_to_gguf.py" "$HF" --outtype f16 --outfile "$TMP/rumi-f16.gguf"
"$LLAMA/build/bin/llama-quantize" "$TMP/rumi-f16.gguf" "$TMP/rumi.gguf" "$QUANT"

OUT="$RAIZ/app/assets/rumi"
rm -rf "$OUT"
mkdir -p "$OUT"
"$LLAMA/build/bin/llama-gguf-split" --split --split-max-size 90M "$TMP/rumi.gguf" "$OUT/rumi"

python3 - "$OUT" "$QUANT" <<'EOF'
import json, os, sys, time
out, quant = sys.argv[1], sys.argv[2]
archivos = sorted(f for f in os.listdir(out) if f.endswith('.gguf'))
json.dump({
    'version': time.strftime('%Y%m%d') + '-' + quant,
    'base': 'Qwen2.5-0.5B-Instruct (Apache 2.0), ajustado para Rumi',
    'parametros': 494032768,
    'cuantizacion': quant,
    'archivos': archivos,
    'bytes': sum(os.path.getsize(os.path.join(out, f)) for f in archivos),
}, open(os.path.join(out, 'modelo.json'), 'w'), ensure_ascii=False, indent=1)
EOF
cat "$OUT/modelo.json"
