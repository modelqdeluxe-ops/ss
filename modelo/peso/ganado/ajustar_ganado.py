# Modelo de peso de ganado (v6): ln peso = c0 + c1 ln alzada + c2 ln fondo de pecho (cm, medidos en la foto de costado).
# Ajuste con horqin_medidas.csv (72 Horqin pesados en báscula; Mendeley Data h2s22wr5py, CC BY 4.0). Las medidas de foto
# salen de medir_bov.py + rasgos_final.py (modelo preciso RF-DETR y puntos AP-10K, como en la app), con la alzada de cinta
# como escala (en la app la escala es la persona de referencia).
#   python3 ajustar_ganado.py            → coeficientes y validación dejando uno fuera
# Validación en otras razas (opcional; sus datos no tienen licencia comercial, solo se usan para medir): ver LEEME.md.
import os,numpy as np,pandas as pd
AQUI=os.path.dirname(os.path.abspath(__file__))
H=pd.read_csv(os.path.join(AQUI,'horqin_medidas.csv')).dropna()
ks=['alzada_cm','fondo_pecho_foto_cm']
X=np.c_[np.ones(len(H)),np.log(H[ks].values)];y=np.log(H.peso_kg.values)
b=np.linalg.lstsq(X,y,rcond=None)[0]
e=[]
for i in range(len(H)):
    m=np.arange(len(H))!=i;bi=np.linalg.lstsq(X[m],y[m],rcond=None)[0];e.append(abs(np.exp(X[i]@bi)/H.peso_kg.values[i]-1))
print('animales',len(H),'coef',[round(float(v),4) for v in b],'error medio dejando uno fuera %.1f%%'%(np.mean(e)*100))
for kk in [['alzada_cm'],['alzada_cm','perimetro_toracico_cm','largo_oblicuo_cm']]:
    Xk=np.c_[np.ones(len(H)),np.log(H[kk].values)];ek=[]
    for i in range(len(H)):
        m=np.arange(len(H))!=i;bi=np.linalg.lstsq(Xk[m],y[m],rcond=None)[0];ek.append(abs(np.exp(Xk[i]@bi)/H.peso_kg.values[i]-1))
    print('referencia',kk,'%.1f%%'%(np.mean(ek)*100))
