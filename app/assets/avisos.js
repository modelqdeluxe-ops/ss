/* Avisos de Rumi en el teléfono y widget de la pantalla de inicio (solo en la app de Android).
   - Rumi arma una lista de avisos con fecha y prioridad: señales de alerta (ganancia o consumo que
     bajan, animales que pierden peso, costos altos), alimento que se acaba, vacunas y pesajes que tocan,
     lotes que llegan a la meta y el resumen de la semana.
   - El teléfono muestra como máximo UN aviso al día, el más importante, a la hora que elijas.
     El mismo problema no se repite en la misma semana. Todo es opcional y se apaga en Configuración.
   - El widget muestra cabezas, lotes, tareas de hoy y lo más importante que dice Rumi. */
(function(){
'use strict';
const A=()=>window.Android&&Android.avisosEstado?Android:null;
const txt=s=>{let t=String(s||'').replace(/<[^>]+>/g,' ').replace(/\s+/g,' ').trim();try{t=aUnidad(t).replace(/[\u0005\u0006⁣⁤]/g,'');}catch(e){}return window.I18N&&I18N.txt?I18N.txt(t):t;};
const semana=f=>{const d=new Date(f+'T12:00:00');const j=new Date(d.getFullYear(),0,1);return d.getFullYear()+'-'+Math.ceil(((d-j)/864e5+j.getDay()+1)/7);};
const clave=t=>String(t).replace(/[\d.,%−-]+/g,'#').replace(/\s+/g,' ').trim();

function cola(){
  const C=calc(),H=C.H,L=[];
  const add=(id,f,t,x,p,ir,dias=3)=>L.push({id,f,hasta:addDias(f,dias),t:txt(t),x:txt(x),p,ir:ir||'#hoy'});
  if(!Object.keys(S.lotes).length)return L;
  // señales de alerta de hoy: el mismo problema una vez por semana como máximo
  try{for(const s of RumiMas.senales().slice(0,6))add('sen:'+clave(s.t)+':'+semana(H),H,s.t,s.s,s.p,'#analisis/senales',1);}catch(e){}
  // lo que toca en la agenda (sin las entregas de comida ni la revisión diaria: eso no necesita aviso)
  try{for(const t of Agenda.tareas()){if(/^(ent|dia|resp):/.test(t.key))continue;
    const p=/^ref:/.test(t.key)?0.75:/^bod:/.test(t.key)?0.85:/^ven:/.test(t.key)?0.7:/^pre:/.test(t.key)?0.55:/^ret:/.test(t.key)?0.5:/^pes:/.test(t.key)?0.45:/^propia:/.test(t.key)?0.8:0.4;
    add('ag:'+t.key,t.f<H?H:t.f,t.t,t.s,p,/^bod:/.test(t.key)?'#bodega':'#agenda',t.f<H?2:4);}}catch(e){}
  // alimento que se va a acabar: aviso unos días antes, aunque no abras la app
  try{for(const it of ((S.config.bodega||{}).items||[])){const e=Extras.bodega.estado(it);if(!e||e.sin||e.dias==null)continue;const min=+it.min||7;const f=addDias(H,Math.max(0,Math.floor(e.dias-min)));
    if(f>H)add('bodf:'+it.id+':'+f,f,`${it.n} alcanza para ${pl(min,'día','días')}`,'Compra con tiempo: un cambio brusco de dieta baja la ganancia.',0.85,'#bodega',3);}}catch(e){}
  // lotes que llegan a su meta
  for(const x of C.act)if(x.fechaMeta&&x.fechaMeta>H&&!x.listo)add('meta:'+x.id+':'+x.fechaMeta,x.fechaMeta,`${x.l.nombre} llega hoy a su peso meta`,`${pl(x.cab,'cabeza','cabezas')} con unos ${nf(x.meta)} kg. Mira cuándo te conviene vender.`,0.7,'#analisis/vender',3);
  // resumen de la semana, el lunes
  const d=new Date(H+'T12:00:00'),lunes=addDias(H,(8-d.getDay())%7||7);
  try{const kg=C.act.reduce((s,x)=>s+(x.gdpUse||0)*x.cab*7,0);add('sem:'+lunes,lunes,'Tu semana con Rumi',`Tus lotes suman unos ${nf(kg)} kg por semana. Toca para ver el resumen y compartirlo.`,0.3,'#analisis/semana',1);}catch(e){}
  return L;
}
function datosWidget(){
  const C=calc(),H=C.H;
  if(!Object.keys(S.lotes).length)return {finca:S.config.finca||'Rumentis',grande:txt('Crea tu primer lote'),sub:txt('Rumi te ayuda paso a paso'),tareas:'',rumi:'',act:'',anotar:txt('+ Anotar')};
  let T=[];try{T=Agenda.tareas().filter(t=>t.f<=H);}catch(e){}
  let s=null;try{s=RumiMas.senales()[0];}catch(e){}
  const hora=new Date().toTimeString().slice(0,5);
  return {finca:S.config.finca||'Rumentis',grande:txt(`${pl(C.cabT,'cabeza','cabezas')}`),sub:txt(`${pl(C.act.length,'lote','lotes')} · ${nf(C.kgPie)} kg en pie`),
    tareas:txt(T.length?`Hoy: ${pl(T.length,'tarea','tareas')} · ${T[0].t}`:'Hoy no tienes tareas pendientes'),
    rumi:txt(s?`Rumi: ${s.t}`:'Rumi: todo en orden'),rumiIr:s?'#analisis/senales':'#analisis',act:txt(`Actualizado ${ffc(H)}, ${hora}`),anotar:txt('+ Anotar')};
}
let _v=-2,_t=0;
function sincronizar(forzar){
  const an=A();if(!an)return;if(!forzar&&_v===ver)return;_v=ver;
  clearTimeout(_t);_t=setTimeout(()=>{try{an.avisos(JSON.stringify(cola()));}catch(e){}try{an.widget(JSON.stringify(datosWidget()));}catch(e){}},forzar?0:1500);
}
setInterval(()=>sincronizar(false),5000);
setTimeout(()=>sincronizar(true),2500);
document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')sincronizar(true);});
// abrir la app desde un aviso o el widget
window.avisoIr=function(){try{const an=A();const d=an&&Android.avisoIr();if(d){closeSheet();if(location.hash!==d)location.hash=d;}}catch(e){}};
setTimeout(()=>window.avisoIr(),600);

/* ================= Configuración: avisos al teléfono ================= */
const estado=()=>{const an=A();try{return an?Android.avisosEstado():'web';}catch(e){return 'web';}};
const horaAv=()=>{try{return A()?Android.avisosHora():7;}catch(e){return 7;}};
function cfgHtml(){
  const e=estado(),h=horaAv();
  if(e==='web')return `<p class="hint" style="margin:0">En la app de Android, Rumi te puede mandar un aviso al día con lo importante de tu engorde y tienes un widget para la pantalla de inicio.</p>`;
  return `<div class="q"><span class="lb">Avisos de Rumi</span><div class="seg" role="group" aria-label="Avisos de Rumi">${[['si','Sí'],['no','No']].map(([v,t])=>`<button type="button" data-act="avisosSi" data-v="${v}" aria-pressed="${(e!=='no')===(v==='si')}">${t}</button>`).join('')}</div></div>
   ${e==='sin-permiso'?`<div class="nota-cfg">${ico('x',2.2)}<p><b>Falta el permiso.</b> Toca Sí otra vez y permite las notificaciones, o actívalas en los ajustes del teléfono para Rumentis.</p></div>`:''}
   ${e!=='no'?`<div class="q"><label for="avHora">A qué hora</label><select class="in" id="avHora" data-avhora>${[5,6,7,8,9,10,12,14,16,18,19,20].map(x=>`<option value="${x}"${x===h?' selected':''}>${x}:00</option>`).join('')}</select></div>
   <button type="button" class="btn full" data-act="avisoPrueba">Probar un aviso ahora</button>`:''}
   <small class="hint" style="margin:0">Como máximo <b>un aviso al día</b> y solo cuando importa: un lote que baja su ganancia o come menos, alimento que se acaba, una vacuna que toca o un lote que llega a su meta. El mismo problema no se repite en la semana.</small>
   <div class="nota-cfg">${ico('check',2.2)}<p><b>Widget:</b> mantén el dedo sobre la pantalla de inicio del teléfono, toca Widgets y busca Rumentis. Muestra tus cabezas, las tareas de hoy y lo que dice Rumi.</p></div>`;
}
const pintar=()=>{const e=document.getElementById('avCfg');if(e)e.innerHTML=aUnidad(cfgHtml());};
if(typeof MAS_SUB!=='undefined'&&MAS_SUB.config){const _c=MAS_SUB.config;MAS_SUB.config=function(){const h=_c.apply(this,arguments);
  return h.replace(`<section class="sec">${secH('Voz de Rumi')}`,`<section class="sec">${secH('Avisos al teléfono y widget')}<div class="card pad cfg-c" id="avCfg">${cfgHtml()}</div></section><section class="sec">${secH('Voz de Rumi')}`);};}
Object.assign(ACTS,{
  avisosSi:el=>{const an=A();if(!an)return;const si=el.dataset.v==='si';try{Android.avisosActivar(si,horaAv());}catch(e){}pintar();sincronizar(true);
    toast(si?'Listo: Rumi te avisará lo importante, una vez al día como máximo':'Avisos apagados');
    if(si){let n=0;const t=setInterval(()=>{pintar();if(++n>12||estado()==='si')clearInterval(t);},1500);}},
  avisoPrueba:()=>{const an=A();if(!an)return;const s=(()=>{try{return RumiMas.senales()[0];}catch(e){return null;}})();
    try{Android.avisoPrueba(txt(s?s.t:'Rumi está pendiente de tu engorde'),txt(s?s.s:'Así se verán los avisos: uno al día, solo lo importante.'),s?'#analisis/senales':'#hoy');}catch(e){}
    if(estado()==='sin-permiso')toast('Primero permite las notificaciones de Rumentis');}
});
document.addEventListener('change',e=>{if(!e.target.matches||!e.target.matches('[data-avhora]'))return;try{Android.avisosActivar(true,+e.target.value);}catch(err){}toast('Hora de los avisos: '+e.target.value+':00');});

/* en Rumi: Usar la app → avisos y widget */
if(window.RumiMenu){const U=RumiMenu.AREAS.find(a=>a.id==='app');if(U){const o=U.ops;U.ops=()=>{const L=o();L.splice(1,0,{l:'Avisos al teléfono y widget',fn:()=>({html:`<b>Avisos al teléfono</b><p class="rs">Te mando como máximo un aviso al día, a la hora que elijas, solo cuando algo importa: un lote que baja su ganancia o come menos, alimento que se acaba, una vacuna que toca o un lote que llega a la meta. Se activan en Configuración, «Avisos al teléfono y widget».</p><b>Widget</b><p class="rs">Mantén el dedo sobre la pantalla de inicio de tu teléfono, toca Widgets y busca Rumentis. Verás tus cabezas, las tareas de hoy y lo más importante que te digo.</p>`,btns:[['Ir a Configuración',{t:'go',go:'#mas/config'}]]})});return L;};}}
window.Avisos={cola,datosWidget,sincronizar};
})();
