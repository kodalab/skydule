"""Mock-up: Settings > Calendars (show/hide toggle + colour per calendar)."""
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import screens as S
from playwright.sync_api import sync_playwright

CALS = [("Personal","greg@gmail.com","#4285F4",True),("Family","greg@gmail.com","#33B679",True),("Work","greg@kodalabs.com","#F6893D",True),
        ("Birthdays","Google","#9C5BD6",False),("Holidays in United Kingdom","Google","#E67C73",True),("Bins","On this phone","#7A8AA0",True)]
PAL = ["#4DB6E5","#EF5350","#F06292","#FB8C00","#FBC02D","#2BCB8F","#5BC21A","#26C6C6","#6C9EF0","#2F63DD","#9C6CF0","#9E9E9E"]

def toggle(on): 
    return f'<div style="width:46px;height:28px;border-radius:14px;background:{"#7FB6FF" if on else "rgba(255,255,255,.22)"};position:relative"><div style="position:absolute;top:3px;{"right" if on else "left"}:3px;width:22px;height:22px;border-radius:50%;background:#fff"></div></div>'

def row(i, c, pop=None):
    n,a,col,on = c
    dot = f'<div style="width:26px;height:26px;border-radius:50%;background:{col};flex:none;{"outline:2px solid #fff;outline-offset:2px" if pop==i else ""}"></div>'
    return f'<div style="display:flex;align-items:center;gap:14px;padding:13px 4px;{"opacity:.55;" if not on else ""}border-top:{0 if i==0 else 1}px solid rgba(255,255,255,.08)">{dot}<div style="flex:1;min-width:0"><div style="font-size:16px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{n}</div><div style="font-size:12px;opacity:.6">{a}</div></div>{toggle(on)}</div>'

def screen(pop=None):
    rows = ''.join(row(i,c,pop) for i,c in enumerate(CALS))
    popover = ''
    if pop is not None:
        dots = ''.join(f'<div style="width:34px;height:34px;border-radius:50%;background:{c};{"outline:2px solid #fff;outline-offset:2px" if c=="#FB8C00" else ""}"></div>' for k,c in enumerate(PAL))
        popover = f'<div style="position:absolute;left:24px;right:24px;top:{150+pop*66+56}px;padding:18px;border-radius:24px;background:#2a2f4a;box-shadow:0 12px 30px rgba(0,0,0,.45);display:grid;grid-template-columns:repeat(6,1fr);gap:14px;justify-items:center"><div style="grid-column:1/-1;font-size:13px;opacity:.7;justify-self:start">Colour in Skydule</div>{dots}<div style="grid-column:1/-1;font-size:12px;opacity:.55;justify-self:start;margin-top:2px">Only changes how it looks here, not in other apps.</div></div>'
    return f'''<div style="position:absolute;inset:0;background:linear-gradient(#0B1026,#141A3D)"></div>{S.STATUS}
<div style="position:absolute;left:0;right:0;top:46px;padding:0 22px"><div style="font-size:30px;font-weight:300;margin-bottom:16px">← &nbsp;Settings</div>
<div class="small" style="margin:6px 4px 8px">CALENDARS</div>
<div style="border-radius:24px;background:rgba(255,255,255,.07);padding:4px 16px">{rows}</div>
<div style="font-size:12px;opacity:.55;margin:8px 6px 0">Hidden calendars are only hidden in Skydule.</div>
<div class="small" style="margin:20px 4px 8px">APPEARANCE</div>
<div style="border-radius:24px;background:rgba(255,255,255,.07);padding:15px 20px;display:flex;justify-content:space-between;font-size:16px"><span>Theme</span><span style="opacity:.6">Sky (follows the time of day) ›</span></div></div>{popover}'''

def phone(inner, label):
    return f'<div style="display:flex;flex-direction:column;align-items:center;gap:22px"><div class="phone" style="position:relative;left:auto;top:auto;transform:none"><div class="scr" style="width:412px;height:892px;transform:scale(1);margin:0">{inner}</div></div><div style="font-size:26px;font-weight:600;text-align:center;max-width:470px;line-height:1.3">{label}</div></div>'

css = S.CSS.replace("width:1080px;height:1920px","width:1100px;height:900px")
html = f'<html><head><style>{css}</style></head><body><div style="position:absolute;top:36px;left:0;right:0;text-align:center;font-family:Plex;font-size:50px;font-weight:700">Calendars, at the top of Settings</div><div style="position:absolute;top:130px;left:0;right:0;display:flex;justify-content:center;gap:60px;transform-origin:top center;transform:scale(.72)">{phone(screen(),"A toggle shows or hides each calendar. Tap the dot to change its colour")}{phone(screen(2),"The colour picker, like the one you showed me")}</div></body></html>'
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={"width":1100,"height":900}); pg.set_content(html); pg.wait_for_timeout(300)
    pg.screenshot(path=sys.argv[1]); b.close()
