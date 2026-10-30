"""Marketing images for the Play listing: stylised mock-ups of the app's screens with made-up events.
They are illustrations, not captures; real device screenshots should replace them where Play asks for them.
Run from the repo root: python3 tools/marketing/screens.py   (writes store/screenshots/)"""
import os, pathlib
from playwright.sync_api import sync_playwright

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "store" / "screenshots"; OUT.mkdir(parents=True, exist_ok=True)
FONT = (ROOT / "tools/fonts/IBMPlexSerif-Bold.ttf").as_uri()

CSS = f"""
@font-face {{ font-family:'Plex'; src:url('{FONT}'); font-weight:700; }}
*{{box-sizing:border-box;margin:0;padding:0}}
body{{width:1080px;height:1920px;font-family:Inter,Roboto,sans-serif;color:#fff;overflow:hidden;
 background:linear-gradient(170deg,#2E78C7 0%,#4F95D6 55%,#8CC0EC 100%);position:relative}}
.head{{position:absolute;top:120px;left:0;right:0;text-align:center}}
.head h1{{font-family:Plex;font-weight:700;font-size:92px;line-height:1.08;text-shadow:0 4px 18px rgba(10,40,90,.35)}}
.head p{{font-size:38px;opacity:.9;margin-top:22px}}
.sun{{position:absolute;width:260px;height:260px;border-radius:50%;right:-60px;top:-60px;background:radial-gradient(#fff6d8,rgba(255,246,216,0) 70%);opacity:.7}}
.phone{{position:absolute;left:50%;top:480px;transform:translateX(-50%);border-radius:92px;padding:16px;background:#0b0f1a;
 box-shadow:0 40px 90px rgba(5,25,70,.45),0 0 0 3px #3a4256}}
.scr{{transform-origin:0 0;position:relative;overflow:hidden;border-radius:44px;
 background:linear-gradient(#2E78C7,#5FA3DE 62%,#7DB6E6)}}
.fold .scr{{background:linear-gradient(#4A7BB5,#8E86A8 50%,#D9805F)}}
.status{{position:absolute;top:10px;left:24px;right:24px;display:flex;justify-content:space-between;font-size:13px;font-weight:600}}
.glass{{background:rgba(255,255,255,.14);border-radius:22px}}
.small{{font-size:11px;letter-spacing:3px;opacity:.85;font-weight:600}}
.hero{{font-size:52px;font-weight:300;line-height:1.05}}
.pill{{display:inline-flex;gap:10px;align-items:center;padding:8px 14px;border-radius:20px;background:rgba(255,255,255,.18);font-size:14px;font-weight:500}}
.card{{display:flex;gap:16px;align-items:center;padding:17px 22px 17px 18px;border-radius:28px;background:rgba(255,255,255,.17);margin-bottom:10px}}
.bar{{width:5px;height:44px;border-radius:3px;flex:none}}
.t{{font-size:18px;font-weight:500;line-height:1.2}} .s{{font-size:15px;opacity:.75;margin-top:4px}}
.day{{display:flex;gap:10px;margin:0 0 0 0}}
.dn{{width:52px;flex:none;text-align:center;font-size:12px;opacity:.92;padding-top:14px}}
.dn b{{display:flex;width:38px;height:38px;border-radius:50%;margin:4px auto 0;align-items:center;justify-content:center;font-size:20px;font-weight:600}}
.div{{height:3px;border-radius:2px;background:rgba(255,255,255,.28);margin:10px 0 12px}}
.ico{{width:22px;height:22px;display:block;margin-right:10px;fill:#fff}}
.dn b.today{{background:#fff;color:#12213f}}
.tabs{{position:absolute;left:22px;right:22px;bottom:18px;height:62px;border-radius:31px;background:rgba(20,34,70,.62);border:1.5px solid rgba(255,255,255,.18);
 display:flex;align-items:center;justify-content:space-around;font-size:15px;font-weight:500;padding:0 6px}}
.tabs span{{display:flex;align-items:center;padding:0 15px;height:48px;border-radius:24px}}
.tabs .on{{background:rgba(255,255,255,.22);border:1.5px solid rgba(255,255,255,.35);font-weight:600}}
.fab{{position:absolute;right:22px;bottom:92px;width:56px;height:56px;border-radius:20px;background:#fff;color:#12213f;font-size:32px;text-align:center;line-height:50px;box-shadow:0 6px 16px rgba(0,0,0,.25)}}
.top{{display:flex;justify-content:flex-end;gap:12px;align-items:center;margin-bottom:8px}}
"""

BLUE, ORANGE, GREEN, RED, PURPLE = "#4285F4", "#F6893D", "#33B679", "#E67C73", "#9C5BD6"
def card(title, sub, col): return f'<div class="card"><div class="bar" style="background:{col}"></div><div><div class="t">{title}</div><div class="s">{sub}</div></div></div>'
def day(dow, n, cards, today=False, last=False):
    return f'<div class="day"><div class="dn">{dow}<b class="{"today" if today else ""}">{n}</b></div><div style="flex:1">{cards}</div></div>' + ('' if last else '<div class="div"></div>')
ICONS = {
 "Schedule": '<svg class="ico" viewBox="0 0 24 24"><rect x="3" y="5" width="3" height="3" rx="1"/><rect x="8" y="5" width="13" height="3" rx="1.5"/><rect x="3" y="10.5" width="3" height="3" rx="1"/><rect x="8" y="10.5" width="13" height="3" rx="1.5"/><rect x="3" y="16" width="3" height="3" rx="1"/><rect x="8" y="16" width="13" height="3" rx="1.5"/></svg>',
 "Week": '<svg class="ico" viewBox="0 0 24 24"><rect x="4" y="10" width="4" height="9" rx="1"/><rect x="10" y="5" width="4" height="9" rx="1"/><rect x="16" y="3" width="4" height="6" rx="1"/></svg>',
 "Month": '<svg class="ico" viewBox="0 0 24 24">' + ''.join(f'<circle cx="{x}" cy="{y}" r="1.9"/>' for x in (5,12,19) for y in (5,12,19)) + '</svg>',
}
def tab_items(on): return ''.join(f'<span class="{"on" if n==on else ""}">{ICONS[n]}{n}</span>' for n in ["Schedule","Week","Month"])
TABS = lambda on: '<div class="tabs">' + tab_items(on) + '</div>'
STATUS = '<div class="status"><span>9:41</span><span>● ▮</span></div>'

def schedule():
    return f'''{STATUS}<div style="padding:46px 22px 0">
<div class="top"><span class="pill">Go to today</span><span style="font-size:22px">⚙</span></div>
<div class="small">TODAY</div><div class="hero">Thursday</div>
<div style="margin:12px 0 8px"><span class="pill" style="font-size:16px;padding:9px 18px">29 Oct 2026 &nbsp;<span style="opacity:.4">|</span>&nbsp; ☀ 14° &nbsp;Sunny</span></div>
<div style="font-size:15px;opacity:.8;margin-bottom:26px">3 events today</div>
<div style="font-size:16px;font-weight:500;opacity:.9;margin-bottom:6px">October 2026</div>
{day("Thu",29,card("Team stand-up","9:00 AM – 9:15 AM",BLUE)+card("Lunch with Sam","12:30 PM – 1:30 PM",ORANGE)+card("Yoga","5:30 PM – 6:30 PM",GREEN),True)}
{day("Fri",30,card("Dentist","10:00 AM – 10:45 AM",RED))}
{day("Sat",31,card("Halloween party","All day",PURPLE),last=True)}
</div><div class="fab">+</div>{TABS("Schedule")}'''

def week():
    hrs = range(8, 19)
    ev = [(1,9,10,"Stand-up",BLUE),(2,13,15,"Design review",PURPLE),(3,9,10,"Stand-up",BLUE),(3,12.5,13.5,"Lunch with Sam",ORANGE),(3,17.5,18.5,"Yoga",GREEN),
          (4,10,10.75,"Dentist",RED),(4,14,16,"Workshop",BLUE),(5,9,10,"Stand-up",BLUE),(6,10,12,"Farmers market",GREEN)]
    top0, hh = 160, 56
    lines = ''.join(f'<div style="position:absolute;left:46px;right:0;top:{top0+(h-8)*hh}px;border-top:1px solid rgba(255,255,255,.14)"><span style="position:absolute;left:-40px;top:-8px;font-size:10.5px;opacity:.7">{h if h<=12 else h-12} {"AM" if h<12 else "PM"}</span></div>' for h in hrs)
    colw = (412-46-8)/7
    cols = ''.join(f'<div style="position:absolute;left:{46+i*colw}px;width:{colw}px;top:104px;text-align:center;font-size:10.5px;opacity:.85">{d}<br><b style="display:inline-flex;width:26px;height:26px;border-radius:50%;align-items:center;justify-content:center;font-size:13px;margin-top:2px;{"background:#fff;color:#12213f" if n==29 else ""}">{n}</b></div>' for i,(d,n) in enumerate(zip("SMTWTFS",range(25,32))))
    evs = ''.join(f'<div style="position:absolute;left:{46+c*colw+1.5}px;width:{colw-3}px;top:{top0+(a-8)*hh}px;height:{(b-a)*hh-2}px;background:{col};border-radius:7px;padding:4px 5px;font-size:9.5px;font-weight:600;line-height:1.15;overflow:hidden">{n}</div>' for c,a,b,n,col in ev)
    return f'''{STATUS}<div style="padding:46px 22px 0"><div class="top" style="justify-content:space-between"><div><div style="font-size:26px;font-weight:300">25 Oct – 31 Oct</div><div style="font-size:11px;opacity:.8">2026</div></div><span class="pill">Go to today</span></div></div>
<div style="position:absolute;left:12px;right:12px;top:98px;bottom:84px;border-radius:24px;background:rgba(10,30,70,.25)"></div>{cols}{lines}{evs}{TABS("Week")}'''

def month():
    d = [27,28,29,30]+list(range(1,32)); cells=''
    chips = {1:[BLUE],2:[ORANGE],6:[BLUE],8:[BLUE,GREEN],9:[BLUE],13:[PURPLE],14:[BLUE],16:[BLUE],19:[ORANGE],22:[BLUE,GREEN],24:[RED],27:[BLUE],29:[BLUE,ORANGE,GREEN],30:[RED,ORANGE],31:[PURPLE]}
    for i,n in enumerate(d):
        dim = i<4; today = (n==29 and not dim)
        ch = '' if dim else ''.join(f'<div style="height:7px;border-radius:3px;background:{c};margin-top:3px"></div>' for c in chips.get(n,[]))
        cells += f'<div style="padding:7px 5px;opacity:{.4 if dim else 1}"><div style="width:26px;height:26px;border-radius:50%;margin:0 auto;display:flex;align-items:center;justify-content:center;font-size:13px;{"background:#fff;color:#12213f;font-weight:700" if today else ""}">{n}</div>{ch}</div>'
    hdr = ''.join(f'<div style="text-align:center;font-size:10.5px;opacity:.8">{x}</div>' for x in ["Sun","Mon","Tue","Wed","Thu","Fri","Sat"])
    return f'''{STATUS}<div style="padding:46px 22px 0"><div class="top" style="justify-content:space-between"><div><div style="font-size:30px;font-weight:300">October</div><div style="font-size:11px;opacity:.8">2026</div></div><span class="pill">Go to today</span></div></div>
<div style="position:absolute;left:12px;right:12px;top:112px;bottom:84px;border-radius:24px;background:rgba(10,30,70,.25);padding:12px 6px">
<div style="display:grid;grid-template-columns:repeat(7,1fr);margin-bottom:4px">{hdr}</div><div style="display:grid;grid-template-columns:repeat(7,1fr);grid-auto-rows:122px">{cells}</div></div>{TABS("Month")}'''

def fold():
    left = f'''<div style="position:absolute;left:0;top:0;width:392px;bottom:0;padding:50px 24px 0">
<div class="small">TODAY</div><div class="hero" style="font-size:46px">Thursday</div>
<div style="margin:10px 0 18px"><span class="pill">Oct 29, 2026 &nbsp;|&nbsp; ☀ 14°</span></div>
{day("Thu",29,card("Team stand-up","9:00 AM – 9:15 AM",BLUE)+card("Lunch with Sam","12:30 PM – 1:30 PM",ORANGE)+card("Yoga","5:30 PM – 6:30 PM",GREEN),True)}
{day("Fri",30,card("Dentist","10:00 AM – 10:45 AM",RED))}</div>
<div class="tabs" style="right:auto;width:362px;left:16px;font-size:13px">{tab_items("Schedule")}</div>'''
    right = f'''<div class="glass" style="position:absolute;left:412px;right:14px;top:42px;bottom:22px;padding:20px 22px;background:rgba(255,255,255,.16);border:1px solid rgba(255,255,255,.2)">
<div style="font-size:22px;margin-bottom:26px">✕</div>
<div style="display:flex;gap:16px"><div style="width:14px;height:14px;border-radius:4px;background:{ORANGE};margin-top:10px"></div><div><div style="font-size:30px;font-weight:400;line-height:1.15">Lunch with Sam</div><div style="font-size:14px;opacity:.85;margin-top:6px">Thursday 29 Oct • 12:30 PM – 1:30 PM</div></div></div>
<div style="margin-top:30px;display:flex;gap:16px;font-size:16px">📍<div>Café Nero, High Street<div style="font-size:12px;opacity:.6">Open in Maps</div></div></div>
<div style="margin-top:24px;display:flex;gap:16px;font-size:16px">📅<div>Personal</div></div>
<div style="margin-top:24px;display:flex;gap:16px;font-size:15px;line-height:1.4">☰<div>Catch up on the weekend. Bring the photos from the trip.</div></div></div>'''
    return f'{STATUS}{left}{right}<div class="fab" style="right:auto;left:312px;bottom:90px">+</div>'

SHOTS = [
 ("1-schedule", "Your day,\nat a glance", "Everything on your calendar, in one calm list", schedule, 412, 892, False),
 ("2-week", "Plan your week\nby the hour", "Events laid out side by side", week, 412, 892, False),
 ("3-month", "See the whole\nmonth", "Tap any day to jump straight in", month, 412, 892, False),
 ("4-foldable", "Made for\nfoldables", "Details open beside your calendar", fold, 800, 760, True),
]

def main():
    with sync_playwright() as p:
        b = p.chromium.launch(); pg = b.new_page(viewport={"width":1080,"height":1920})
        for name, h1, sub, fn, w, h, isfold in SHOTS:
            scale = 1.55 if not isfold else 1.2
            html = f'''<html><head><style>{CSS}</style></head><body><div class="sun"></div>
    <div class="head"><h1>{h1.replace(chr(10),"<br>")}</h1><p>{sub}</p></div>
    <div class="phone {"fold" if isfold else ""}" style="border-radius:{70 if isfold else 92}px;top:{520 if isfold else 470}px"><div class="scr" style="width:{w}px;height:{h}px;transform:scale({scale});margin-right:{w*(scale-1)}px;margin-bottom:{h*(scale-1)}px">{fn()}</div></div></body></html>'''
            pg.set_content(html); pg.wait_for_timeout(300)
            pg.screenshot(path=str(OUT / f"{name}.png"))
        b.close()
    print("done", sorted(os.listdir(OUT)))

if __name__ == '__main__':
    main()
