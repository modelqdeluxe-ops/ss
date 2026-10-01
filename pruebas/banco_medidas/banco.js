// Corre la medición de la app (modelo preciso + puntos de fotos) sobre fotos reales y guarda silueta y puntos.
const {chromium}=require('playwright');const fs=require('fs');
const L=fs.readFileSync('/home/user/ss/app/assets/camvivo.js','utf8').split('\n');
const de=(a,b)=>L.slice(a-1,b).join('\n');
const FUN=[de(29,29),de(35,39),de(43,49),de(52,60),de(143,144)].join('\n');
const CFG0=fs.readFileSync('/home/user/ss/app/assets/config.js','utf8').replace("prueba:false,beta:false,","prueba:false,beta:true,");
(async()=>{const b=await chromium.launch({executablePath:'/opt/pw-browsers/chromium-1194/chrome-linux/chrome'});
 const p=await (await b.newContext()).newPage();p.on('pageerror',e=>console.log('err',e.message));
 await p.route('**/config.js',r=>r.fulfill({status:200,contentType:'application/javascript',body:CFG0}));
 await p.addInitScript(()=>{window.VISION_BASE='http://127.0.0.1:8112/';});
 await p.goto('http://127.0.0.1:8111/index.html#hoy');await p.waitForTimeout(1500);
 await p.addScriptTag({content:FUN+'\nwindow.__f={aCuadro,medir,zonaDe,recortePose};'});
 const lista=JSON.parse(fs.readFileSync(process.env.LISTA||'banco/lista.json'));
 const out=[];const N=+process.env.N||lista.length;
 for(let i=0;i<Math.min(N,lista.length);i++){const o=lista[i];
  const r=await p.evaluate(async o=>{const {aCuadro,medir,zonaDe,recortePose}=window.__f;
    window.__pre=window.__pre||await Vision.motor('preciso');window.__pose=window.__pose||await Vision.motor('cuerpo');const pre=window.__pre,m=window.__pose;
    const im=new Image();im.crossOrigin='anonymous';im.src='http://127.0.0.1:8112/banco/'+o.id+'.jpg';await im.decode();
    const k=Math.min(1,960/Math.max(im.width,im.height)),W=Math.round(im.width*k),H=Math.round(im.height*k);
    const fc=document.createElement('canvas');fc.width=W;fc.height=H;fc.getContext('2d').drawImage(im,0,0,W,H);
    const [bx,by,bw,bh]=o.bbox,n=[bx*k/W,by*k/H,(bx+bw)*k/W,(by+bh)*k/H];
    const c=document.createElement('canvas');c.width=c.height=pre.R;const x=c.getContext('2d',{willReadFrequently:true});
    const una=async Z=>{x.drawImage(fc,Z.x,Z.y,Z.w,Z.h,0,0,pre.R,pre.R);const r=await pre.correr({px:x.getImageData(0,0,pre.R,pre.R).data},[Vision.PERSONA]);return aCuadro(r.suj[Vision.PERSONA],r.G,Z,W,H);};
    const Zr=zonaDe([n],W,H,{margen:.1,min:.3,amin:.5,amax:2}),todo={x:0,y:0,w:W,h:H};
    let s=await una(Zr||todo);if(Zr&&(!(s&&s.ok)||s.cortada))s=await una(todo);
    if(!s||!s.ok)return null;
    const Z=recortePose(s.n,W,H,m),c2=document.createElement('canvas');c2.width=m.W;c2.height=m.H;const y=c2.getContext('2d',{willReadFrequently:true});
    y.fillStyle='#000';y.fillRect(0,0,m.W,m.H);y.drawImage(fc,Z.x,Z.y,Z.w,Z.h,0,0,m.W,m.H);
    const P=Array.from((await m.correr({px:y.getImageData(0,0,m.W,m.H).data},[])).kp);
    y.setTransform(-1,0,0,1,m.W,0);y.fillRect(0,0,m.W,m.H);y.drawImage(fc,Z.x,Z.y,Z.w,Z.h,0,0,m.W,m.H);y.setTransform(1,0,0,1,0,0);
    const Q=(await m.correr({px:y.getImageData(0,0,m.W,m.H).data},[])).kp,esp=Vision.ESPEJO;
    for(let i=0;i<P.length/3;i++){const j=esp[i]??i;P[3*i]=(P[3*i]+1-Q[3*j])/2;P[3*i+1]=(P[3*i+1]+Q[3*j+1])/2;P[3*i+2]=(P[3*i+2]+Q[3*j+2])/2;}
    for(let i=0;i<P.length;i+=3){P[i]=Z.x+P[i]*Z.w;P[i+1]=Z.y+P[i+1]*Z.h;}
    return {id:o.id,tipo:o.tipo,k,med:medir(s,W,H),kp:P,sil:{G:s.G,caja:s.caja,Z:s.Z,cx:s.cx,cy:s.cy,n:s.n,mask:Array.from(s.mask)}};},o);
  if(r)out.push(r);if(i%20===0)console.log(i,out.length);}
 fs.writeFileSync(process.env.SAL||'banco.json',JSON.stringify(out));console.log('listo',out.length);await b.close();})();
