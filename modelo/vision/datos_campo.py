# fotos de campo de Kaggle (BMGF, CC BY 4.0) al formato de entrenamiento: td/img/<id>.jpg y td/msk/<id>.png
# (0 fondo, 1 persona, 2 vaca). La vaca (y la calcomanía) de la máscara a mano; las personas, del modelo preciso.
import glob,json,re,os,numpy as np,onnxruntime as ort,torch,torch.nn.functional as F
from PIL import Image
o=ort.SessionOptions();o.intra_op_num_threads=4;s=ort.InferenceSession(os.path.join(os.path.dirname(os.path.abspath(__file__)),'seg.onnx'),o)
MEAN=np.array([.485,.456,.406],np.float32);STD=np.array([.229,.224,.225],np.float32)
fuera=set(json.load(open('td/fuera_kaggle.json')))
def grupo(f):
    n=f.split('/')[-1];m=re.match(r'(\d+)_(?:(b4-\d)_)?[sr]_(\d+)_([MF])',n);return ('B4' if '/B4/' in f else 'B3')+(m.group(2) or '')+'_'+m.group(3)+m.group(4) if m else n
fs=sorted(glob.glob('kaggle/px/B3/images/*.jpg')+glob.glob('kaggle/px/B4/Side/images/*.jpg')+glob.glob('kaggle/px/B4/Rear/images/*.jpg'))
L=json.load(open('td/lista.json'));ya={i for i,k in L};nuevos=[]
for n,f in enumerate(fs):
    if grupo(f) in fuera:continue
    iid='k'+('4r' if '/Rear/' in f else '4s' if '/B4/' in f else '3s')+'_'+f.split('/')[-1].rsplit('.',1)[0]
    if iid in ya or os.path.exists(f'td/msk/{iid}.png'):nuevos.append(iid);continue
    a=f.replace('/images/','/annotations/').rsplit('.',1)[0]+'.jpg___fuse.png'
    if not os.path.exists(a):continue
    img=Image.open(f).convert('RGB');k=640/max(img.size);img=img.resize((round(img.width*k),round(img.height*k)),Image.BILINEAR)
    A=np.array(Image.open(a).convert('RGB').resize(img.size,Image.NEAREST));vaca=((A[...,0]==255)&(A[...,1]==30))|((A[...,0]==0)&(A[...,1]==117)&(A[...,2]==255))
    M=np.where(vaca,2,0).astype(np.uint8)
    x=np.asarray(img.resize((312,312),Image.BILINEAR),np.float32)/255
    d,l,m=s.run(['dets','labels','masks'],{'input':((x-MEAN)/STD).transpose(2,0,1)[None].astype(np.float32)})
    sc=1/(1+np.exp(-l[0][:,1]))
    for q in np.where(sc>.5)[0]:
        up=F.interpolate(torch.from_numpy(m[0][q][None,None].astype(np.float32)),size=(img.height,img.width),mode='bilinear',align_corners=False)[0,0].numpy()>0
        M[up&~vaca]=1
    img.save(f'td/img/{iid}.jpg',quality=90);Image.fromarray(M).save(f'td/msk/{iid}.png');nuevos.append(iid)
    if n%250==0:print(n,len(nuevos),flush=True)
L=[x for x in L if not str(x[0]).startswith('k')]+[[i,'g'] for i in nuevos];json.dump(L,open('td/lista.json','w'));print('listo',len(nuevos),len(L))
