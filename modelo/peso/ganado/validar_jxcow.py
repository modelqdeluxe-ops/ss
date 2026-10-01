# Validación del modelo de peso con JXcow (Kaggle dcaoren/jxcow-datasets, CC BY-NC-SA 4.0: solo para medir, no para
# ajustar). Animales de engorde en báscula, de costado detrás de barandas; cada cuadro trae la silueta recortada a
# 224×224 (rgb_mask) y la nube de puntos en metros alineada pixel a pixel (pointcloud: X Y Z U V).
#   JX=<carpeta con JX-preprocess/ y Weight-table.xlsx> python3 validar_jxcow.py
# (bajar solo unos cuadros por animal con zipremoto.py: JX-preprocess/<id>/{pointcloud,rgb_mask}/frame_*.{txt,png})
# Medidas como la app (rasgos_final.final: alzada y fondo de pecho con los puntos AP-10K); la escala, del alto en metros
# de la nube contra los píxeles (pendiente de Y contra V).
import os,sys,glob,numpy as np,pandas as pd
from PIL import Image
from medir_bov import pose,caja
from rasgos_final import final
D=os.environ.get('JX','jx');out=[]
for a in sorted(os.listdir(os.path.join(D,'JX-preprocess'))):
    for pc in sorted(glob.glob(os.path.join(D,'JX-preprocess',a,'pointcloud','*.txt'))):
        fr=os.path.basename(pc)[:-4];rm=os.path.join(D,'JX-preprocess',a,'rgb_mask',fr+'.png')
        if not os.path.exists(rm):continue
        im=np.array(Image.open(rm).convert('RGB'));M0=im.sum(-1)>0
        X=pd.read_csv(pc,sep=' ');X=X[(X.X!=0)|(X.Y!=0)|(X.Z!=0)]
        if len(X)<500:continue
        sv=abs(np.polyfit(X.V,X.Y,1)[0])
        S=4;big=Image.fromarray(im).resize((224*S,224*S),Image.BILINEAR)
        M=np.array(Image.fromarray(M0.astype(np.uint8)*255).resize((224*S,224*S),Image.NEAREST))>127
        try:f=final(M,pose(big,caja(M)))
        except Exception:continue
        if f:out.append(dict(a=a,WH=f['WH']*sv*100/S,CD=f['CD']*sv*100/S))
w=pd.read_excel(os.path.join(D,'Weight-table.xlsx')).rename(columns={'Number':'a','weight':'W'})
T=pd.DataFrame(out).groupby('a').median().reset_index().merge(w,on='a');y=np.log(T.W.values)
q=-3.392+2.0*np.log(T.WH)+0.54*np.log(np.clip(T.CD/T.WH,.42,.65)/.52)   # v7, europeo de carne
print(f'{len(T)} animales: v7 sin calibrar {np.mean(np.abs(np.exp(q-y)-1))*100:.1f} %, sesgo {np.exp(np.median(y-q)):.2f}; '
      f'promedio del lote {np.mean(np.abs(np.exp(np.median(y)-y)-1))*100:.1f} %')
