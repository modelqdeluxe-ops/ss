# fotos de COCO train2017 para entrenar la silueta: 0 fondo, 1 persona, 2 vaca, 255 sin evaluar (multitudes)
import json,random,os,io,urllib.request,numpy as np
from concurrent.futures import ThreadPoolExecutor
from PIL import Image
from pycocotools import mask as MU
d=json.load(open('inst_train.json'));im={i['id']:i for i in d['images']}
from collections import defaultdict
A=defaultdict(list)
for a in d['annotations']:
    if a['category_id'] in (1,21):A[a['image_id']].append(a)
del d
sel=[(i,'c') for i in json.load(open('td/casa.json'))]
print('casa',len(sel))
os.makedirs('td/img',exist_ok=True);os.makedirs('td/msk',exist_ok=True)
def uno(t):
    iid,k=t;fi=f'td/img/{iid}.jpg';fm=f'td/msk/{iid}.png'
    if os.path.exists(fm):return 1
    try:
        b=urllib.request.urlopen(f'https://s3.amazonaws.com/images.cocodataset.org/train2017/{iid:012d}.jpg',timeout=60).read()
        img=Image.open(io.BytesIO(b)).convert('RGB');W,H=img.size;M=np.zeros((H,W),np.uint8)
        # primero lo que se ignora, después persona y vaca encima
        for a in A[iid]:
            if a['category_id'] not in (1,21):continue
            s=a['segmentation'];r=MU.frPyObjects(s,H,W) if isinstance(s,list) else (MU.frPyObjects(s,H,W) if isinstance(s['counts'],list) else s)
            m=MU.decode(r);m=m.max(2) if m.ndim==3 else m
            if a['iscrowd']:M[(m>0)&(M==0)]=255
        for cid,v in ((1,1),(21,2)):
            for a in A[iid]:
                if a['category_id']!=cid or a['iscrowd']:continue
                r=MU.frPyObjects(a['segmentation'],H,W);m=MU.decode(r);m=m.max(2) if m.ndim==3 else m;M[m>0]=v
        k2=min(1,480/max(W,H));
        if k2<1:img=img.resize((round(W*k2),round(H*k2)),Image.BILINEAR);Mi=Image.fromarray(M).resize(img.size,Image.NEAREST)
        else:Mi=Image.fromarray(M)
        img.save(fi,quality=92);Mi.save(fm);return 1
    except Exception as e:return 0
with ThreadPoolExecutor(24) as ex:ok=sum(ex.map(uno,sel))
L=json.load(open('td/lista.json'));ya={x[0] for x in L};L+=[[i,k] for i,k in sel if i not in ya];json.dump(L,open('td/lista.json','w'));print('listas',ok,'total',len(L))
