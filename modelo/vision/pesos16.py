# Pesos en fp16 dentro del archivo (la mitad de tamaño); al cargar, Cast a fp32: el cálculo sigue en fp32 (mismo
# resultado: diferencia máxima ~0.01 en los logits, la misma clase o punto en todos los píxeles probados).
# También quita los pesos que ningún nodo usa. Uso: python3 pesos16.py entrada.onnx salida.onnx
import onnx,sys,numpy as np
from onnx import numpy_helper,helper,TensorProto
m=onnx.load(sys.argv[1]);g=m.graph;nuevos=[];n=0
usados={i for nd in g.node for i in nd.input}
for init in list(g.initializer):
    if init.name not in usados:g.initializer.remove(init);continue
    if init.data_type!=TensorProto.FLOAT:continue
    a=numpy_helper.to_array(init)
    if a.size<256:continue
    h=numpy_helper.from_array(a.astype(np.float16),init.name+'_h')
    g.initializer.remove(init);g.initializer.append(h)
    nuevos.append(helper.make_node('Cast',[init.name+'_h'],[init.name],to=TensorProto.FLOAT,name='cast_'+str(n)));n+=1
for x in reversed(nuevos):g.node.insert(0,x)
onnx.checker.check_model(m);onnx.save(m,sys.argv[2]);print('casts',n)
