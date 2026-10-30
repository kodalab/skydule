"""Mock-up: full month shows titled chips in each day; they melt into dots when the day panel opens."""
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import screens as S
from playwright.sync_api import sync_playwright

LIGHT = "#8ED3E6"   # a pale calendar colour: dark text on it
EV = {  # day -> [(title, colour)]
 1:[("Payday",LIGHT),("Standup",S.BLUE)], 2:[("Yoga",S.GREEN)], 3:[("Football",S.GREEN)],
 5:[("Swimming",S.GREEN),("Parents ev",S.BLUE)], 6:[("Dentist",S.RED)], 7:[("Gym",S.GREEN),("Call Sam",LIGHT)],
 8:[("Jones - S",S.GREEN),("Gym",S.GREEN),("Rent",LIGHT)], 9:[("Swimming",S.GREEN),("Gym",S.GREEN),("Lunch",S.ORANGE),("Dinner",S.ORANGE),("Bins",S.BLUE)],
 10:[("Brunch",S.ORANGE),("Gym",S.GREEN)], 12:[("Football",S.GREEN)], 15:[("Review",S.BLUE)], 17:[("Swimming",S.GREEN),("Sam",S.ORANGE)],
 19:[("Yoga",S.GREEN)], 22:[("Swimming",S.GREEN)], 24:[("Football",S.GREEN)], 28:[("Payday",LIGHT)], 29:[("Lucy bday",S.ORANGE),("Black bin",S.GREEN)],
 30:[("Quiz",S.BLUE)], 31:[("Halloween","#B0B0B0"),("Swimming",S.GREEN),("Beth+Paul",S.GREEN),("Cinema",S.PURPLE)],
}
def txt(c): return "#0E1A33" if c in (LIGHT,"#B0B0B0") else "#fff"

def cell(n, dim, today, sel, mode, rowh):
    evs = [] if dim else EV.get(n, [])
    if mode == "chips":
        shown = evs[:3]; more = len(evs) - 3
        body = ''.join(f'<div style="height:15px;border-radius:5px;background:{c};color:{txt(c)};font-size:9.5px;font-weight:600;line-height:15px;padding:0 4px;margin-top:3px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{t}</div>' for t,c in shown)
        if more > 0: body += f'<div style="font-size:9.5px;opacity:.85;margin-top:3px;padding-left:3px">+{more}</div>'
    elif mode == "mid":   # chips squeezing into thin bars, dots arriving
        body = ''.join(f'<div style="height:6px;border-radius:3px;background:{c};margin-top:3px;opacity:.9"></div>' for t,c in evs[:3])
    else:
        body = '<div style="height:8px;margin-top:3px;text-align:center">' + ''.join(f'<div style="height:5px;width:5px;border-radius:50%;background:{c};display:inline-block;margin:0 1.5px"></div>' for t,c in evs[:3]) + '</div>'
    circ = "background:#fff;color:#12213f;font-weight:700" if today else ("border:2px solid #fff" if sel else "")
    return f'<div style="padding:5px 2px;opacity:{.4 if dim else 1};overflow:hidden;height:{rowh}px"><div style="width:26px;height:26px;border-radius:50%;margin:0 auto 1px;display:flex;align-items:center;justify-content:center;font-size:13px;{circ}">{n}</div>{body}</div>'

def grid(mode, rowh, top=112, height=None, sel=None):
    d = [28,29,30]+list(range(1,32))   # Mon start; Oct 1 is Thu
    cells = ''.join(cell(n, i<3, (n==30 and i>=3), (n==sel and i>=3), mode, rowh) for i,n in enumerate(d))
    cells += ''.join(cell(n, True, False, False, mode, rowh) for n in (1,))
    hdr = ''.join(f'<div style="text-align:center;font-size:10.5px;opacity:.8">{x}</div>' for x in ["Mon","Tue","Wed","Thu","Fri","Sat","Sun"])
    h = f"height:{height}px;" if height else ""
    return f'<div style="position:absolute;left:12px;right:12px;top:{top}px;{h}border-radius:24px;background:rgba(10,30,70,.25);padding:10px 4px"><div style="display:grid;grid-template-columns:repeat(7,1fr);margin-bottom:2px">{hdr}</div><div style="display:grid;grid-template-columns:repeat(7,1fr);grid-auto-rows:{rowh}px">{cells}</div></div>'

def head(): return f'''{S.STATUS}<div style="padding:46px 22px 0"><div class="top" style="justify-content:space-between"><div><div style="font-size:30px;font-weight:300">October</div><div style="font-size:11px;opacity:.8">2026</div></div><span class="pill">Go to today</span></div></div>'''
def panel(top, op=1):
    plus = '<div style="width:40px;height:40px;border-radius:14px;background:#fff;color:#12213f;font-size:26px;text-align:center;line-height:37px">+</div>'
    body = S.card("Halloween","All day","#B0B0B0") + S.card("Swimming","13:30 – 14:30",S.GREEN)
    return f'<div style="position:absolute;left:12px;right:12px;top:{top}px;bottom:84px;border-radius:26px;background:rgba(255,255,255,.17);border:1px solid rgba(255,255,255,.2);padding:14px 16px;overflow:hidden;opacity:{op}"><div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px"><div style="font-size:19px;font-weight:500">Saturday 31 October</div>{plus}</div>{body}</div>'

full  = head() + grid("chips", 118, height=660) + '<div class="fab" style="bottom:92px;right:22px">+</div>' + S.TABS("Month")
mid   = head() + grid("mid", 76, height=470, sel=31) + panel(600, .55) + '<div class="fab" style="bottom:92px;right:22px;opacity:.4;transform:scale(.7)">+</div>' + S.TABS("Month")
opened= head() + grid("dots", 50, height=318, sel=31) + panel(442) + S.TABS("Month")

def phone(inner, label):
    return f'<div style="display:flex;flex-direction:column;align-items:center;gap:22px"><div class="phone" style="position:relative;left:auto;top:auto;transform:none"><div class="scr" style="width:412px;height:892px;transform:scale(1);margin:0">{inner}</div></div><div style="font-size:26px;font-weight:600;text-align:center;max-width:470px;line-height:1.3">{label}</div></div>'

css = S.CSS.replace("width:1080px;height:1920px","width:1500px;height:900px")
html = f'<html><head><style>{css}</style></head><body><div style="position:absolute;top:34px;left:0;right:0;text-align:center;font-family:Plex;font-size:50px;font-weight:700">Month: titles when roomy, dots when the panel opens</div><div style="position:absolute;top:128px;left:0;right:0;display:flex;justify-content:center;gap:40px;transform-origin:top center;transform:scale(.72)">{phone(full,"1. Full month: titled chips, up to three, then +N")}{phone(mid,"2. Tap a day: cells shrink, chips squeeze toward dots")}{phone(opened,"3. Panel open: dots, and the + moves into the panel")}</div></body></html>'
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={"width":1500,"height":900}); pg.set_content(html); pg.wait_for_timeout(300)
    pg.screenshot(path=sys.argv[1] if len(sys.argv)>1 else "month_chips_mock.png"); b.close()
