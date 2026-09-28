// Sondeo 2 (diagnóstico): contenido del reporte de Oklahoma City (PDF) y del Excel de precios de ERS
const {execSync}=require('child_process');const fs=require('fs');
const cortar=(t,n=600)=>String(t).replace(/\s+/g,' ').slice(0,n);
async function bajar(url,arch){const r=await fetch(url,{headers:{'User-Agent':'Mozilla/5.0 (rumentis)'}});const b=Buffer.from(await r.arrayBuffer());fs.writeFileSync(arch,b);console.log('\n==',r.status,url,r.headers.get('content-type'),b.length);return r.status;}
(async()=>{
  // enlaces de la página de subastas
  const h=await (await fetch('https://www.ams.usda.gov/market-news/feeder-and-replacement-cattle-auctions',{headers:{'User-Agent':'Mozilla/5.0'}})).text();
  const L=[...h.matchAll(/href="([^"]+)"[^>]*>([^<]{2,80})</g)].filter(m=>/mnreports|MARS|viewReport|Oklahoma|National|Joplin|Dodge|Texas|Nebraska/i.test(m[1]+m[2])).map(m=>m[2].trim()+' -> '+m[1]);
  console.log('enlaces',L.slice(0,80).join('\n'));
  for(const id of ['1280','1920','2466','1953','3456']){const a='r'+id+'.pdf';if(await bajar(`https://www.ams.usda.gov/mnreports/LSD_MARS_${id}.pdf`,a)===200){try{const t=execSync(`pdftotext -layout ${a} -`).toString();console.log(t.slice(0,3500));}catch(e){console.log('pdftotext',e.message);}}}
  if(await bajar('https://www.ers.usda.gov/media/5536/livestock-prices.xlsx','lp.xlsx')===200){
    execSync('npm i --no-save --no-audit --no-fund xlsx@0.18.5 >/dev/null');const X=require('xlsx');const wb=X.readFile('lp.xlsx');
    for(const n of wb.SheetNames){const rows=X.utils.sheet_to_json(wb.Sheets[n],{header:1});console.log('\n-- hoja',n,rows.length);rows.slice(0,8).forEach(r=>console.log(cortar(JSON.stringify(r),400)));console.log('...');rows.slice(-4).forEach(r=>console.log(cortar(JSON.stringify(r),400)));}
  }
  for(const u of ['https://mymarketnews.ams.usda.gov/National_Feeder_Stocker_Dashboard','https://mymarketnews.ams.usda.gov/public_data'])try{const r=await fetch(u,{headers:{'User-Agent':'Mozilla/5.0'}});console.log('\n==',r.status,u,cortar(await r.text(),500));}catch(e){console.log('ERR',u,e.message);}
})();
