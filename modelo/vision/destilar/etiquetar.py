# Etiquetas del maestro (RTMW-l, 133 puntos) para destilar el modelo de puntos del video.
# Personas de COCO train2017 con fotos en td/img: recorte de su caja (25 % de margen, 192×256), puntos del maestro
# en coordenadas de la foto; y los 17 puntos marcados a mano de COCO.
import json,os,sys,numpy as np,onnxruntime as ort,time
from PIL import Image
K=json.load(open('kp_train.json'));imgs={int(f[:-4]) for f in os.listdir('td/img')};TAM={i['id']:(i['width'],i['height']) for i in K['images']}
A=[a for a in K['annotations'] if a['image_id'] in imgs and not a['iscrowd'] and a['num_keypoints']>=8 and a['bbox'][3]>=160]
print('personas',len(A),flush=True)
o=ort.SessionOptions();o.intra_op_num_threads=4
T=ort.InferenceSession('pose/wb_rtmw-dw-x-l_simcc-cocktail14_270e-256x192_20231122/end2end.onnx',o)
M=np.array([123.675,116.28,103.53]);SD=np.array([58.395,57.12,57.375])
def pico(v):
    i=v.argmax(-1);n=v.shape[-1];x=i.astype(np.float64)
    for k in range(len(i)):
        j=i[k]
        if 0<j<n-1:
            a,b,c=v[k,j-1],v[k,j],v[k,j+1];d=a-2*b+c
            if d<0:x[k]+=.5*(a-c)/d
    return x,v.max(-1)
out=[];t0=time.time()
for n,a in enumerate(A):
    # (las fotos de td/img están reducidas: la caja y los puntos a mano de COCO se llevan a su tamaño)
    im=Image.open(f"td/img/{a['image_id']}.jpg").convert('RGB');f=im.width/TAM[a['image_id']][0]
    a=dict(a,bbox=[v*f for v in a['bbox']],keypoints=[v*f if k%3<2 else v for k,v in enumerate(a['keypoints'])])
    x,y,w,h=a['bbox'];cx,cy=x+w/2,y+h/2;w*=1.25;h*=1.25
    if w/h>.75:h=w/.75
    else:w=h*.75
    c=im.transform((192,256),Image.EXTENT,(cx-w/2,cy-h/2,cx+w/2,cy+h/2),Image.BILINEAR)
    z=((np.asarray(c,np.float32)-M)/SD).transpose(2,0,1)[None].astype(np.float32)
    X,Y=T.run(None,{'input':z});px,cx_=pico(X[0]);py,cy_=pico(Y[0])
    kx=cx-w/2+(px/2+.5)/192*w;ky=cy-h/2+(py/2+.5)/256*h
    out.append((a['image_id'],a['bbox'],np.stack([kx,ky,np.minimum(cx_,cy_)],1).astype(np.float32),np.array(a['keypoints'],np.float32).reshape(17,3)))
    if n%500==499:print(n+1,round(time.time()-t0),'s',flush=True)
ids=np.array([z[0] for z in out]);bb=np.array([z[1] for z in out],np.float32);tk=np.stack([z[2] for z in out]);gk=np.stack([z[3] for z in out])
np.savez_compressed('dest/etiquetas.npz',ids=ids,bb=bb,tk=tk,gk=gk);print('listo',len(out))
