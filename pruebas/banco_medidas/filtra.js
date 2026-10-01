// postura de la app: de pie y derecho (piernas rectas y verticales, tronco vertical), de frente, brazos abajo
const fs=require('fs');const B=JSON.parse(fs.readFileSync('banco.json'));
const ok=t=>{const P=t.kp,c=i=>P[3*i+2]>=.4,y=i=>P[3*i+1],x=i=>P[3*i];if(![0,5,6,9,10,11,12,13,14,15,16].every(c))return false;
  const m=(i,j)=>[(x(i)+x(j))/2,(y(i)+y(j))/2],sh=m(5,6),hp=m(11,12),an=m(15,16),T=hp[1]-sh[1],L=an[1]-hp[1];
  if(!(T>0&&L>0))return false;
  if(Math.abs(hp[0]-sh[0])>.15*T||Math.abs(an[0]-hp[0])>.12*L)return false;          // tronco y piernas verticales
  for(const [h,k,a] of [[11,13,15],[12,14,16]]){const t2=(y(k)-y(h))/(y(a)-y(h));if(!(t2>.35&&t2<.65))return false;
    const xl=x(h)+(x(a)-x(h))*t2;if(Math.abs(x(k)-xl)>.06*L)return false;}             // rodillas rectas
  if(L<1.2*T)return false;                                                             // piernas largas (no sentado ni niño chico)
  if((an[1]-y(0))/((y(5)+y(6))/2-y(0))<5.5)return false;                               // cabeza chica: adulto
  const hip=hp[1];return y(9)>hip-.05*T&&y(10)>hip-.05*T&&Math.abs(x(5)-x(6))/T>.6;};
const S=B.filter(t=>t.tipo==='F'&&ok(t));fs.writeFileSync('banco_app.json',JSON.stringify(S));console.log(S.length);
