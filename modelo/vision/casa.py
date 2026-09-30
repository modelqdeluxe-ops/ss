# escenas de casa: personas grandes junto a muebles (cama 65, sillón 63, silla 62, mesa 67, tv 72, refrigerador 82, planta 64)
import json,random,os,sys,urllib.request,io,numpy as np
from concurrent.futures import ThreadPoolExecutor
MUEBLES={62,63,64,65,67,72,82}
parte=sys.argv[1]
d=json.load(open(f'inst_{parte}.json'));im={i['id']:i for i in d['images']}
from collections import defaultdict
A=defaultdict(list)
for a in d['annotations']:A[a['image_id']].append(a)
sel=[]
for iid,an in A.items():
    W,H=im[iid]['width'],im[iid]['height'];cats={a['category_id'] for a in an}
    if not cats&MUEBLES:continue
    ps=[a for a in an if a['category_id']==1 and not a['iscrowd']]
    if not ps:continue
    p=max(ps,key=lambda a:a['area']);x,y,w,h=p['bbox']
    if parte=='val':
        # prueba: una persona grande, de cuerpo casi completo, sin cortar
        if sum(1 for a in ps if a['area']>.35*p['area'])>1 or h/H<.45 or y<3 or y+h>H-3 or x<3 or x+w>W-3:continue
        sel.append({'id':iid,'cls':1,'seg':p['segmentation'],'bbox':p['bbox'],'W':W,'H':H})
    else:
        if p['area']>=.04*W*H:sel.append(iid)
if parte=='val':
    os.makedirs('ev',exist_ok=True)
    for o in sel:
        f=f"ev/{o['id']}.jpg"
        if not os.path.exists(f):urllib.request.urlretrieve(f"https://s3.amazonaws.com/images.cocodataset.org/val2017/{o['id']:012d}.jpg",f)
    json.dump(sel,open('ev/casa.json','w'));print('prueba casa',len(sel))
else:
    ya={int(os.path.basename(f)[:-4]) for f in os.listdir('td/msk')}
    nuevas=[i for i in sel if i not in ya];random.Random(5).shuffle(nuevas);nuevas=nuevas[:int(os.environ.get('N','4000'))]
    print('candidatas',len(sel),'nuevas',len(nuevas))
    json.dump(nuevas,open('td/casa.json','w'))
