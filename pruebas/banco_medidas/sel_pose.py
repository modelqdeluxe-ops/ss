import json,collections,math
R=[]
for parte in ['train','val']:
    d=json.load(open(f'kp_{parte}.json'));im={i['id']:i for i in d['images']};por=collections.defaultdict(list)
    for a in d['annotations']:por[a['image_id']].append(a)
    for iid,As in por.items():
        I=im[iid];W,H=I['width'],I['height']
        for a in As:
            if a['iscrowd'] or a['num_keypoints']<17:continue
            k=a['keypoints'];P=[(k[3*i],k[3*i+1],k[3*i+2]) for i in range(17)]
            if sum(p[2]==0 for p in P)>2 or any(P[i][2]==0 for i in (5,6,11,12,15,16)):continue
            x,y,w,h=a['bbox']
            if h<.45*H or h<250 or y<4 or y+h>H-4 or x<2 or x+w>W-2:continue
            otros=[b for b in As if b is not a and b['area']>.05*a['area']]
            def ov(b):
                bx,by,bw,bh=b['bbox'];ix=max(0,min(x+w,bx+bw)-max(x,bx));iy=max(0,min(y+h,by+bh)-max(y,by));return ix*iy/(w*h)
            if any(ov(b)>.05 for b in otros):continue
            sh=[(P[5][0]+P[6][0])/2,(P[5][1]+P[6][1])/2];hp=[(P[11][0]+P[12][0])/2,(P[11][1]+P[12][1])/2]
            an=[(P[15][0]+P[16][0])/2,(P[15][1]+P[16][1])/2];kn=[(P[13][1]+P[14][1])/2]
            T=hp[1]-sh[1]
            if T<=0 or not(sh[1]<hp[1]<kn[0]<an[1]):continue
            if abs(an[0]-sh[0])>.25*(an[1]-sh[1]):continue          # derecho
            if min(P[9][1],P[10][1])<hp[1]-.3*T:continue              # muñecas abajo (no brazos arriba)
            if abs(P[15][0]-P[16][0])>.35*(an[1]-sh[1]):continue      # piernas no muy abiertas
            r=abs(P[5][0]-P[6][0])/T
            tipo='F' if r>.6 else 'S' if r<.3 else None
            if tipo:R.append({'parte':parte,'id':iid,'url':I['coco_url'],'W':W,'H':H,'bbox':a['bbox'],'tipo':tipo,'r':round(r,3),'kp':k})
print(len(R),collections.Counter(r['tipo'] for r in R))
json.dump(R,open('sel_pose.json','w'))
