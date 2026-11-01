"""Promo video mock-up: ~23 s vertical (1080x1920) MP4 built from the store mock-up screens.
Run from anywhere: python3 tools/marketing/promo_video.py out.mp4   (needs playwright + ffmpeg)"""
import sys, pathlib, subprocess
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import screens as S
from playwright.sync_api import sync_playwright

FPS, DUR = 24, 23.0
EV = {1:[("Stand-up",S.BLUE)],2:[("Dentist",S.RED)],6:[("Stand-up",S.BLUE)],8:[("Stand-up",S.BLUE),("Yoga",S.GREEN)],13:[("Dinner",S.PURPLE)],
      19:[("Lunch",S.ORANGE)],22:[("Review",S.PURPLE),("Yoga",S.GREEN)],24:[("Match",S.RED)],29:[("Stand-up",S.BLUE),("Lunch",S.ORANGE),("Yoga",S.GREEN)],
      30:[("Dentist",S.RED)],31:[("Party",S.PURPLE)]}

def month_new():
    d = [27,28,29,30]+list(range(1,32)); cells = ''
    for i,n in enumerate(d):
        dim = i < 4; today = (n == 29 and not dim); evs = [] if dim else EV.get(n, [])
        chips = ''.join(f'<div style="height:15px;border-radius:5px;background:{c};margin-top:3px;font-size:8.5px;font-weight:600;line-height:15px;padding:0 4px;white-space:nowrap;overflow:hidden;color:#fff">{t}</div>' for t,c in evs[:2])
        if len(evs) > 2: chips += f'<div style="font-size:8.5px;opacity:.85;margin-top:2px;text-align:center">+{len(evs)-2}</div>'
        dots = ''.join(f'<span style="display:inline-block;width:5px;height:5px;border-radius:50%;background:{c};margin:0 1.5px"></span>' for t,c in evs[:3])
        circ = "background:#fff;color:#12213f;font-weight:700" if today else ""
        cells += (f'<div class="mc" style="padding:5px 3px;opacity:{.4 if dim else 1}"><div style="width:28px;height:28px;border-radius:50%;margin:0 auto;display:flex;align-items:center;justify-content:center;font-size:14px;{circ}">{n}</div>'
                  f'<div class="chips">{chips}</div><div class="dots" style="opacity:0;height:8px;text-align:center;margin-top:3px">{dots}</div></div>')
    hdr = ''.join(f'<div style="text-align:center;font-size:10.5px;opacity:.8">{x}</div>' for x in ["Sun","Mon","Tue","Wed","Thu","Fri","Sat"])
    plus = '<div style="width:34px;height:34px;border-radius:12px;background:#fff;color:#12213f;font-size:24px;text-align:center;line-height:31px">+</div>'
    panel = (f'<div id="mpanel" style="position:absolute;left:12px;right:12px;top:700px;bottom:84px;border-radius:26px;background:rgba(255,255,255,.17);border:1px solid rgba(255,255,255,.22);padding:14px 16px;opacity:0;overflow:hidden">'
             f'<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px"><div style="font-size:19px;font-weight:500">Thursday 29 October</div>{plus}</div>'
             + S.card("Team stand-up","9:00 AM – 9:15 AM",S.BLUE)+S.card("Lunch with Sam","12:30 PM – 1:30 PM",S.ORANGE)+'</div>')
    return (f'{S.STATUS}<div style="padding:46px 22px 0"><div class="top" style="justify-content:space-between"><div><div style="font-size:30px;font-weight:300">October</div><div style="font-size:11px;opacity:.8">2026</div></div><span class="pill">Go to today</span></div></div>'
            f'<div style="position:absolute;left:12px;right:12px;top:112px;height:696px;border-radius:24px;background:rgba(10,30,70,.25);padding:10px 6px" id="mgrid"><div style="display:grid;grid-template-columns:repeat(7,1fr);margin-bottom:2px">{hdr}</div>'
            f'<div id="mcells" style="display:grid;grid-template-columns:repeat(7,1fr);grid-auto-rows:130px">{cells}</div></div>{panel}{S.TABS("Month")}')

def fold_screen():
    h = S.fold()
    # wrap the right-hand card so it can slide in
    i = h.index('<div class="glass"'); j = h.index('<div class="fab"')
    return h[:i] + '<div id="fcard" style="position:absolute;inset:0;pointer-events:none">' + h[i:j] + '</div>' + h[j:]

CSS = S.CSS + """
body{background:#4A6FA5}
#stage{position:absolute;inset:0;overflow:hidden}
#intro{position:absolute;left:0;right:0;top:640px;text-align:center}
#intro h1{font-family:Plex;font-weight:700;font-size:200px;text-shadow:0 6px 30px rgba(10,40,90,.35)}
#intro p{font-size:46px;opacity:.92;margin-top:18px}
#glow{position:absolute;left:50%;width:900px;height:900px;margin-left:-450px;border-radius:50%;background:radial-gradient(#fff6d8,rgba(255,246,216,0) 68%)}
#cap{position:absolute;top:110px;left:0;right:0;text-align:center}
#cap h1{font-family:Plex;font-weight:700;font-size:92px;line-height:1.08;text-shadow:0 4px 18px rgba(10,40,90,.35)}
#cap p{font-size:40px;opacity:.92;margin-top:20px}
.ph{position:absolute;left:50%;transform:translateX(-50%);border-radius:92px;padding:16px;background:#0b0f1a;box-shadow:0 40px 90px rgba(5,25,70,.45),0 0 0 3px #3a4256}
.ph .scr{transform-origin:0 0;position:relative;overflow:hidden;border-radius:44px}
.layer{position:absolute;inset:0}
#free{position:absolute;left:0;right:0;top:640px;text-align:center}
#free div{font-family:Plex;font-weight:700;font-size:112px;line-height:1.25;text-shadow:0 4px 18px rgba(10,40,90,.35);opacity:0}
#end{position:absolute;left:0;right:0;top:700px;text-align:center;opacity:0}
#end h1{font-family:Plex;font-weight:700;font-size:200px;text-shadow:0 6px 30px rgba(10,40,90,.35)}
#end p{font-size:50px;opacity:.95;margin-top:26px}
"""

JS = """
const STOPS=[[5,'#1B2250','#5B4B8A'],[7,'#4A6FA5','#D9805F'],[11,'#2E78C7','#5FA3DE'],[15,'#D98A2F','#A84B28'],[18.5,'#B8426A','#3C2A6E'],[21,'#141A3D','#2B2358'],[24,'#0B1026','#1B2250']];
const hex=c=>[1,3,5].map(i=>parseInt(c.slice(i,i+2),16));
const mix=(a,b,t)=>{a=hex(a);b=hex(b);return 'rgb('+a.map((v,i)=>Math.round(v+(b[i]-v)*t)).join(',')+')'};
function sky(h){let i=0;for(let k=0;k<STOPS.length-1;k++)if(h>=STOPS[k][0])i=k;const a=STOPS[i],b=STOPS[i+1];const t=Math.max(0,Math.min(1,(h-a[0])/(b[0]-a[0])));return [mix(a[1],b[1],t),mix(a[2],b[2],t)]}
const cl=(x,a=0,b=1)=>Math.max(a,Math.min(b,x));
const seg=(t,a,b)=>cl((t-a)/(b-a));
const ease=x=>x<.5?4*x*x*x:1-Math.pow(-2*x+2,3)/2;
const out=x=>1-Math.pow(1-x,3);
const $=id=>document.getElementById(id);
const DAYBLUE=['rgb(46,120,199)','rgb(95,163,222)','rgb(140,192,236)'];
function setCap(h,p,o){$('cap').style.opacity=o;$('cap').querySelector('h1').innerHTML=h;$('cap').querySelector('p').textContent=p}
function clock(h){const hh=Math.floor(h),mm=Math.floor((h-hh)*60/10)*10;const ap=hh>=12&&hh<24?'PM':'AM';const h12=((hh+11)%12)+1;return h12+':'+String(mm).padStart(2,'0')+' '+ap}
function seek(t){
  // background: sunrise -> day blue
  const bgT=ease(seg(t,2.2,3.4));
  document.body.style.background=`linear-gradient(170deg,${mix('#4A6FA5','#2E78C7',bgT)} 0%,${mix('#D9805F','#4F95D6',bgT)} 55%,${mix('#E8A06A','#8CC0EC',bgT)} 100%)`;
  // intro
  const iin=out(seg(t,.2,1.4)), iout=1-seg(t,2.0,2.8);
  $('intro').style.opacity=iin*iout; $('intro').style.transform=`translateY(${(1-iin)*40}px)`;
  $('glow').style.top=(1100-out(seg(t,0,2.4))*620)+'px'; $('glow').style.opacity=.8*(1-seg(t,2.2,3.2));
  // phone 1 (schedule -> month)
  const pin=out(seg(t,2.6,3.9)), pout=ease(seg(t,13.0,14.0));
  const p1=$('p1'); p1.style.top=(470+(1-pin)*1500+pout*1500)+'px';
  // schedule sky 6:00 -> 23:00 over 4.2 .. 8.6
  const hr=6+17*ease(seg(t,4.2,8.6));
  const [c1,c2]=sky(hr); $('scr1').style.background=`linear-gradient(${c1},${c2})`;
  const mono=t>=8.6?1:0; // month screen takes over
  const mx=ease(seg(t,8.6,9.4));
  $('L_sched').style.opacity=1-mx; $('L_month').style.opacity=mx;
  $('scr1').style.background= mx>0? `linear-gradient(${mix('#2E78C7','#2E78C7',0)},rgb(95,163,222) 62%,rgb(125,182,230))`: `linear-gradient(${c1},${c2})`;
  if(mx>0&&mx<1){const [d1,d2]=[ 'rgb(46,120,199)','rgb(95,163,222)']; $('scr1').style.background=`linear-gradient(${d1},${d2})`}
  // month panel reveal 10.0 -> 11.2
  const r=ease(seg(t,10.0,11.2));
  $('mcells').style.gridAutoRows=(130-70*r)+'px';
  document.querySelectorAll('#mcells .chips').forEach(e=>e.style.opacity=cl(1-2*r));
  document.querySelectorAll('#mcells .dots').forEach(e=>e.style.opacity=cl(2*r-1));
  $('mgrid').style.height=(696-0*r)+'px';
  const pn=$('mpanel'); pn.style.opacity=cl(r*1.6); pn.style.top=(140+24+5*(130-70*r)+14)+'px';
  // captions
  const capIn=(a,b)=>out(seg(t,a,a+.6))*(1-seg(t,b-.5,b));
  if(t<2.6) setCap('','',0);
  else if(t<8.6) setCap('Colours that<br>follow the sky',clock(hr),capIn(3.0,8.6));
  else if(t<13.0) setCap('Tap a day.<br>See what&rsquo;s on.','Month at a glance',capIn(9.0,13.0));
  else if(t<17.4) setCap('Made for<br>foldables','Details open beside your calendar',capIn(14.4,17.4));
  else setCap('','',0);
  // fold phone
  const fin=out(seg(t,14.0,15.0)), fout=ease(seg(t,17.4,18.3));
  $('p2').style.top=(640+(1-fin)*1500+fout*1500)+'px';
  const fc=ease(seg(t,15.6,16.6)); $('fcard').style.transform=`translateX(${(1-fc)*110}%)`; $('fcard').style.opacity=cl(fc*2);
  // free lines
  [['f1',18.3],['f2',19.0],['f3',19.7]].forEach(([id,a])=>{const v=out(seg(t,a,a+.5));$(id).style.opacity=v*(1-seg(t,20.9,21.4));$(id).style.transform=`translateY(${(1-v)*30}px)`});
  // end card
  const e=out(seg(t,21.2,22.0)); $('end').style.opacity=e; $('end').style.transform=`translateY(${(1-e)*30}px)`;
}
"""

def html():
    sc = 1.55
    return f'''<html><head><style>{CSS}</style></head><body><div id="stage">
<div id="glow"></div>
<div id="intro"><h1>Skydule</h1><p>A calendar that follows the sky</p></div>
<div id="cap"><h1></h1><p></p></div>
<div class="ph" id="p1" style="top:2400px"><div class="scr" id="scr1" style="width:412px;height:892px;transform:scale({sc});margin-right:{412*(sc-1)}px;margin-bottom:{892*(sc-1)}px">
 <div class="layer" id="L_sched">{S.schedule()}</div><div class="layer" id="L_month" style="opacity:0">{month_new()}</div></div></div>
<div class="ph fold" id="p2" style="top:2400px;border-radius:70px"><div class="scr" style="width:800px;height:760px;transform:scale(1.2);margin-right:160px;margin-bottom:152px;background:linear-gradient(#4A6FA5,#8E86A8 50%,#D9805F)">{fold_screen()}</div></div>
<div id="free"><div id="f1">Free.</div><div id="f2">No ads.</div><div id="f3">No account.</div></div>
<div id="end"><h1>Skydule</h1><p>Coming soon to Google Play</p></div>
</div><script>{JS}</script></body></html>'''

def main(out):
    n = int(FPS * DUR)
    ff = subprocess.Popen(["ffmpeg","-y","-loglevel","error","-f","image2pipe","-framerate",str(FPS),"-i","-","-c:v","libx264","-preset","veryfast","-crf","20","-pix_fmt","yuv420p","-movflags","+faststart",out], stdin=subprocess.PIPE)
    with sync_playwright() as p:
        b = p.chromium.launch(); pg = b.new_page(viewport={"width":1080,"height":1920})
        pg.set_content(html()); pg.wait_for_timeout(500)
        for k in range(n):
            pg.evaluate(f"seek({k/FPS})")
            ff.stdin.write(pg.screenshot(type="jpeg", quality=92))
        b.close()
    ff.stdin.close(); ff.wait()

if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "skydule-promo.mp4")
