# Saca un archivo del zip de anotaciones de COCO sin bajar el zip entero (pide solo los bytes que hacen falta).
#   python3 bajar_anotaciones.py val    → inst_val.json      python3 bajar_anotaciones.py train  → inst_train.json
import urllib.request,struct,zlib,sys
U='http://images.cocodataset.org/annotations/annotations_trainval2017.zip'
parte=sys.argv[1] if len(sys.argv)>1 else 'val'
def rng(a,b):return urllib.request.urlopen(urllib.request.Request(U,headers={'Range':f'bytes={a}-{b}'})).read()
n=int(urllib.request.urlopen(urllib.request.Request(U,method='HEAD')).headers['Content-Length'])
t=rng(n-65536,n-1);i=t.rfind(b'PK\x05\x06');cd_size,cd_off=struct.unpack('<II',t[i+12:i+20])
cd=rng(cd_off,cd_off+cd_size-1);p=0
while p<len(cd):
    (_,_,_,_,meth,_,_,crc,csz,usz,fl,el,cl,_,_,_,off)=struct.unpack('<IHHHHHHIIIHHHHHII',cd[p:p+46]);name=cd[p+46:p+46+fl].decode()
    if name.endswith(f'instances_{parte}2017.json'):
        lh=rng(off,off+29);fl2,el2=struct.unpack('<HH',lh[26:30]);d=rng(off+30+fl2+el2,off+30+fl2+el2+csz-1)
        open(f'inst_{parte}.json','wb').write(zlib.decompress(d,-15));print('listo',usz);break
    p+=46+fl+el+cl
