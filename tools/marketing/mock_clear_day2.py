"""Mock-up: empty today collapses to a compact row with a dash."""
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import screens as S
from playwright.sync_api import sync_playwright

def screen(kind):
    line = "Nothing planned today"
    style = "font-size:15px;opacity:.8"
    return f'''{S.STATUS}<div style="padding:46px 22px 0">
<div class="top"><span class="pill">Go to today</span><span style="font-size:22px">⚙</span></div>
<div class="small">TODAY</div><div class="hero">Friday</div>
<div style="margin:12px 0 8px"><span class="pill" style="font-size:16px;padding:9px 18px">30 Oct 2026 &nbsp;<span style="opacity:.4">|</span>&nbsp; ☀ 15° &nbsp;Clear</span></div>
<div style="{style};margin-bottom:26px">{line}</div>
{S.day("Thu",29,S.card("Lucy James birthday","All day","#9AD7E6")+S.card("Black bin","All day",S.GREEN))}
{S.day("Fri",30,"<div style='height:30px'></div>",True) if kind=="now" else compact()}
{S.day("Sat",31,S.card("Halloween","All day","#B0B0B0")+S.card("Jones - Swimming","13:30 – 14:30",S.GREEN),last=True)}
</div><div class="fab">+</div>{S.TABS("Schedule")}'''

def compact():
    return '''<div style="display:flex;align-items:center;gap:10px;height:44px"><div style="width:52px;flex:none;display:flex;align-items:center;justify-content:center;gap:6px"><span style="font-size:12px;opacity:.92">Fri</span><b style="display:flex;width:30px;height:30px;border-radius:50%;background:#fff;color:#12213f;align-items:center;justify-content:center;font-size:16px;font-weight:600">30</b></div><div style="flex:1;height:2px;border-radius:1px;background:rgba(255,255,255,.35);max-width:26px"></div></div><div class="div"></div>'''

def phone(inner, label):
    return f'<div style="display:flex;flex-direction:column;align-items:center;gap:22px"><div class="phone" style="position:relative;left:auto;top:auto;transform:none"><div class="scr" style="width:412px;height:892px;transform:scale(1);margin:0">{inner}</div></div><div style="font-size:26px;font-weight:600;text-align:center;max-width:470px;line-height:1.3">{label}</div></div>'

css = S.CSS.replace("width:1080px;height:1920px","width:1100px;height:900px")
html = f'<html><head><style>{css}</style></head><body><div style="position:absolute;top:36px;left:0;right:0;text-align:center;font-family:Plex;font-size:50px;font-weight:700">Collapsing an empty day</div><div style="position:absolute;top:130px;left:0;right:0;display:flex;justify-content:center;gap:60px;transform-origin:top center;transform:scale(.72)">{phone(screen("now"),"Today: a tall empty row")}{phone(screen("new"),"Proposed: collapsed to one slim row, a dash for nothing planned")}</div></body></html>'
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={"width":1100,"height":900}); pg.set_content(html); pg.wait_for_timeout(300)
    pg.screenshot(path=sys.argv[1] if len(sys.argv)>1 else "clear_day_mock.png"); b.close()
