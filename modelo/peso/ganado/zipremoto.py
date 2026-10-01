# lee el índice (directorio central) de un zip remoto con pedidos por rangos y saca archivos sueltos
import subprocess,struct,json,zlib,sys,os
def url():
    r=subprocess.run(['curl','-sS','-m','60','-o','/dev/null','-w','%{redirect_url}','https://www.kaggle.com/api/v1/datasets/download/sadhliroomyprime/cattle-weight-detection-model-dataset-12k'],capture_output=True,text=True);return r.stdout.strip()
def rng(u,a,b):return subprocess.run(['curl','-sS','-m','600','--fail','-r',f'{a}-{b}',u],capture_output=True,check=True).stdout
def tam(u):
    r=subprocess.run(['curl','-sS','-m','60','-r','0-0','-D','-','-o','/dev/null',u],capture_output=True,text=True).stdout
    for l in r.splitlines():
        if l.lower().startswith('content-range'):return int(l.split('/')[-1])
def indice(u):
    n=tam(u);t=rng(u,n-200000,n-1);i=t.rfind(b'PK\x06\x06')   # zip64
    if i>=0:
        cd_size,cd_off=struct.unpack('<QQ',t[i+40:i+56])
    else:
        i=t.rfind(b'PK\x05\x06');cd_size,cd_off=struct.unpack('<II',t[i+12:i+20])
    cd=rng(u,cd_off,cd_off+cd_size-1);p=0;E=[]
    while p<len(cd):
        (_,_,_,_,meth,_,_,crc,csz,usz,fl,el,cl,_,_,_,off)=struct.unpack('<IHHHHHHIIIHHHHHII',cd[p:p+46]);name=cd[p+46:p+46+fl].decode('utf8','replace')
        ex=cd[p+46+fl:p+46+fl+el];q=0
        while q<len(ex)-4:
            hid,hl=struct.unpack('<HH',ex[q:q+4])
            if hid==1:
                vals=list(struct.unpack('<'+'Q'*(hl//8),ex[q+4:q+4+hl]));
                if usz==0xFFFFFFFF:usz=vals.pop(0)
                if csz==0xFFFFFFFF:csz=vals.pop(0)
                if off==0xFFFFFFFF:off=vals.pop(0)
            q+=4+hl
        E.append((name,meth,csz,usz,off));p+=46+fl+el+cl
    return E
def sacar(u,e,dst):
    name,meth,csz,usz,off=e;lh=rng(u,off,off+29);fl,el=struct.unpack('<HH',lh[26:30]);d=rng(u,off+30+fl+el,off+30+fl+el+csz-1)
    b=zlib.decompress(d,-15) if meth==8 else d;os.makedirs(os.path.dirname(dst),exist_ok=True);open(dst,'wb').write(b)
if __name__=='__main__':
    u=url();E=indice(u);json.dump(E,open('kaggle_indice.json','w'));json.dump(u,open('kaggle_url.json','w'));print(len(E),'archivos',sum(e[3] for e in E)/1e9,'GB')
