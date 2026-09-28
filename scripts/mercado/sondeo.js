// Sondeo de fuentes (se corre en GitHub Actions, que tiene internet): muestra estado, CORS y un pedazo de cada respuesta.
const fs=require('fs');const {execSync}=require('child_process');
const UA={'User-Agent':'Mozilla/5.0 (Linux; Android 14) Rumentis/1.0','Accept':'*/*','Origin':'https://appassets.androidplatform.net'};
async function ver(u,n=1200,filtro){try{const r=await fetch(u,{headers:UA,redirect:'follow'});const t=await r.text();
  console.log(`\n##### ${u}\nstatus ${r.status} ${r.headers.get('content-type')} cors=${r.headers.get('access-control-allow-origin')}`);
  const s=filtro?filtro(t):t;console.log(String(s).slice(0,n));return t;}catch(e){console.log(`\n##### ${u}\nERROR ${e.message}`);}}
(async()=>{
  const L=await ver('https://mpr.datamart.ams.usda.gov/services/v1.1/reports',200);
  try{const j=JSON.parse(L);const c=(Array.isArray(j)?j:j.results||[]).filter(x=>/LM_CT|LM_XB4|feeder/i.test(JSON.stringify(x)));console.log(JSON.stringify(c,null,0).slice(0,6000));}catch(e){console.log('no json',e.message);}
  for(const id of ['2466','2477','2484','2498','2500','2513'])await ver(`https://mpr.datamart.ams.usda.gov/services/v1.1/reports/${id}`,1500);
  for(const u of ['https://www.ams.usda.gov/mnreports/ko_ls750.txt','https://www.ams.usda.gov/mnreports/ams_1280.txt','https://www.ams.usda.gov/mnreports/sj_ls850.txt','https://www.ams.usda.gov/mnreports/lm_ct150.txt','https://www.ams.usda.gov/mnreports/lm_ct100.txt','https://mymarketnews.ams.usda.gov/public_data','https://marsapi.ams.usda.gov/services/v1.2/reports/1280'])await ver(u,1500);
  for(const u of ['https://www.cepea.org.br/br/indicador/boi-gordo.aspx','https://www.cepea.org.br/br/indicador/bezerro.aspx','https://www.cepea.org.br/br/indicador/milho.aspx'])
    await ver(u,2500,t=>{const i=t.search(/imagenet-table|<table/i);return i<0?t.slice(0,800):t.slice(i,i+2500);});
  await ver('https://api.bcb.gov.br/dados/serie/bcdata.sgs.1/dados/ultimos/3?formato=json',400);
  await ver('https://open.er-api.com/v6/latest/USD',600);
  await ver('https://www.bch.hn/',300);
  // IPCC 2019, capítulo 10 (ganado): valores para la huella de carbono
  try{execSync('curl -sL -A "Mozilla/5.0" -o ipcc10.pdf https://www.ipcc-nggip.iges.or.jp/public/2019rf/pdf/4_Volume4/19R_V4_Ch10_Livestock.pdf && pdftotext -layout ipcc10.pdf ipcc10.txt');
    const t=fs.readFileSync('ipcc10.txt','utf8');const lines=t.split('\n');
    const ver2=(re,a=4,b=40)=>{const idx=lines.map((l,i)=>re.test(l)?i:-1).filter(i=>i>=0).slice(0,3);for(const i of idx){console.log(`\n--- ${re} @${i}`);console.log(lines.slice(Math.max(0,i-a),i+b).join('\n'));}};
    ver2(/TABLE 10\.12 /,2,60);ver2(/55\.65/,6,6);ver2(/18\.45 MJ/,4,4);ver2(/EQUATION 10\.24/,2,30);ver2(/TABLE 10\.16 /,2,40);ver2(/TABLE 10\.17 /,2,70);ver2(/TABLE 10\.21 /,2,60);ver2(/TABLE 10\.22 /,2,50);ver2(/Frac\s*GasMS/,2,10);
  }catch(e){console.log('ipcc',e.message);}
})();
