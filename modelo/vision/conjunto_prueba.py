# conjunto de evaluación: fotos de COCO val2017 con una persona o una vaca grande y completa (silueta real a mano)
import json,random,os,urllib.request
d=json.load(open('inst_val.json'));im={i['id']:i for i in d['images']}
from collections import defaultdict
A=defaultdict(list)
for a in d['annotations']:A[a['image_id']].append(a)
out=[]
for cls,n in ((1,160),(21,80)):
    C=[]
    for iid,anns in A.items():
        cs=[a for a in anns if a['category_id']==cls and not a['iscrowd']]
        if not cs:continue
        a=max(cs,key=lambda z:z['area']);W,H=im[iid]['width'],im[iid]['height'];x,y,w,h=a['bbox']
        # grande, sin cortar, y sin otro de la misma clase casi igual de grande
        if a['area']<(.06 if cls==1 else .025)*W*H or x<3 or y<3 or x+w>W-3 or y+h>H-3:continue
        if sum(1 for z in cs if z['area']>(.35 if cls==1 else .5)*a['area'])>1:continue
        if cls==1 and h/H<.45:continue
        C.append((iid,a))
    random.Random(7).shuffle(C);C=C[:n]
    for iid,a in C:out.append({'id':iid,'cls':cls,'seg':a['segmentation'],'bbox':a['bbox'],'W':im[iid]['width'],'H':im[iid]['height']})
os.makedirs('ev',exist_ok=True)
for o in out:
    f=f"ev/{o['id']}.jpg"
    if not os.path.exists(f):urllib.request.urlretrieve(f"https://s3.amazonaws.com/images.cocodataset.org/val2017/{o['id']:012d}.jpg",f)
json.dump(out,open('ev/set.json','w'));print(len(out),sum(o['cls']==1 for o in out),sum(o['cls']==21 for o in out))
