# junta las traducciones (modelo/traducciones/*.tsv: clave, inglés, portugués) y escribe los catálogos de la app
import glob,json,os,re,sys
AQUI=os.path.dirname(os.path.abspath(__file__))
en,pt={},{}
malas=[]
for f in sorted(glob.glob(os.path.join(AQUI,'traducciones','*.tsv'))):
    for n,l in enumerate(open(f,encoding='utf8')):
        l=l.rstrip('\n')
        if not l.strip():continue
        p=l.split('\t')
        if len(p)!=3:malas.append((f,n+1,'columnas',l[:60]));continue
        k,e,t=p
        ph=sorted(re.findall(r'\{\d+\}',k))
        for x,nm in ((e,'en'),(t,'pt')):
            if sorted(re.findall(r'\{\d+\}',x))!=ph:malas.append((f,n+1,nm+' marcas',k[:60]))
        en[k]=e;pt[k]=t
for m in malas:print('REVISAR',*m)
A=os.path.join(AQUI,'..','app','assets','')
for nom,d in (('en',en),('pt',pt)):
    open(A+'i18n_'+nom+'.js','w',encoding='utf8').write('/* Catálogo '+nom+' de Rumentis: frase en español → traducción. Lo arma modelo/armar_i18n.py. */\nwindow.I18N_DIC=window.I18N_DIC||{};I18N_DIC.'+nom+'='+json.dumps(d,ensure_ascii=False,separators=(',',':'))+';\n')
print('frases',len(en))
