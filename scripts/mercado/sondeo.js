// Sondeo 3: tablas del IPCC para el estiércol en corral de tierra (dry lot).
const fs=require('fs');const {execSync}=require('child_process');
try{execSync('curl -sL -A "Mozilla/5.0" -o c10.pdf https://www.ipcc-nggip.iges.or.jp/public/2019rf/pdf/4_Volume4/19R_V4_Ch10_Livestock.pdf && pdftotext -layout c10.pdf c10.txt');}catch(e){console.log(e.message);}
const L=fs.readFileSync('c10.txt','utf8').split('\n');
const show=(re,a,b,max=3)=>{L.map((l,i)=>re.test(l)?i:-1).filter(i=>i>=0).slice(0,max).forEach(i=>{console.log(`\n--- ${re} @${i}`);console.log(L.slice(Math.max(0,i-a),i+b).join('\n'));});};
show(/TABLE 10\.14 /,2,70,1);show(/TABLE 10\.22 /,2,90,1);show(/TABLE 10\.16 /,2,40,1);show(/EQUATION 10\.25 /,2,25,1);show(/EQUATION 10\.31 /,2,30,1);show(/EQUATION 10\.33 /,2,30,1);
