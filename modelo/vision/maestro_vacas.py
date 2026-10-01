# Siluetas del maestro (RF-DETR) para las vacas: en cada foto 'v', las vacas más grandes (hasta 3, de la silueta a mano
# de COCO) se recortan con 10 % de margen; el maestro da su silueta y reemplaza a la de COCO dentro del recorte.
import json,os,numpy as np,onnxruntime as ort,time
from PIL import Image
from scipy import ndimage
import torch,torch.nn.functional as F
L=json.load(open('td/lista.json'));fuera=set(json.load(open('td/fuera_vacas.json')))
sel=[i for i,k in L if k=='v' and i not in fuera]
o=ort.SessionOptions();o.intra_op_num_threads=4;s=ort.InferenceSession(os.path.join(os.path.dirname(os.path.abspath(__file__)),'seg.onnx'),o)
MEAN=np.array([.485,.456,.406],np.float32);STD=np.array([.229,.224,.225],np.float32)
t0=time.time();hechas=0
for n,i in enumerate(sel):
    f=f'td/msk_t/{i}.png'
    if os.path.exists(f):continue
    img=Image.open(f'td/img/{i}.jpg').convert('RGB');M=np.array(Image.open(f'td/msk/{i}.png'));W,H=img.size
    lab,k=ndimage.label(M==2)
    if not k:continue
    tam=ndimage.sum(M==2,lab,range(1,k+1));orden=np.argsort(-tam)[:3];T=M.copy();cambio=False
    for j in orden:
        if tam[j]<.02*W*H:continue
        ys,xs=np.where(lab==j+1);x0,x1,y0,y1=xs.min(),xs.max()+1,ys.min(),ys.max()+1;g=.1;bw,bh=x1-x0,y1-y0
        X0=max(0,int(x0-g*bw));Y0=max(0,int(y0-g*bh));X1=min(W,int(x1+g*bw));Y1=min(H,int(y1+g*bh))
        C=img.crop((X0,Y0,X1,Y1));a=np.asarray(C.resize((312,312),Image.BILINEAR),np.float32)/255
        d,l,m=s.run(['dets','labels','masks'],{'input':((a-MEAN)/STD).transpose(2,0,1)[None].astype(np.float32)})
        sc=1/(1+np.exp(-l[0][:,21]));q=sc.argmax()
        if sc[q]<.5:continue
        up=F.interpolate(torch.from_numpy(m[0][q][None,None].astype(np.float32)),size=(Y1-Y0,X1-X0),mode='bilinear',align_corners=False)[0,0].numpy()>0
        # (solo la vaca de este recorte: lo del maestro que toca la vaca de COCO)
        reg=T[Y0:Y1,X0:X1];esta=(lab[Y0:Y1,X0:X1]==j+1)
        lb,nb=ndimage.label(up)
        if nb>1:
            ov=ndimage.sum(esta,lb,range(1,nb+1));up=lb==(1+np.argmax(ov))
        reg[esta&~up]=0;reg[up&(reg!=1)]=2;T[Y0:Y1,X0:X1]=reg;cambio=True
    if cambio:Image.fromarray(T).save(f);hechas+=1
    if n%100==99:print(n+1,hechas,round(time.time()-t0),'s',flush=True)
print('listo',hechas,flush=True)
