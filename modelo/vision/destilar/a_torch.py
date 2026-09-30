# ONNX de RTMPose/DWPose → módulo de PyTorch entrenable (onnx2torch)
import onnx,sys,numpy as np
from onnx import numpy_helper
def preparar(p,out):
    m=onnx.load(p);g=m.graph
    for n in [n for n in g.node if n.op_type=='Constant']:
        t=n.attribute[0].t;t.name=n.output[0];g.initializer.append(t);g.node.remove(n)
    g.initializer.append(numpy_helper.from_array(np.array(1e30,np.float32),'clip_max_'))
    for n in g.node:
        if n.op_type=='Clip':
            while len(n.input)<3:n.input.append('')
            if n.input[2]=='':n.input[2]='clip_max_'
    onnx.save(m,out)
if __name__=='__main__':preparar(sys.argv[1],sys.argv[2])
