// Sondeo 2: formato del detalle de USDA Datamart y tablas del IPCC que faltan.
const fs=require('fs');const {execSync}=require('child_process');
const UA={'User-Agent':'Mozilla/5.0 Rumentis/1.0'};
async function ver(u,n=3000){try{const r=await fetch(u,{headers:UA});const t=await r.text();console.log(`\n##### ${u}\nstatus ${r.status}`);console.log(t.slice(0,n));return t;}catch(e){console.log(`\n##### ${u}\nERROR ${e.message}`);}}
(async()=>{
  const B='https://mpr.datamart.ams.usda.gov/services/v1.1/reports/';
  await ver(B+'2477/Detail?q=report_date=09/21/2026',6000);
  await ver(B+'2477/Detail?q=report_date=09/21/2026&allSections=true',400);
  await ver(B+'2477?allSections=true&q=report_date=09/21/2026',400);
  await ver(B+'2466/Detail?q=report_date=09/25/2026',3000);
  await ver(B+'2477/History',3000);
  for(const f of ['19R_V4_Ch10_Livestock','19R_V4_Ch11_Soils_N2O_CO2']){
    try{execSync(`curl -sL -A "Mozilla/5.0" -o ${f}.pdf https://www.ipcc-nggip.iges.or.jp/public/2019rf/pdf/4_Volume4/${f}.pdf && pdftotext -layout ${f}.pdf ${f}.txt`);}catch(e){console.log('pdf',e.message);}}
  const L=f=>fs.existsSync(f)?fs.readFileSync(f,'utf8').split('\n'):[];
  const show=(lines,re,a,b,max=2)=>{const idx=lines.map((l,i)=>re.test(l)?i:-1).filter(i=>i>=0).slice(0,max);for(const i of idx){console.log(`\n--- ${re} @${i}`);console.log(lines.slice(Math.max(0,i-a),i+b).join('\n'));}};
  const c10=L('19R_V4_Ch10_Livestock.txt'),c11=L('19R_V4_Ch11_Soils_N2O_CO2.txt');
  show(c10,/TABLE 10\.16 /,2,45);show(c10,/Dry lot/,3,6,12);show(c10,/TABLE 10\.2 /,2,50,1);
  show(c11,/TABLE 11\.3 /,2,50,1);
})();
