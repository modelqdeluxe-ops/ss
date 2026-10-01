# rasgos de costado (en píxeles) desde silueta M y puntos P
import numpy as np
def ok(P,i,c=.3):return P[i][2]>=c
def media(P,ids):
    v=[P[i] for i in ids if ok(P,i)];return np.mean(v,0) if v else None
def primer_tramo(col):
    ys=np.where(col)[0]
    if not len(ys):return None
    y0=ys[0];br=np.where(np.diff(ys)>1)[0];y1=ys[br[0]] if len(br) else ys[-1];return y0,y1+1
def costado(M,P):
    H,W=M.shape;sh=media(P,[5,8]);el=media(P,[6,9]);tr=P[4] if ok(P,4) else None;pf=media(P,[7,10]);pt=media(P,[13,16])
    if sh is None or tr is None or el is None or pf is None:return None
    s=1 if tr[0]>sh[0] else -1;L=abs(tr[0]-sh[0])
    suelo=np.median([P[i][1] for i in (7,10,13,16) if ok(P,i)])
    # cruz: lo más alto de la silueta entre el hombro y 15 % del largo hacia atrás
    xs=[int(sh[0]+s*f*L) for f in np.linspace(-.05,.15,9)];tops=[np.where(M[:,x])[0][0] for x in xs if 0<=x<W and M[:,x].any()]
    cruz=min(tops) if tops else None
    # fondo del pecho: columnas detrás del codo (5–20 % del largo); el primer tramo de cada columna, si no baja hasta
    # la pata (si baja, es la pierna)
    lim=el[1]+.35*(pf[1]-el[1]);cds=[];bajos=[]
    for f in np.linspace(.05,.25,11):
        x=int(el[0]+s*f*L)
        if not 0<=x<W:continue
        t=primer_tramo(M[:,x])
        if t and t[1]<lim:cds.append(t[1]-t[0]);bajos.append(t[1])
    # vientre: igual a lo largo de todo el tronco
    vien=[]
    for f in np.linspace(.15,.85,29):
        x=int(sh[0]+s*f*L);t=primer_tramo(M[:,x]) if 0<=x<W else None
        if t and t[1]<lim:vien.append(t[1])
    belly=np.median(vien) if vien else None
    # área del tronco de costado (sin patas, cabeza ni cuello): columnas del hombro (−8 % del largo) al final del anca
    xa=int(sh[0]-s*.08*L);cols=range(min(xa,int(tr[0]+s*.15*L)),max(xa,int(tr[0]+s*.15*L)))
    area=0
    for x in cols:
        if not 0<=x<W:continue
        t=primer_tramo(M[:,x])
        if t:area+=max(0,min(t[1],belly if belly else t[1])-t[0])
    # largo de la silueta del tronco a la altura del lomo medio (de la punta del pecho a la punta de la nalga)
    y=int((cruz+(belly or el[1]))/2) if cruz is not None else int(sh[1]);fila=np.where(M[y])[0]
    return dict(cruz_px=(suelo-cruz) if cruz is not None else np.nan,largo_k=L,fondo=np.median(cds) if cds else np.nan,
                vientre=(suelo-belly) if belly else np.nan,area=area,suelo=suelo,cruz=cruz,s=s,sh=sh,el=el,tr=tr,belly=belly)
