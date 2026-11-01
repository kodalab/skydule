"""Mock-up: Settings > Calendars (show/hide toggle + colour per calendar)."""
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import screens as S
from playwright.sync_api import sync_playwright

CALS = [("Personal","greg@gmail.com","#4285F4",True),("Family","greg@gmail.com","#33B679",True),("Work","greg@kodalabs.com","#F6893D",True),
        ("Birthdays","Google","#9C5BD6",False),("Holidays in United Kingdom","Google","#E67C73",True),("Bins","On this phone","#7A8AA0",True)]
PAL = ["#E91E63","#EC407A","#F44336","#EF6C57","#E53935","#FB8C00","#FF9800","#FFB300","#FDD835","#C0CA33","#7CB342","#26A65B","#00A98F","#2E9E6B","#26C6DA","#1E88E5","#3F7BE0","#5C6BC0","#9575CD","#AB47BC","#8E24AA","#8D6E63","#9E9E9E","#C2A27C"]

def toggle(on): 
    return f'<div style="width:46px;height:28px;border-radius:14px;background:{"#7FB6FF" if on else "rgba(255,255,255,.22)"};position:relative"><div style="position:absolute;top:3px;{"right" if on else "left"}:3px;width:22px;height:22px;border-radius:50%;background:#fff"></div></div>'

def row(i, c, pop=None):
    n,a,col,on = c
    dot = f'<div style="width:26px;height:26px;border-radius:50%;background:{col};flex:none;{"outline:2px solid #fff;outline-offset:2px" if pop==i else ""}"></div>'
    return f'<div style="display:flex;align-items:center;gap:14px;padding:13px 4px;{"opacity:.55;" if not on else ""}border-top:{0 if i==0 else 1}px solid rgba(255,255,255,.08)">{dot}<div style="flex:1;min-width:0"><div style="font-size:16px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{n}</div><div style="font-size:12px;opacity:.6">{a}</div></div>{toggle(on)}</div>'

def srow(label, value="", first=False, sub=None):
    subh = f'<div style="font-size:12px;opacity:.6;margin-top:3px">{sub}</div>' if sub else ""
    return f'<div style="display:flex;align-items:center;justify-content:space-between;padding:16px 4px;border-top:{0 if first else 1}px solid rgba(255,255,255,.08)"><div><div style="font-size:17px">{label}</div>{subh}</div><div style="display:flex;gap:10px;align-items:center;opacity:.65;font-size:14px">{value}<span style="font-size:20px">›</span></div></div>'

def settings_main():
    return f'''<div style="position:absolute;inset:0;background:linear-gradient(#0B1026,#141A3D)"></div>{S.STATUS}
<div style="position:absolute;left:0;right:0;top:46px;padding:0 22px"><div style="font-size:30px;font-weight:300;margin-bottom:16px">← &nbsp;Settings</div>
<div class="small" style="margin:6px 4px 8px">CALENDARS</div>
<div style="border-radius:24px;background:rgba(255,255,255,.07);padding:2px 16px">{srow("Calendar settings","5 of 6 shown",True)}</div>
<div class="small" style="margin:22px 4px 8px">APPEARANCE</div>
<div style="border-radius:24px;background:rgba(255,255,255,.07);padding:2px 16px">{srow("Theme","Sky (follows the time of day)",True)}{srow("Week starts on","Automatic (by region)")}{srow("App icon","Dynamic")}</div>
<div class="small" style="margin:22px 4px 8px">SCHEDULE</div>
<div style="border-radius:24px;background:rgba(255,255,255,.07);padding:2px 16px">{srow("Show weather","",True,"Needs location")}{srow("Default alert","10 minutes before")}</div></div>'''

def group(title, sub, items, startIdx, pop):
    rows = ''.join(row(startIdx+k, c, pop) for k,c in enumerate(items))
    return (f'<div style="display:flex;justify-content:space-between;align-items:center;margin:14px 6px 6px"><div><div style="font-size:15px;font-weight:600">{title}</div><div style="font-size:11.5px;opacity:.6">{sub}</div></div></div>'
            f'<div style="border-radius:24px;background:rgba(255,255,255,.07);padding:2px 16px">{rows}</div>')

def screen(pop=None):
    g = CALS
    body = group("Google", "greg@gmail.com", g[0:2], 0, pop) + group("Google", "greg@kodalabs.com", g[2:3], 2, pop) + group("Google", "Subscribed", g[3:5], 3, pop) + group("On this phone", "", g[5:6], 5, pop)
    popover = ''
    if pop is not None:
        sw = ''.join(f'<div style="width:30px;height:30px;border-radius:50%;background:{c};{"outline:2px solid #fff;outline-offset:2px" if c=="#FF9800" else ""}"></div>' for c in PAL)
        wheel = '<div style="width:30px;height:30px;border-radius:50%;background:conic-gradient(#f44,#fb0,#6c3,#2cc,#46f,#c4c,#f44)"></div>'
        popover = f'<div style="position:absolute;left:22px;right:22px;top:{190+pop*60}px;padding:16px;border-radius:24px;background:#2a2f4a;box-shadow:0 12px 30px rgba(0,0,0,.5);display:grid;grid-template-columns:repeat(6,1fr);gap:14px;justify-items:center">{sw}{wheel}<div style="grid-column:2/-1;font-size:12px;opacity:.7;justify-self:start;align-self:center">Custom colour</div></div>'
    return f'''<div style="position:absolute;inset:0;background:linear-gradient(#0B1026,#141A3D)"></div>{S.STATUS}
<div style="position:absolute;left:0;right:0;top:46px;padding:0 22px"><div style="font-size:28px;font-weight:300;margin-bottom:4px">← &nbsp;Calendar settings</div>{body}
<div style="font-size:12px;opacity:.55;margin:10px 6px 0">Hiding or recolouring only affects Skydule, not your other apps.</div></div>{popover}'''

def custom():
    spec = '<div style="height:150px;border-radius:12px;background:linear-gradient(to bottom,rgba(255,255,255,0),#000),linear-gradient(to right,#fff,#f90)"></div>'
    hue = '<div style="height:18px;border-radius:9px;background:linear-gradient(to right,#f00,#ff0,#0f0,#0ff,#00f,#f0f,#f00);margin-top:14px;position:relative"><div style="position:absolute;left:12%;top:-3px;width:24px;height:24px;border-radius:50%;background:#fff;box-shadow:0 1px 4px rgba(0,0,0,.5)"></div></div>'
    recent = ''.join(f'<div style="width:28px;height:28px;border-radius:50%;background:{c}"></div>' for c in ["#FF9800","#2F63DD","#26A65B","#AB47BC","#EF5350"])
    dlg = f'<div style="position:absolute;left:20px;right:20px;top:200px;padding:20px;border-radius:26px;background:#2a2f4a;box-shadow:0 14px 34px rgba(0,0,0,.55)"><div style="font-size:19px;margin-bottom:12px">Custom colour: Work</div>{spec}{hue}<div style="display:flex;gap:12px;align-items:center;margin-top:16px"><div style="width:44px;height:44px;border-radius:12px;background:#FF9800"></div><div style="flex:1;border-radius:10px;background:rgba(255,255,255,.1);padding:10px 12px;font-size:15px">#FF9800</div></div><div style="font-size:12px;opacity:.6;margin:16px 0 8px">Recently used</div><div style="display:flex;gap:12px">{recent}</div><div style="display:flex;justify-content:flex-end;gap:26px;margin-top:20px;font-size:16px"><span style="opacity:.8">Cancel</span><span style="color:#7FB6FF;font-weight:600">Done</span></div></div>'
    return screen(None) + '<div style="position:absolute;inset:0;background:rgba(0,0,0,.45)"></div>' + dlg

def phone(inner, label):
    return f'<div style="display:flex;flex-direction:column;align-items:center;gap:22px"><div class="phone" style="position:relative;left:auto;top:auto;transform:none"><div class="scr" style="width:412px;height:892px;transform:scale(1);margin:0">{inner}</div></div><div style="font-size:26px;font-weight:600;text-align:center;max-width:470px;line-height:1.3">{label}</div></div>'

css = S.CSS.replace("width:1080px;height:1920px","width:1100px;height:900px")
html = f'<html><head><style>{css}</style></head><body><div style="position:absolute;top:36px;left:0;right:0;text-align:center;font-family:Plex;font-size:50px;font-weight:700">More colours, and your own</div><div style="position:absolute;top:130px;left:0;right:0;display:flex;justify-content:center;gap:60px;transform-origin:top center;transform:scale(.72)">{phone(screen(2),"Grouped by account. Tap a dot: 24 colours, plus a colour wheel for custom")}{phone(custom(),"Custom colour: spectrum, hex and recently used")}</div></body></html>'
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={"width":1100,"height":900}); pg.set_content(html); pg.wait_for_timeout(300)
    pg.screenshot(path=sys.argv[1]); b.close()
