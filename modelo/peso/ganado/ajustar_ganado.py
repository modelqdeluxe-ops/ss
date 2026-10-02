# Modelo de peso de ganado (v7): ln peso = a(tipo) [+ joven] + β ln alzada + γ ln(fondo de pecho / (0.52 · alzada))
# (cm; la razón fondo/alzada se limita a 0.42–0.65). Mismas definiciones que la app (pesocam.js, medidasGanado).
#   python3 ajustar_ganado.py            → β, γ, puntos de partida por tipo y la validación
# Datos (todos con licencia CC BY 4.0, ver LEEME.md):
#   horqin_medidas.csv           71 animales con fotos (fondo de pecho medido en la foto) y báscula — Bai, Mendeley Data
#   tablas/bororo_niger.csv      292 cebú Bororo adultos, cinta y báscula — Zenodo 10.5281/zenodo.22911607
#   tablas/curraleiro_pe_duro.csv 1,023 criollos Curraleiro Pé-Duro (terneros a toros), cinta y báscula — Zenodo 8364889
#   tablas/indonesia.csv         95 bovinos de Indonesia con fondo de pecho, cinta y báscula — Kaggle (jameswisnuaryatama)
#   tablas/simmental_lidar.csv   45 terneros Simmental, alzada de LiDAR y báscula — Zenodo 11277007
# Validación opcional con bases sin licencia comercial (solo para medir): VALIDAR=carpeta con CowDatabase/Measurements.xlsx
import os,numpy as np,pandas as pd
AQUI=os.path.dirname(os.path.abspath(__file__));T=lambda f:pd.read_csv(os.path.join(AQUI,f))
R0,LO,HI=.52,.42,.65
rng=np.random.default_rng(0)
def base(nom,W,WH,CD=None):
    d=pd.DataFrame({'W':np.asarray(W,float),'WH':np.asarray(WH,float)});d['r']=0.0 if CD is None else np.log(np.clip(np.asarray(CD,float)/d.WH,LO,HI)/R0)
    d['hasCD']=CD is not None;d=d[(d.W>0)&(d.WH>0)].dropna(subset=['r']);d['base']=nom;return d
h=T('horqin_medidas.csv').dropna();bo=T('tablas/bororo_niger.csv');cu=T('tablas/curraleiro_pe_duro.csv').dropna(subset=['alzada_cm'])
ind=T('tablas/indonesia.csv');si=T('tablas/simmental_lidar.csv')
cu['categoria']=cu.categoria.str.strip();cuA=cu[cu.categoria.isin(['Cow','Bull'])];cuJ=cu[cu.categoria=='Posweaned']
B={'Horqin':base('Horqin',h.peso_kg,h.alzada_cm,h.fondo_pecho_foto_cm),
   'Bororo':base('Bororo',bo.peso_kg,bo.alzada_cm),
   'Indonesia adultos':base('Indonesia adultos',ind[ind.edad=='Adult'].peso_kg,ind[ind.edad=='Adult'].alzada_cm,ind[ind.edad=='Adult'].fondo_pecho_cm),
   'Curraleiro adultos':base('Curraleiro adultos',cuA.peso_kg,cuA.alzada_cm)}
J={'Indonesia jóvenes':base('Indonesia jóvenes',ind[ind.edad=='Young'].peso_kg,ind[ind.edad=='Young'].alzada_cm,ind[ind.edad=='Young'].fondo_pecho_cm),
   'Curraleiro destetados':base('Curraleiro destetados',cuJ.peso_kg,cuJ.alzada_cm),
   'Simmental terneros (LiDAR)':base('Simmental terneros (LiDAR)',si.peso_kg,si.alzada_cm)}
# 1) β: en la app la alzada sale de la foto (escala de la persona de referencia y silueta: ~4 % de ruido). Con ese ruido
#    simulado, error dejando uno fuera con el punto de partida propio de cada base (= un lote calibrado), promedio de bases
def loo(y,q):return np.mean([abs(np.exp(q[i]+np.median(np.delete(y-q,i))-y[i])-1) for i in range(len(y))])
print('β   error medio dentro de cada base (alzada con 4 % de ruido de foto)')
grilla={}
for beta in (1.8,2.0,2.2,2.5,2.8):
    e=[]
    for d in list(B.values())+list(J.values()):
        y=np.log(d.W.values);e.append(np.mean([loo(y,beta*(np.log(d.WH.values)+rng.normal(0,.04,len(d)))+.8*d.r.values) for _ in range(5)]))
    grilla[beta]=np.mean(e);print(f'{beta:.1f}  {grilla[beta]*100:.1f} %   ({" · ".join(f"{x*100:.1f}" for x in e)})')
BETA=float(os.environ.get('BETA','2.0'))   # 1.8–2.0 es lo mejor dentro de cada base con el ruido de la foto; entre edades (terneros a
           # toros) el peso crece como alzada^2.8–3: 2.0 iguala a v6 en Horqin y deja la corrección de jóvenes a JOVEN y a la calibración
# 2) γ (fondo de pecho): con β fijo, en las bases que tienen fondo de pecho (Horqin de foto, Indonesia de cinta)
F=pd.concat([B['Horqin'],B['Indonesia adultos'],J['Indonesia jóvenes']]);y=np.log(F.W.values)-BETA*np.log(F.WH.values)
X=np.c_[[(F.base==b).values for b in F.base.unique()]].T.astype(float);X=np.c_[X,F.r.values];GAM=round(float(np.linalg.lstsq(X,y,rcond=None)[0][-1]),2)
# 3) puntos de partida (a) de cada base
a={k:float(np.median(np.log(d.W.values)-BETA*np.log(d.WH.values)-GAM*d.r.values)) for k,d in {**B,**J}.items()}
A={'euro':a['Horqin'],'cebu':float(np.mean([a['Bororo'],a['Indonesia adultos'],a['Curraleiro adultos']]))};A['cruce']=(A['euro']+A['cebu'])/2;A['leche']=A['euro']
JOVEN=round(float(np.median([a['Indonesia jóvenes']-a['Indonesia adultos'],a['Curraleiro destetados']-a['Curraleiro adultos'],a['Simmental terneros (LiDAR)']-a['Horqin']])),2)
print(f'\nβ = {BETA}  γ = {GAM}  joven = {JOVEN}');print('a por base:',{k:round(v,3) for k,v in a.items()})
print('a por tipo:',{k:round(v,3) for k,v in A.items()},'(lechero: sin base abierta con báscula; usa el de europeo)')
# 4) validación
c6=[-4.3002,1.811,0.4362]
def v6(d):return c6[0]+c6[1]*np.log(d.WH.values)+c6[2]*np.log(np.exp(d.r.values)*R0*d.WH.values)
def err(y,p):return np.mean(np.abs(np.exp(p-y)-1))*100
def cal5(y,p):
    e=[]
    for _ in range(300):
        i=rng.choice(len(y),5,replace=False);m=np.ones(len(y),bool);m[i]=False;e.append(err(y[m],p[m]+np.median(y[i]-p[i])))
    return np.median(e)
tipo={'Horqin':'euro','Bororo':'cebu','Indonesia adultos':'cebu','Curraleiro adultos':'cebu','Indonesia jóvenes':'cebu','Curraleiro destetados':'cebu','Simmental terneros (LiDAR)':'euro'}
print(f'\n{"base":28s}   n | v6 sin calibrar | v7 sin calibrar* | v7 calibrado con 5 | v7 dentro de la base')
def fila(k,d,ag,jov=0):
    y=np.log(d.W.values);q=BETA*np.log(d.WH.values)+GAM*d.r.values
    print(f'{k:28s} {len(d):4d} | {err(y,v6(d)):13.1f} % | {err(y,q+ag+jov):14.1f} % | {cal5(y,q):16.1f} % | {loo(y,q)*100:8.1f} %')
for k,d in {**B,**J}.items():
    g=tipo[k];otras=[a[x] for x in B if tipo[x]==g and x!=k and x!='Horqin'] if g=='cebu' else [A['euro']]
    ag=np.mean(otras) if otras else A[g];fila(k,d,ag,JOVEN if k in J else 0)
print('* con el punto de partida de su tipo calculado SIN esa base (cebú) y con la corrección de jóvenes donde corresponde')
# 5) promedios publicados por raza (tablas/promedios_razas.csv): el punto de partida que da cada uno con su etapa
ET={'adulto':0,'crec':-.2,'ternero':-.4};AP={'euro':-3.45,'cruce':-3.66,'cebu':-3.876,'leche':-3.53}
pr=T('tablas/promedios_razas.csv');pr['a']=np.log(pr.peso_kg)-BETA*np.log(pr.alzada_cm)-pr.etapa.map(ET)
print('\npromedios por raza (a = ln peso − 2 ln alzada − etapa; el de la app por tipo)')
for _,x in pr.iterrows():print(f'   {x.raza:34s} {x.tipo:6s} a {x.a:6.3f}  app {AP[x.tipo]:6.3f}  diferencia {np.exp(x.a-AP[x.tipo])-1:+.0%}')
# 6) con cinta (para quien no tiene báscula): ln peso = a + tipo (europeo) + etapa + c1 ln alzada + c2 ln perímetro
def cin(nom,tipo,et,d):
    hg=d['perimetro_cm'] if 'perimetro_cm' in d else d['perimetro_toracico_cm']
    x=pd.DataFrame({'W':d.peso_kg.values,'WH':d.alzada_cm.values,'HG':hg.values}).astype(float).dropna();x=x[(x.W>0)&(x.WH>0)&(x.HG>0)];x['b']=nom;x['t']=tipo;x['e']=et;return x
cu2=cu.copy();cu2['categoria']=cu2.categoria.str.strip()
Q=pd.concat([cin('Horqin','euro','adulto',h),cin('Bororo','cebu','adulto',bo),cin('Curraleiro','cebu','adulto',cu2[cu2.categoria.isin(['Cow','Bull'])]),
  cin('Curraleiro destetados','cebu','crec',cu2[cu2.categoria=='Posweaned']),cin('Curraleiro terneros','cebu','ternero',cu2[cu2.categoria=='Calves']),
  cin('Indonesia','cebu','adulto',ind[ind.edad=='Adult']),cin('Indonesia jóvenes','cebu','crec',ind[ind.edad=='Young'])])
XQ=lambda d:np.c_[np.ones(len(d)),(d.t=='euro').values,(d.e=='crec').values,(d.e=='ternero').values,np.log(d.WH),np.log(d.HG)]
sq=np.sqrt(1/Q.groupby('b').W.transform('size').values);cq=np.linalg.lstsq(XQ(Q)*sq[:,None],np.log(Q.W.values)*sq,rcond=None)[0]
print('\ncon cinta [a, europeo, crecimiento, ternero, ln alzada, ln perímetro]:',np.round(cq,4))
rq=np.log(Q.W.values)-XQ(Q)@cq
for bb in Q.b.unique():mm=(Q.b==bb).values;print(f'   {bb:24s} {np.mean(np.abs(np.exp(rq[mm])-1))*100:5.1f} %')
V=os.environ.get('VALIDAR')
if V:
    x=pd.read_excel(os.path.join(V,'CowDatabase/Measurements.xlsx'))
    fila('Hereford (cinta, validación)',base('Hereford',x['live weithg'],x['withers height'],x['chest depth']),A['euro'])
    H=pd.DataFrame({'W':x['live weithg'],'WH':x['withers height'],'HG':x['heart girth'],'t':'euro','e':'adulto'});rh=np.log(H.W)-XQ(H)@cq
    print(f'con cinta, Hereford (no entró): sesgo {np.exp(np.median(rh)):.2f}, error {np.mean(np.abs(np.exp(rh)-1))*100:.1f} %')
