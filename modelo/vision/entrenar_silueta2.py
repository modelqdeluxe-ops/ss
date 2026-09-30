# Silueta v2: el LR-ASPP afinado (sil_320_e5.pt) más un refinamiento a 1/4 de la entrada con los rasgos de esa
# resolución (capa 3 de MobileNetV3, 24 canales): bordes más finos. La última capa del refinamiento empieza en cero,
# así que al arrancar da exactamente lo mismo que el modelo anterior. 3 clases: 0 fondo, 1 persona, 2 vaca.
import json,os,random,sys,time,math,numpy as np,torch,torch.nn as nn,torch.nn.functional as F
from PIL import Image,ImageEnhance,ImageFilter
from torchvision.models.segmentation import lraspp_mobilenet_v3_large,LRASPP_MobileNet_V3_Large_Weights as LW
torch.set_num_threads(4);R=int(os.environ.get('RES','320'));EP=int(os.environ.get('EPOCAS','6'));BS=16
LR=float(os.environ.get('LR','6e-4'));NOM=os.environ.get('NOMBRE','sil');SUJ=float(os.environ.get('SUJETO','.7'))
MEAN=np.array([.485,.456,.406],np.float32);STD=np.array([.229,.224,.225],np.float32)
L=[x for x in json.load(open('td/lista.json')) if os.path.exists(f'td/msk/{x[0]}.png')]
# vacas ×3 (son pocas); escenas de casa (personas con camas, sillones, sillas…) ×2: el fondo que más confunde
items=[i for i,k in L for _ in range(3 if k=='v' else 2 if k=='c' else 1)]
print('fotos',len(L),'muestras por época',len(items),flush=True)
class DS(torch.utils.data.Dataset):
    def __len__(s):return len(items)
    def __getitem__(s,j):
        iid=items[j];img=Image.open(f'td/img/{iid}.jpg').convert('RGB');m=Image.open(f'td/msk/{iid}.png');W,H=img.size;r=random.random
        a=np.array(m);ys,xs=np.where((a==1)|(a==2))
        asp=math.exp(random.uniform(math.log(.6),math.log(1.6)))   # ancho/alto del recorte
        if len(xs) and r()<SUJ:   # alrededor del sujeto, con margen al azar
            x0,x1,y0,y1=xs.min(),xs.max(),ys.min(),ys.max();bw,bh=x1-x0+1,y1-y0+1;g=random.uniform(.05,.45)
            cw,ch=bw*(1+2*g),bh*(1+2*g)
            if cw/ch<asp:cw=ch*asp
            else:ch=cw/asp
            cx=(x0+x1)/2+random.uniform(-.1,.1)*cw;cy=(y0+y1)/2+random.uniform(-.1,.1)*ch
        else:
            s_=random.uniform(.55,1);ch=min(H,math.sqrt(W*H*s_/asp));cw=min(W,ch*asp);cx=random.uniform(cw/2,W-cw/2);cy=random.uniform(ch/2,H-ch/2)
        box=(cx-cw/2,cy-ch/2,cx+cw/2,cy+ch/2)
        img=img.transform((R,R),Image.EXTENT,box,Image.BILINEAR,fillcolor=(124,116,104))
        m=m.transform((R,R),Image.EXTENT,box,Image.NEAREST,fillcolor=0)
        if r()<.5:img=img.transpose(Image.FLIP_LEFT_RIGHT);m=m.transpose(Image.FLIP_LEFT_RIGHT)
        for E in (ImageEnhance.Brightness,ImageEnhance.Contrast,ImageEnhance.Color):
            if r()<.8:img=E(img).enhance(random.uniform(.7,1.3))
        if r()<.15:img=img.filter(ImageFilter.GaussianBlur(random.uniform(.5,1.5)))
        x=((np.asarray(img,np.float32)/255-MEAN)/STD).transpose(2,0,1)
        return torch.from_numpy(x.copy()),torch.from_numpy(np.array(m,np.int64))
m=lraspp_mobilenet_v3_large(weights=LW.DEFAULT)
# de 21 clases a 3, empezando con los pesos que ya tenía para fondo, persona y vaca
for nm in ('low_classifier','high_classifier'):
    c=getattr(m.classifier,nm);n=nn.Conv2d(c.in_channels,3,1)
    with torch.no_grad():n.weight.copy_(c.weight[[0,15,10]]);n.bias.copy_(c.bias[[0,15,10]])
    setattr(m.classifier,nm,n)
class Env(nn.Module):
    def __init__(s,m):super().__init__();s.m=m
    def forward(s,x):return s.m.classifier(s.m.backbone(x))   # a 1/8 de la entrada
base=Env(m)
if os.environ.get('DESDE'):base.load_state_dict(torch.load(os.environ['DESDE']))
class SilV2(nn.Module):
    def __init__(s,base):
        super().__init__();s.m=base.m;s.m.backbone.return_layers={'3':'f4','4':'low','16':'high'}
        def sep(i,o):return nn.Sequential(nn.Conv2d(i,i,3,padding=1,groups=i,bias=False),nn.BatchNorm2d(i),nn.ReLU(inplace=True),nn.Conv2d(i,o,1,bias=False),nn.BatchNorm2d(o),nn.ReLU(inplace=True))
        s.ref=nn.Sequential(nn.Conv2d(27,32,1,bias=False),nn.BatchNorm2d(32),nn.ReLU(inplace=True),sep(32,32),sep(32,32),nn.Conv2d(32,3,1))
        nn.init.zeros_(s.ref[-1].weight);nn.init.zeros_(s.ref[-1].bias)
    def forward(s,x):
        f=s.m.backbone(x);o=s.m.classifier({'low':f['low'],'high':f['high']})
        o4=F.interpolate(o,size=f['f4'].shape[-2:],mode='bilinear',align_corners=False)
        return o4+s.ref(torch.cat([f['f4'],o4],1))   # a 1/4 de la entrada
net=SilV2(base)
# seguir desde un v2 ya entrenado
if os.environ.get('DESDE2'):net.load_state_dict(torch.load(os.environ['DESDE2']))
dl=torch.utils.data.DataLoader(DS(),batch_size=BS,shuffle=True,num_workers=3,drop_last=True,persistent_workers=True)
# el refinamiento aprende más rápido que lo ya entrenado
nuevos=list(net.ref.parameters());ids={id(p) for p in nuevos};viejos=[p for p in net.parameters() if id(p) not in ids]
opt=torch.optim.AdamW([{'params':viejos,'lr':LR},{'params':nuevos,'lr':LR*5}],weight_decay=1e-4);N=EP*len(dl)
sch=torch.optim.lr_scheduler.OneCycleLR(opt,max_lr=[LR,LR*5],total_steps=N,pct_start=.06)
wce=torch.tensor([1.,1.,1.5])
def perdida(o,y):
    o=F.interpolate(o,size=y.shape[-2:],mode='bilinear',align_corners=False)
    ce=F.cross_entropy(o,y,weight=wce,ignore_index=255)
    # dice de persona y vaca: cuida el área de la silueta
    v=(y!=255).float()[:,None];p=o.softmax(1)*v;t=F.one_hot(y.clamp(max=2),3).permute(0,3,1,2).float()*v
    i=(p*t).sum((0,2,3));d=1-(2*i+1)/(p.sum((0,2,3))+t.sum((0,2,3))+1)
    return ce+.5*d[1:].mean()
t0=time.time();k=0
for ep in range(EP):
    net.train();acc=0
    for x,y in dl:
        l=perdida(net(x),y);opt.zero_grad();l.backward();opt.step();sch.step();k+=1;acc+=l.item()
        if k%50==0:print(f'época {ep+1} paso {k}/{N} pérdida {acc/50:.3f} {time.time()-t0:.0f}s',flush=True);acc=0
    torch.save(net.state_dict(),f'{NOM}_{R}_e{ep+1}.pt')
    net.eval()
    for RR in (256,320):torch.onnx.export(net,torch.randn(1,3,RR,RR),f'm/{NOM}{ep+1}_{RR}.onnx',input_names=['input'],output_names=['logits'],opset_version=17,dynamo=False)
    print('guardada época',ep+1,flush=True)
