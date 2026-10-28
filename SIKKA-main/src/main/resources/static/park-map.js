let ROUTE_DATA,DESTINATIONS=[],nodeById=new Map(),requestNumber=0;
const svg=document.querySelector('#map'),from=document.querySelector('#from'),to=document.querySelector('#to'),result=document.querySelector('#result'),tip=document.querySelector('#tip');
let active=null,drag=null;const ns='http://www.w3.org/2000/svg';
function element(tag,attrs,parent){let el=document.createElementNS(ns,tag);for(const [k,v]of Object.entries(attrs))el.setAttribute(k,v);parent.appendChild(el);return el;}
function polyline(coords,color,width,parent){return element('polyline',{points:coords.map(p=>p.join(',')).join(' '),fill:'none',stroke:color,'stroke-width':width,'stroke-linecap':'round','stroke-linejoin':'round','vector-effect':'non-scaling-stroke'},parent);}
function getRide(select){return DESTINATIONS.find(r=>r.key===select.value);}
function markers(){const group=document.querySelector('#markers');group.replaceChildren();const a=getRide(from),b=getRide(to),r=svg.viewBox.baseVal.width/Math.max(300,svg.clientWidth)*6;
const list=[...DESTINATIONS].sort((x,y)=>(+(x===a||x===b))-(+(y===a||y===b)));
for(const ride of list){let n=nodeById.get(ride.nodeId),selected=ride===a||ride===b;const c=element('circle',{cx:n.x,cy:n.y,r:selected?r*1.5:r,fill:ride===a?'#2ce3a3':ride===b?'#ff704b':ride.kind==='RESTAURANT'?'#fbbf24':ride.kind==='SHOP'?'#38bdf8':'#c084fc',stroke:'#fff','stroke-width':selected?2:1,'vector-effect':'non-scaling-stroke',tabindex:0,'aria-label':ride.name},group);element('title',{},c).textContent=ride.name+' — نقطة وصول تقريبية';c.addEventListener('pointerenter',e=>{tip.hidden=false;tip.textContent=ride.name+' — نقطة وصول تقريبية';tip.style.left=Math.min(e.clientX+12,window.innerWidth-280)+'px';tip.style.top=Math.max(0,e.clientY-45)+'px';});c.addEventListener('pointerleave',()=>tip.hidden=true);}}
async function draw(fit=true){
 const request=++requestNumber;
 document.querySelector('#route').replaceChildren();
 const a=getRide(from),b=getRide(to); active=null;
 if(!a||!b)return;
 markers(); result.textContent='جاري حساب المسار...';
 try{
  const data=await getJson('/api/v1/map/route/'+a.type+'/'+a.id+'/'+b.type+'/'+b.id);
  if(request!==requestNumber)return;
  if(!Number.isFinite(data.distanceMeters)||!Array.isArray(data.points)||!data.points.length||!data.points.every(p=>Array.isArray(p)&&p.length===2&&p.every(Number.isFinite)))throw Error('استجابة المسار غير صالحة');
  active=data;
  polyline(active.points,'#142a26',9,document.querySelector('#route'));
  polyline(active.points,'#33f4ad',5,document.querySelector('#route'));
  result.textContent='من '+a.name+' إلى '+b.name+' | '+active.distanceMeters+' متر تقريباً';
  if(fit){const xs=active.points.map(p=>p[0]),ys=active.points.map(p=>p[1]);const x=Math.min(...xs)-200,y=Math.min(...ys)-200;svg.setAttribute('viewBox',[x,y,Math.max(600,Math.max(...xs)-x+200),Math.max(600,Math.max(...ys)-y+200)].join(' '));}
  markers();
 }catch(error){if(request===requestNumber)result.textContent='تعذر عرض المسار: '+error.message;}
}
function position(e){return new DOMPoint(e.clientX,e.clientY).matrixTransform(svg.getScreenCTM().inverse());}
svg.addEventListener('pointerdown',e=>{if(e.button!==0)return;drag=position(e);tip.hidden=true;svg.setPointerCapture(e.pointerId);});svg.addEventListener('pointermove',e=>{if(!drag)return;let p=position(e),b=svg.viewBox.baseVal;svg.setAttribute('viewBox',[b.x+drag.x-p.x,b.y+drag.y-p.y,b.width,b.height].join(' '));});for(const name of ['pointerup','pointercancel'])svg.addEventListener(name,()=>drag=null);
svg.addEventListener('wheel',e=>{e.preventDefault();tip.hidden=true;let p=position(e),b=svg.viewBox.baseVal,f=e.deltaY>0?1.15:1/1.15;if(b.width*f<150||b.width*f>15000)return;svg.setAttribute('viewBox',[p.x+(b.x-p.x)*f,p.y+(b.y-p.y)*f,b.width*f,b.height*f].join(' '));markers();},{passive:false});
document.querySelector('#draw').onclick=()=>draw();from.onchange=()=>draw();to.onchange=()=>draw();document.querySelector('#swap').onclick=()=>{[from.value,to.value]=[to.value,from.value];draw();};document.querySelector('#fit').onclick=()=>{svg.setAttribute('viewBox','2400 0 4100 4035');markers();};document.querySelector('#show').onchange=e=>document.querySelector('#network').style.display=e.target.checked?'':'none';initialize();

async function getJson(url){
 const response=await fetch(url);
 let data;try{data=await response.json();}catch{throw Error('السيرفر لم يرجع JSON. تأكد من الرابط وتشغيل المشروع');}
 if(!response.ok)throw Error(data.message||('HTTP '+response.status));
 return data;
}
async function initialize(){
 const controls=[from,to,document.querySelector('#draw'),document.querySelector('#swap')];
 controls.forEach(c=>c.disabled=true);
 result.textContent='جاري تحميل الخريطة والوجهات...';
 try{
  const [map,rides,places]=await Promise.all([getJson('/api/v1/map/get/data'),getJson('/api/v1/ride/get'),getJson('/api/v1/place/get')]);
  if(!Array.isArray(map.nodes)||!Array.isArray(map.edges)||!Array.isArray(map.rides)||!Array.isArray(map.places)||!Array.isArray(rides)||!Array.isArray(places))throw Error('صيغة بيانات الخريطة أو الوجهات غير صحيحة');
  ROUTE_DATA=map;nodeById=new Map(map.nodes.map(n=>[n.id,n]));
  for(const [type,items,links,field] of [['RIDE',rides,map.rides,'sourceRideId'],['PLACE',places,map.places,'sourcePlaceId']]){
   for(const item of items){const link=links.find(l=>item[field]&&l[field]===item[field]);if(link&&nodeById.has(link.nodeId))DESTINATIONS.push({id:item.id,type,key:type+':'+item.id,name:item.name,kind:type==='RIDE'?'RIDE':item.kind,nodeId:link.nodeId});}
  }
  if(!DESTINATIONS.length)throw Error('لا توجد وجهات مرتبطة بالخريطة');
for(const ride of DESTINATIONS){for(const select of [from,to]){const option=document.createElement('option');option.value=ride.key;option.textContent=({'RIDE':'لعبة','RESTAURANT':'مطعم','SHOP':'متجر'}[ride.kind]||'مكان')+' — '+ride.name;select.appendChild(option);}}
from.selectedIndex=0;to.selectedIndex=Math.min(1,DESTINATIONS.length-1);
for(const edge of ROUTE_DATA.edges)polyline(edge.points,'#67c8f5',1.5,document.querySelector('#network'));

  controls.forEach(c=>c.disabled=false);await draw(false);
 }catch(error){result.textContent='تعذر تحميل الخريطة: '+error.message;}
}
