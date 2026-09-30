# Compara modelos de puntos (ONNX): error de los 17 del cuerpo contra COCO val (a mano) con la caja bien puesta y con
# la caja movida (como en el video), y acuerdo con el maestro RTMW-l en pies y manos (133 puntos).
import json,os,sys,numpy as np,onnxruntime as ort
from PIL import Image
K=json.load(open('kp_val.json'));ids={int(f[:-4]) for f in os.listdir('ev') if f.endswith('.jpg')}
A=[a for a in K['annotations'] if a['image_id'] in ids and a['num_keypoints']>=12 and not a['iscrowd'] and a['bbox'][3]>150]
M=np.array([123.675,116.28,103.53]);SD=np.array([58.395,57.12,57.375])
def pico(v):
    i=v.argmax(-1);n=v.shape[-1];x=i.astype(np.float64)
    for k in range(len(i)):
        j=i[k]
        if 0<j<n-1:
            a,b,c=v[k,j-1],v[k,j],v[k,j+1];d=a-2*b+c
            if d<0:x[k]+=.5*(a-c)/d
    return x,v.max(-1)
def corre(s,im,box):
    x,y,w,h=box;cx,cy=x+w/2,y+h/2;w*=1.25;h*=1.25
    if w/h>.75:h=w/.75
    else:w=h*.75
    c=im.transform((192,256),Image.EXTENT,(cx-w/2,cy-h/2,cx+w/2,cy+h/2),Image.BILINEAR)
    z=((np.asarray(c,np.float32)-M)/SD).transpose(2,0,1)[None].astype(np.float32)
    X,Y=s.run(None,{'input':z});px,_=pico(X[0]);py,_=pico(Y[0])
    return np.stack([cx-w/2+(px/2+.5)/192*w,cy-h/2+(py/2+.5)/256*h],1)
rng=np.random.RandomState(3);JIT=[(rng.randn()*.08,rng.randn()*.06,rng.uniform(.85,1.3)) for _ in A]
def movida(b,j):x,y,w,h=b;dx,dy,s=j;cx,cy=x+w/2+dx*w,y+h/2+dy*h;return [cx-w*s/2,cy-h*s/2,w*s,h*s]
o=ort.SessionOptions();o.intra_op_num_threads=4
maestro='dest/maestro_ev.npy'
if not os.path.exists(maestro):
    T=ort.InferenceSession('pose/wb_rtmw-dw-x-l_simcc-cocktail14_270e-256x192_20231122/end2end.onnx',o)
    np.save(maestro,np.stack([corre(T,Image.open(f"ev/{a['image_id']}.jpg").convert('RGB'),a['bbox']) for a in A]))
MT=np.load(maestro)
for p in sys.argv[1:]:
    s=ort.InferenceSession(p,o);eb=[];ej=[];ep=[];em=[]
    for i,a in enumerate(A):
        im=Image.open(f"ev/{a['image_id']}.jpg").convert('RGB');G=np.array(a['keypoints']).reshape(-1,3);v=G[:,2]>0;h=a['bbox'][3]
        P=corre(s,im,a['bbox']);Pj=corre(s,im,movida(a['bbox'],JIT[i]))
        eb+=list(np.linalg.norm(P[:17][v]-G[v,:2],axis=1)/h);ej+=list(np.linalg.norm(Pj[:17][v]-G[v,:2],axis=1)/h)
        ep+=list(np.linalg.norm(P[17:23]-MT[i][17:23],axis=1)/h);em+=list(np.linalg.norm(P[91:]-MT[i][91:],axis=1)/h)
    f=lambda e:f'{np.mean(e)*100:.2f}/{np.median(e)*100:.2f}'
    print(os.path.basename(p),'cuerpo (media/mediana %):',f(eb),'· caja movida:',f(ej),'· pies vs maestro:',f(ep),'· manos vs maestro:',f(em),flush=True)
