# compara modelos de silueta contra la silueta real de COCO: IoU, error de área y de alto
import json,sys,numpy as np,onnxruntime as ort,torch,torch.nn.functional as F
from PIL import Image,ImageDraw
from scipy import ndimage
S=json.load(open('ev/set.json'))
MEAN=np.array([.485,.456,.406],np.float32);STD=np.array([.229,.224,.225],np.float32)
def gt(o):
    m=Image.new('L',(o['W'],o['H']),0);d=ImageDraw.Draw(m)
    for p in o['seg']:d.polygon(list(map(float,p)),fill=1)
    return np.array(m,bool)
def prep(img,R):
    a=np.asarray(img.convert('RGB').resize((R,R),Image.BILINEAR),np.float32)/255;return ((a-MEAN)/STD).transpose(2,0,1)[None].astype(np.float32)
def up(l,h,w):return F.interpolate(torch.from_numpy(np.ascontiguousarray(l))[None],size=(h,w),mode='bilinear',align_corners=False)[0].numpy()
class Rapido:
    def __init__(s,f,R):s.s=ort.InferenceSession(f,providers=['CPUExecutionProvider']);s.R=R
    def __call__(s,img,cls):
        l=s.s.run(None,{'input':prep(img,s.R)})[0][0];k=1 if cls==1 else 2
        L=up(l,img.height,img.width);m=L.argmax(0)==k
        lab,n=ndimage.label(m)
        if n>1:m=lab==(1+np.argmax(ndimage.sum(m,lab,range(1,n+1))))
        return m
class Detr:
    def __init__(s,f):s.s=ort.InferenceSession(f,providers=['CPUExecutionProvider'])
    def __call__(s,img,cls):
        o=dict(zip(['dets','labels','masks'],s.s.run(['dets','labels','masks'],{'input':prep(img,312)})))
        sc=1/(1+np.exp(-o['labels'][0][:,cls]));q=sc.argmax()
        return up(o['masks'][0][q][None],img.height,img.width)[0]>0
def recorte(o,g=.15):
    x,y,w,h=o['bbox'];x0=max(0,int(x-g*w));y0=max(0,int(y-g*h));x1=min(o['W'],int(x+w+g*w+1));y1=min(o['H'],int(y+h+g*h+1));return x0,y0,x1,y1
def evalua(mod,crop):
    R={1:[],21:[]}
    for o in S:
        img=Image.open(f"ev/{o['id']}.jpg");G=gt(o);P=np.zeros_like(G)
        if crop:x0,y0,x1,y1=recorte(o);P[y0:y1,x0:x1]=mod(img.crop((x0,y0,x1,y1)),o['cls'])
        else:P=mod(img,o['cls'])
        i=(P&G).sum();u=(P|G).sum();ys=np.where(G.any(1))[0];yp=np.where(P.any(1))[0]
        hg=ys[-1]-ys[0]+1;hp=(yp[-1]-yp[0]+1) if len(yp) else 0
        R[o['cls']].append((i/u,abs(P.sum()/G.sum()-1),abs(hp/hg-1)))
    return {('persona' if k==1 else 'vaca'):[round(float(np.mean([r[j] for r in v])),3) for j in range(3)]+[round(float(np.median([r[0] for r in v])),3)] for k,v in R.items()}
if __name__=='__main__':
    M={'rfdetr':lambda:Detr('seg.onnx')}
    for a in sys.argv[1:]:
        if a.startswith('rfdetr'):continue
    for a in sys.argv[1:]:
        nom,modo=a.split(':');
        mod=M[nom]() if nom in M else Rapido(nom,int(nom.split('_')[-1].split('.')[0]))
        print(a,'[IoU, err área, err alto, IoU mediana]',evalua(mod,modo=='crop'),flush=True)
