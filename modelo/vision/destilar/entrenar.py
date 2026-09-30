# Destila el modelo de puntos del video (DWPose-t, 133 puntos) con las etiquetas del maestro (RTMW-l) y los puntos a mano
# de COCO. Recortes con la caja movida, escalada y girada (como la del video), espejo, color, desenfoque y baja
# resolución. Pérdida SimCC (KL con etiquetas gaussianas, beta 10), como el entrenamiento original de RTMPose.
import numpy as np,torch,torch.nn.functional as F,math,time,os,sys,random
from PIL import Image,ImageFilter,ImageEnhance
from onnx2torch import convert
torch.set_num_threads(3)
_E=np.load('dest/etiquetas.npz');E={k:_E[k] for k in _E.files};N=len(E['ids'])
FLIP=None
def espejo():
    P=[[1,2],[3,4],[5,6],[7,8],[9,10],[11,12],[13,14],[15,16],[17,20],[18,21],[19,22]]
    for i in range(8):P.append([23+i,39-i])
    for i in range(5):P.append([40+i,49-i])
    P+= [[54,58],[55,57]]
    for a,b in [[36,45],[37,44],[38,43],[39,42],[40,47],[41,46],[48,54],[49,53],[50,52],[55,59],[56,58],[60,64],[61,63],[65,67]]:P.append([23+a,23+b])
    for i in range(21):P.append([91+i,112+i])
    m=list(range(133))
    for a,b in P:m[a]=b;m[b]=a
    return np.array(m)
FLIP=espejo()
MEAN=np.array([123.675,116.28,103.53],np.float32);SD=np.array([58.395,57.12,57.375],np.float32)
def muestra(i,aug=True):
    im=Image.open(f"td/img/{E['ids'][i]}.jpg").convert('RGB');x,y,w,h=E['bb'][i];tk=E['tk'][i].copy();gk=E['gk'][i]
    K=tk.copy();W=np.clip((tk[:,2]-.3)/.5,0,1)
    v=gk[:,2]>0;K[:17][v]=np.concatenate([gk[v,:2],np.ones((v.sum(),1))],1);W[:17][v]=1.
    cx,cy=x+w/2,y+h/2;s=1.25;rot=0.
    if aug:
        cx+=np.random.randn()*.08*w;cy+=np.random.randn()*.06*h;s*=np.random.uniform(.85,1.3)
        if random.random()<.5:rot=np.random.uniform(-15,15)
    bw,bh=w*s,h*s
    if bw/bh>.75:bh=bw/.75
    else:bw=bh*.75
    # afín: del recorte girado a 192×256
    c,sn=math.cos(math.radians(rot)),math.sin(math.radians(rot));sx,sy=bw/192,bh/256
    # punto de salida (u,v) → foto: cx + R·((u-96)sx, (v-128)sy)
    A=(c*sx,-sn*sy,cx-c*sx*96+sn*sy*128,sn*sx,c*sy,cy-sn*sx*96-c*sy*128)
    A0=A
    if aug and random.random()<.3:
        f=np.random.uniform(.35,.7);im=im.resize((max(1,int(im.width*f)),max(1,int(im.height*f))),Image.BILINEAR)
        A=tuple(v*f for v in A[:3])+tuple(v*f for v in A[3:])
    cr=im.transform((192,256),Image.AFFINE,A,Image.BILINEAR)
    if aug:
        if random.random()<.8:cr=ImageEnhance.Brightness(cr).enhance(np.random.uniform(.7,1.3));cr=ImageEnhance.Contrast(cr).enhance(np.random.uniform(.7,1.3))
        if random.random()<.3:cr=cr.filter(ImageFilter.GaussianBlur(np.random.uniform(.5,1.5)))
    # puntos a la salida: inversa de A (A lleva de salida a foto; con escala f también, ya incluida)
    M=np.array([[A0[0],A0[1],A0[2]],[A0[3],A0[4],A0[5]],[0,0,1]]);Mi=np.linalg.inv(M)
    P=(Mi@np.concatenate([K[:,:2],np.ones((133,1))],1).T).T[:,:2]
    a=np.asarray(cr,np.float32)
    if aug and random.random()<.5:a=a[:,::-1];P[:,0]=191-P[:,0];P=P[FLIP];W=W[FLIP]
    fuera=(P[:,0]<0)|(P[:,0]>191)|(P[:,1]<0)|(P[:,1]>255);W=W*(~fuera)
    return ((a-MEAN)/SD).transpose(2,0,1).copy(),P.astype(np.float32),W.astype(np.float32)
class DS(torch.utils.data.Dataset):
    def __init__(s,idx,aug):s.idx=idx;s.aug=aug
    def __len__(s):return len(s.idx)
    def __getitem__(s,k):x,p,w=muestra(s.idx[k],s.aug);return torch.from_numpy(x),torch.from_numpy(p),torch.from_numpy(w)
def perdida(ox,oy,P,Wt,beta=10.):
    bx=torch.arange(384,dtype=torch.float32);by=torch.arange(512,dtype=torch.float32)
    tx=torch.exp(-((bx[None,None]-(P[...,0:1]+.5)*2+.5)**2)/(2*4.9**2));ty=torch.exp(-((by[None,None]-(P[...,1:2]+.5)*2+.5)**2)/(2*5.66**2))
    def kl(o,t):
        lp=F.log_softmax(o*beta,-1);q=F.softmax(t*beta,-1);return (q*(torch.log(q+1e-10)-lp)).sum(-1)
    l=(kl(ox,tx)+kl(oy,ty))*Wt
    return l.sum()/Wt.sum().clamp(min=1)
if __name__=='__main__':
    ep=int(os.environ.get('EPOCAS','3'));bs=32
    m=convert('pose/dw_t_p.onnx');m.train()
    idx=np.random.RandomState(0).permutation(N)
    dl=torch.utils.data.DataLoader(DS(idx,True),batch_size=bs,shuffle=True,num_workers=2,drop_last=True,persistent_workers=True)
    LR=float(os.environ.get('LR','2e-4'));MAX=int(os.environ.get('PASOS','0'))
    opt=torch.optim.AdamW(m.parameters(),lr=LR,weight_decay=.01);tot=MAX or ep*len(dl);paso=0;t0=time.time()
    sch=torch.optim.lr_scheduler.LambdaLR(opt,lambda s:min(1,(s+1)/200)*.5*(1+math.cos(math.pi*s/tot)))
    for e in range(ep):
        acc=0
        for x,p,w in dl:
            ox,oy=m(x);l=perdida(ox,oy,p,w);opt.zero_grad();l.backward();torch.nn.utils.clip_grad_norm_(m.parameters(),5.);opt.step();sch.step();paso+=1;acc+=l.item()
            if MAX and paso>=MAX:break
            if paso%100==0:print(f'época {e+1} paso {paso}/{tot} pérdida {acc/100:.4f} {time.time()-t0:.0f}s',flush=True);acc=0
        torch.save(m.state_dict(),f'dest/t_{os.environ.get("NOMBRE","e")}{e+1}.pt')
        if MAX and paso>=MAX:break
    print('listo')
