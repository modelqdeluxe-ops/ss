# Medidas de un bovino en una foto: silueta del modelo preciso (RF-DETR, sobre un recorte alrededor del animal) y
# puntos AP-10K (0 ojo i, 1 ojo d, 2 nariz, 3 cuello, 4 base de la cola, 5/8 hombros, 6/9 codos, 7/10 manos,
# 11/14 caderas, 12/15 rodillas, 13/16 patas). Todo en píxeles de la foto.
import os,numpy as np,onnxruntime as ort,torch,torch.nn.functional as F
from PIL import Image,ImageDraw
from scipy import ndimage
MEAN=np.array([.485,.456,.406],np.float32);STD=np.array([.229,.224,.225],np.float32)
o_=ort.SessionOptions();o_.intra_op_num_threads=4
V=os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..','vision')+'/'
SEG=ort.InferenceSession(V+'seg.onnx',o_);POSE=ort.InferenceSession(V+'animal_m.onnx',o_)
ESP=list(range(17))
for a,b in [[0,1],[5,8],[6,9],[7,10],[11,14],[12,15],[13,16]]:ESP[a]=b;ESP[b]=a
def norm(a):return ((a-MEAN)/STD).transpose(2,0,1)[None].astype(np.float32)
def seg(img,box=None):
    W,H=img.size
    if box is None:Z=(0,0,W,H)
    else:
        x0,y0,x1,y1=box;g=.1;w,h=x1-x0,y1-y0;Z=(max(0,int(x0-g*w)),max(0,int(y0-g*h)),min(W,int(x1+g*w)),min(H,int(y1+g*h)))
    C=img.crop(Z);a=np.asarray(C.resize((312,312),Image.BILINEAR),np.float32)/255
    d,l,m=SEG.run(['dets','labels','masks'],{'input':norm(a)})
    sc=1/(1+np.exp(-l[0][:,21]));q=sc.argmax()
    if sc[q]<.3:return None,0
    up=F.interpolate(torch.from_numpy(m[0][q][None,None].astype(np.float32)),size=(Z[3]-Z[1],Z[2]-Z[0]),mode='bilinear',align_corners=False)[0,0].numpy()>0
    M=np.zeros((H,W),bool);M[Z[1]:Z[3],Z[0]:Z[2]]=up
    lab,n=ndimage.label(M)
    if n>1:M=lab==(1+np.argmax(ndimage.sum(M,lab,range(1,n+1))))
    return M,float(sc[q])
def caja(M):ys,xs=np.where(M);return xs.min(),ys.min(),xs.max()+1,ys.max()+1
def pico(v):
    j=int(v.argmax());x=float(j)
    if 0<j<len(v)-1:
        a,b,c=v[j-1],v[j],v[j+1];d=a-2*b+c
        if d<0:x+=.5*(a-c)/d
    return x,float(v[j])
def pose(img,box):
    x0,y0,x1,y1=box;w,h=(x1-x0)*1.25,(y1-y0)*1.25;s=max(w,h);cx,cy=(x0+x1)/2,(y0+y1)/2
    Z=(cx-s/2,cy-s/2,s);C=img.crop((int(cx-s/2),int(cy-s/2),int(cx+s/2),int(cy+s/2))).resize((256,256),Image.BILINEAR)
    a=np.asarray(C,np.float32)/255;P=[]
    for fl in (0,1):
        x=norm(a[:,::-1].copy() if fl else a);X,Y=POSE.run(None,{'input':x});X,Y=X[0],Y[0]
        Q=[]
        for k in range(17):
            px,cxv=pico(X[k]);py,cyv=pico(Y[k]);Q.append([(px+.5)/X.shape[1],(py+.5)/Y.shape[1],min(cxv,cyv)])
        Q=np.array(Q)
        if fl:Q=Q[ESP];Q[:,0]=1-Q[:,0]
        P.append(Q)
    P=(P[0]+P[1])/2;s2=int(s)
    P[:,0]=int(cx-s/2)+P[:,0]*s2;P[:,1]=int(cy-s/2)+P[:,1]*s2;return P
def medir(img):
    img=img.convert('RGB');M,sc=seg(img)
    if M is None:return None
    M,sc=seg(img,caja(M))
    if M is None:return None
    return M,pose(img,caja(M)),sc
def dibujar(img,M,P,lineas=(),nombre='x.jpg'):
    im=img.convert('RGB').copy();ov=Image.new('RGB',im.size,(0,255,120));im.paste(ov,(0,0),Image.fromarray((M*90).astype(np.uint8)))
    d=ImageDraw.Draw(im)
    for k,(x,y,c) in enumerate(P):
        if c>.3:d.ellipse([x-6,y-6,x+6,y+6],fill='white');d.text((x+7,y-7),str(k),fill='yellow')
    for (a,b,col) in lineas:d.line([a,b],fill=col,width=4)
    im.save(nombre)
