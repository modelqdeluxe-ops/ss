# medidas de atrás (sin escala): anchos de la silueta por fila en fracciones del alto (de lo más alto al suelo)
import numpy as np
def atras(M):
    ys=np.where(M.any(1))[0];top,bot=ys.min(),ys.max();h=bot-top
    if h<20:return None
    def ancho(y):
        x=np.where(M[int(y)])[0]
        if not len(x):return 0
        br=np.where(np.diff(x)>1)[0];return max(len(r) for r in np.split(x,br+1))
    perf=[ancho(top+f*h) for f in np.linspace(.05,.7,27)]
    o={'h':float(h),'w_max':float(max(perf[:20])),'w_p90':float(np.percentile(perf[:20],90))}
    for f,w in zip(np.linspace(.05,.7,27)[::2],perf[::2]):o[f'w{int(round(f*100)):02d}']=float(w)
    return o
