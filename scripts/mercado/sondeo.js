// Sondeo 4: Tabla 10.22 del IPCC, fila de corral de tierra (dry lot).
const fs=require('fs');const {execSync}=require('child_process');
execSync('curl -sL -A "Mozilla/5.0" -o c10.pdf https://www.ipcc-nggip.iges.or.jp/public/2019rf/pdf/4_Volume4/19R_V4_Ch10_Livestock.pdf && pdftotext -layout c10.pdf c10.txt');
const L=fs.readFileSync('c10.txt','utf8').split('\n');const i=L.findIndex(l=>/TABLE 10\.22 /.test(l));
const j=L.findIndex((l,k)=>k>i&&/Dry lot/i.test(l));console.log(L.slice(j-6,j+14).join('\n'));
const k=L.findIndex((l,n)=>n>j&&/^\s*Notes?:|Sources?:/.test(l));console.log('\nNOTAS\n'+L.slice(k,k+30).join('\n'));
