# Exporta RF-DETR Seg Nano (preentrenado en COCO) a ONNX. Ver LEEME.md.
import warnings;warnings.filterwarnings('ignore')
from rfdetr import RFDETRSegNano
m=RFDETRSegNano()
p=m.export(output_dir='output',opset_version=17,verbose=False)
print('exportado',p)
