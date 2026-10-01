# Las medidas de costado que usará la app (mismas definiciones que se pasarán a JS). M: silueta, P: puntos AP-10K.
import numpy as np
from rasgos_bov import ok,media,primer_tramo
def final(M,P):
    H,W=M.shape;sh=media(P,[5,8]);el=media(P,[6,9]);tr=P[4] if ok(P,4) else None;pf=media(P,[7,10])
    if sh is None or tr is None or el is None or pf is None:return None
    s=1 if tr[0]>sh[0] else -1;L=abs(tr[0]-sh[0])
    if L<20:return None
    suelo=float(np.where(M.any(1))[0].max());lim=el[1]+.35*(pf[1]-el[1])
    def tramo(x):
        x=int(round(x))
        if not 0<=x<W:return None
        t=primer_tramo(M[:,x]);return t if t and t[1]<lim else None
    tops=[primer_tramo(M[:,int(sh[0]+s*f*L)]) for f in np.linspace(-.05,.15,9) if 0<=int(sh[0]+s*f*L)<W]
    tops=[t[0] for t in tops if t]
    if not tops:return None
    cruz=min(tops)
    cd=[t[1]-t[0] for f in np.linspace(.10,.30,21) for t in [tramo(el[0]+s*f*L)] if t]
    fm=[t[1]-t[0] for f in np.linspace(-.05,1.0,43) for t in [tramo(sh[0]+s*f*L)] if t]
    if len(cd)<3 or len(fm)<10:return None
    return dict(WH=suelo-cruz,CD=float(min(cd)),CDmed=float(np.median(cd)),L=float(L),FM=float(np.mean(fm)),A=float(np.mean(fm)*L),
                lat=float(np.mean([abs(P[a][0]-P[b][0]) for a,b in ((5,8),(11,14)) if ok(P,a) and ok(P,b)] or [np.nan])/L))
