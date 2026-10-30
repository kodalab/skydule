"""Mock-up: Month tab where tapping a day opens that day's events in a panel under the grid. Writes /tmp-able PNG."""
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import screens as S
from playwright.sync_api import sync_playwright

def grid(sel, chips):
    d = [27,28,29,30]+list(range(1,32)); cells = ''
    for i,n in enumerate(d):
        dim = i<4; today = (n==29 and not dim); picked = (n==sel and not dim)
        ch = '' if dim else ''.join(f'<div style="height:5px;width:5px;border-radius:50%;background:{c};display:inline-block;margin:0 1.5px"></div>' for c in chips.get(n,[]))
        circ = "background:#fff;color:#12213f;font-weight:700" if today else ("border:2px solid #fff" if picked else "")
        cells += f'<div style="padding:4px 2px;text-align:center;opacity:{.4 if dim else 1}"><div style="width:30px;height:30px;border-radius:50%;margin:0 auto;display:flex;align-items:center;justify-content:center;font-size:14px;{circ}">{n}</div><div style="height:8px;margin-top:3px">{ch}</div></div>'
    hdr = ''.join(f'<div style="text-align:center;font-size:10.5px;opacity:.8">{x}</div>' for x in ["Sun","Mon","Tue","Wed","Thu","Fri","Sat"])
    return f'<div style="position:absolute;left:12px;right:12px;top:112px;height:318px;border-radius:24px;background:rgba(10,30,70,.25);padding:10px 6px"><div style="display:grid;grid-template-columns:repeat(7,1fr);margin-bottom:2px">{hdr}</div><div style="display:grid;grid-template-columns:repeat(7,1fr);grid-auto-rows:50px">{cells}</div></div>'

chips = {1:[S.BLUE],2:[S.ORANGE],6:[S.BLUE],8:[S.BLUE,S.GREEN],13:[S.PURPLE],19:[S.ORANGE],22:[S.BLUE,S.GREEN],24:[S.RED],29:[S.BLUE,S.ORANGE,S.GREEN],30:[S.RED],31:[S.PURPLE]}

def panel(title, link, body):
    return f'''<div style="position:absolute;left:12px;right:12px;top:442px;bottom:84px;border-radius:26px;background:rgba(255,255,255,.17);border:1px solid rgba(255,255,255,.2);padding:14px 16px;overflow:hidden">
<div style="width:36px;height:4px;border-radius:2px;background:rgba(255,255,255,.45);margin:0 auto 10px"></div>
<div style="display:flex;justify-content:space-between;align-items:baseline;margin-bottom:10px"><div style="font-size:19px;font-weight:500">{title}</div><div style="font-size:12.5px;opacity:.85">{link}</div></div>{body}</div>'''

def screen(sel, chips_, panel_html):
    return f'''{S.STATUS}<div style="padding:46px 22px 0"><div class="top" style="justify-content:space-between"><div><div style="font-size:30px;font-weight:300">October</div><div style="font-size:11px;opacity:.8">2026</div></div><span class="pill">Go to today</span></div></div>
{grid(sel, chips_)}{panel_html}<div class="fab" style="bottom:92px">+</div>{S.TABS("Month")}'''

busy = panel("Thursday 29 October", "Open in Schedule ›",
  S.card("Team stand-up","9:00 AM – 9:15 AM",S.BLUE)+S.card("Lunch with Sam","12:30 PM – 1:30 PM",S.ORANGE)+S.card("Yoga","5:30 PM – 6:30 PM",S.GREEN))
quiet = panel("Wednesday 28 October", "", '<div style="text-align:center;padding-top:26px;opacity:.85"><div style="font-size:20px;font-weight:300">Nothing planned</div><div style="font-size:14px;opacity:.7;margin-top:6px">Tap + to add an event</div></div>')

def phone(inner, label):
    return f'<div style="display:flex;flex-direction:column;align-items:center;gap:22px"><div class="phone" style="position:relative;left:auto;top:auto;transform:none"><div class="scr" style="width:412px;height:892px;transform:scale(1);margin:0">{inner}</div></div><div style="font-size:26px;font-weight:600;text-align:center;max-width:470px;line-height:1.3">{label}</div></div>'

css = S.CSS.replace("width:1080px;height:1920px","width:1100px;height:900px")
html = f'<html><head><style>{css}</style></head><body><div style="position:absolute;top:36px;left:0;right:0;text-align:center;font-family:Plex;font-size:50px;font-weight:700">Month, with a day panel</div><div style="position:absolute;top:130px;left:0;right:0;display:flex;justify-content:center;gap:60px;transform-origin:top center;transform:scale(.72)">{phone(screen(29,chips,busy),"Tap a day: its events open in a panel under the grid")}{phone(screen(28,chips,quiet),"A quiet day says so, and + adds an event on it")}</div></body></html>'
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={"width":1100,"height":900}); pg.set_content(html); pg.wait_for_timeout(300)
    pg.screenshot(path=sys.argv[1] if len(sys.argv)>1 else "month_panel_mock.png"); b.close()
