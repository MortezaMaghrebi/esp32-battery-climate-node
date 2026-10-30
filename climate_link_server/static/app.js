"use strict";
const $=id=>document.getElementById(id);
let token="",selected="",timer=null,busy=false,generation=0;
const format=n=>new Intl.NumberFormat("fa-IR",{maximumFractionDigits:1}).format(n);
const date=s=>s?new Date(s).toLocaleString("fa-IR"):"ثبت نشده";
function status(text,error=false){$("status").textContent=text;$("status").classList.toggle("error",error);}
async function api(path){
  const response=await fetch(path,{headers:{Authorization:`Bearer ${token}`},cache:"no-store",signal:AbortSignal.timeout(12000)});
  if(!response.ok)throw Error(response.status===401?"توکن دسترسی معتبر نیست.":`خطای سرور: ${response.status}`);
  return response.json();
}
function el(tag,text,className){const e=document.createElement(tag);e.textContent=text;if(className)e.className=className;return e;}
function chart(id,rows,key,color){
  const svg=$(id);svg.replaceChildren();if(!rows.length)return;
  const values=rows.map(r=>r[key]);const lo=Math.min(...values)-1,hi=Math.max(...values)+1;
  const create=(tag,attrs)=>{const n=document.createElementNS("http://www.w3.org/2000/svg",tag);for(const[k,v]of Object.entries(attrs))n.setAttribute(k,v);svg.append(n);return n;};
  for(let i=0;i<4;i++){const y=20+i*44;create("line",{x1:45,y1:y,x2:585,y2:y,stroke:"#26364b"});create("text",{x:4,y:y+4,fill:"#9cacc0","font-size":11}).textContent=(hi-i*(hi-lo)/3).toFixed(1);}
  const points=values.map((v,i)=>`${50+i*530/Math.max(1,values.length-1)},${20+(hi-v)*132/(hi-lo)}`);
  create("polyline",{points:points.join(" "),fill:"none",stroke:color,"stroke-width":2.5});
  if(values.length===1){const [cx,cy]=points[0].split(",");create("circle",{cx,cy,r:4,fill:color});}
}
async function history(requestGeneration){
  if(!selected)return;
  const device=selected;
  const {samples}=await api(`/api/v1/measurements?device_id=${encodeURIComponent(device)}&limit=120`);
  if(requestGeneration!==generation||device!==selected)return;
  $("history").hidden=false;$("selected").textContent=device;$("sample-count").textContent=`${format(samples.length)} نمونه اخیر`;
  chart("temperature-chart",samples,"temperature_c","#4dd3b2");chart("humidity-chart",samples,"humidity_pct","#77b8fc");
  $("rows").replaceChildren(...samples.slice().reverse().map(r=>{
    const tr=document.createElement("tr");
    for(const value of [r.sequence,r.timestamp?date(r.timestamp*1000):"ثبت نشده",`${format(r.temperature_c)} °C`,`${format(r.humidity_pct)} %`,r.simulated?"شبیه‌سازی‌شده":"سنسور واقعی",date(r.received_at)])tr.append(el("td",value));
    return tr;
  }));
}
async function refresh(){
  if(busy||!token)return;busy=true;const currentGeneration=generation;
  try{
    const {devices}=await api("/api/v1/devices");if(currentGeneration!==generation)return;
    if(!selected&&devices.length)selected=devices[0].device_id;
    const cards=devices.map(d=>{
      const card=el("button","",`device${d.device_id===selected?" active":""}`);card.type="button";
      card.append(el("h3",d.device_id));if(d.simulated)card.append(el("span","داده شبیه‌سازی‌شده","simulated"));
      const values=el("div","","values");values.append(el("span",`${format(d.temperature_c)}°`),el("span",`${format(d.humidity_pct)}%`));card.append(values);
      card.append(el("small",`آخرین دریافت: ${date(d.received_at)}`),el("small",`${format(d.sample_count)} نمونه · باتری: ${d.battery_mv===null?"سخت‌افزار اندازه‌گیری ندارد":format(d.battery_mv)+" mV"}`));
      card.addEventListener("click",()=>{selected=d.device_id;refresh();});return card;
    });
    $("devices").replaceChildren(...(cards.length?cards:[el("div","هنوز داده‌ای دریافت نشده. آدرس سرور و توکن را در اپ تنظیم کنید.","empty")]));
    await history(currentGeneration);if(currentGeneration!==generation)return;
    status(`متصل · ${format(devices.length)} دستگاه · به‌روزرسانی خودکار هر ۱۵ ثانیه`);
  }catch(error){if(currentGeneration===generation)status(error.message,true);}finally{busy=false;}
}
$("connect").addEventListener("click",()=>{token=$("token").value.trim();$("token").value="";generation++;selected="";$("refresh").disabled=!token;clearInterval(timer);refresh();timer=setInterval(refresh,15000);});
$("token").addEventListener("keydown",e=>{if(e.key==="Enter")$("connect").click();});
$("refresh").addEventListener("click",refresh);
$("logout").addEventListener("click",()=>{token="";generation++;selected="";clearInterval(timer);$("token").value="";$("refresh").disabled=true;$("history").hidden=true;$("rows").replaceChildren();$("devices").replaceChildren(el("div","از سرور خارج شدید.","empty"));status("برای مشاهده داده‌ها، توکن را وارد کنید.");});
