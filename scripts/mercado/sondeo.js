// Sondeo (diagnóstico): fuentes públicas del precio del ternero / novillo de engorde en EE. UU.
const cortar=(t,n=600)=>String(t).replace(/\s+/g,' ').slice(0,n);
async function ver(url,{grep,n=600}={}){
  try{const r=await fetch(url,{headers:{'User-Agent':'Mozilla/5.0 (rumentis)'},redirect:'follow'});const t=await r.text();
    console.log('\n==',r.status,url,'tipo',r.headers.get('content-type'),'largo',t.length);
    if(grep){const L=t.split(/\n/).filter(l=>grep.test(l)).slice(0,40);console.log(L.map(l=>cortar(l,300)).join('\n'));}
    else console.log(cortar(t,n));
    return t;}catch(e){console.log('\n== ERROR',url,e.message);return '';}
}
(async()=>{
  const lista=await ver('https://mpr.datamart.ams.usda.gov/services/v1.1/reports',{n:300});
  try{const j=JSON.parse(lista);const a=(j.results||j).filter(x=>/feeder|stocker|calf|calves/i.test(JSON.stringify(x)));console.log('datamart feeder',JSON.stringify(a).slice(0,3000));}catch(e){console.log('lista no json');}
  await ver('https://www.ams.usda.gov/market-news/livestock-poultry-grain',{grep:/feeder|stocker/i});
  await ver('https://www.ams.usda.gov/market-news/feeder-and-replacement-cattle-auctions',{grep:/mnreports|\.txt|\.pdf|feeder/i});
  for(const id of ['ams_1920','ams_1832','ams_1834','ams_2466','sj_ls850','ko_ls750','ams_1281','ams_1280'])await ver(`https://www.ams.usda.gov/mnreports/${id}.txt`,{n:500});
  await ver('https://marsapi.ams.usda.gov/services/v1.2/reports',{n:200});
  await ver('https://www.cmegroup.com/ftp/cash_settled_commodity_index_prices/daily_data/',{n:1500});
  await ver('https://www.ers.usda.gov/data-products/livestock-and-meat-domestic-data',{grep:/xlsx|csv|price/i});
  await ver('https://mymarketnews.ams.usda.gov/viewReport/1920',{n:800});
})();
