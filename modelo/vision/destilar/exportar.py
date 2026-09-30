# de vuelta a ONNX: el modelo entrenado (state_dict) → ONNX (entrada input [1,3,256,192], salidas simcc_x, simcc_y)
import torch,sys,numpy as np,onnxruntime as ort
from onnx2torch import convert
m=convert('pose/dw_t_p.onnx')
if len(sys.argv)>2 and sys.argv[2]!='-':m.load_state_dict(torch.load(sys.argv[2]))
m.eval();x=torch.randn(1,3,256,192)
torch.onnx.export(m,x,sys.argv[1],input_names=['input'],output_names=['simcc_x','simcc_y'],opset_version=17,dynamo=False)
with torch.no_grad():y=m(x)
r=ort.InferenceSession(sys.argv[1]).run(None,{'input':x.numpy()})
print('diferencia',[float(abs(a.numpy()-b).max()) for a,b in zip(y,r)])
