"""Mock-up: the add button moves into the Month day panel's header while the panel is open."""
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import screens as S
import mock_month_panel as M  # reuses grid(); importing renders its own image, which is harmless
from playwright.sync_api import sync_playwright

def screen(kind):
    base = f'''{S.STATUS}<div style="padding:46px 22px 0"><div class="top" style="justify-content:space-between"><div><div style="font-size:30px;font-weight:300">October</div><div style="font-size:11px;opacity:.8">2026</div></div><span class="pill">Go to today</span></div></div>'''
    if kind == "closed":
        g = M.grid(0, M.chips).replace("height:318px","height:660px").replace("grid-auto-rows:50px","grid-auto-rows:118px")
        return base + g + '<div class="fab" style="bottom:92px">+</div>' + S.TABS("Month")
    plus = '<div style="width:38px;height:38px;border-radius:13px;background:#fff;color:#12213f;font-size:26px;text-align:center;line-height:35px;box-shadow:0 3px 8px rgba(0,0,0,.2)">+</div>'
    title = "Saturday 31 October"
    body = S.card("Halloween","All day","#B0B0B0") + S.card("Jones - Swimming","13:30 – 14:30",S.GREEN) + S.card("Beth and Paul over","16:00 – 21:00",S.GREEN)
    fade = '<div style="position:absolute;left:0;right:0;bottom:0;height:46px;background:linear-gradient(rgba(122,166,214,0),rgba(122,166,214,.95))"></div>'
    panel = f'''<div style="position:absolute;left:12px;right:12px;top:442px;bottom:84px;border-radius:26px;background:rgba(255,255,255,.17);border:1px solid rgba(255,255,255,.2);padding:14px 16px;overflow:hidden">
<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px"><div style="font-size:19px;font-weight:500">{title}</div>{plus}</div>{body}{fade}</div>'''
    return base + M.grid(31, M.chips) + panel + S.TABS("Month")

def phone(inner, label):
    return f'<div style="display:flex;flex-direction:column;align-items:center;gap:22px"><div class="phone" style="position:relative;left:auto;top:auto;transform:none"><div class="scr" style="width:412px;height:892px;transform:scale(1);margin:0">{inner}</div></div><div style="font-size:26px;font-weight:600;text-align:center;max-width:470px;line-height:1.3">{label}</div></div>'

css = S.CSS.replace("width:1080px;height:1920px","width:1100px;height:900px")
html = f'<html><head><style>{css}</style></head><body><div style="position:absolute;top:36px;left:0;right:0;text-align:center;font-family:Plex;font-size:50px;font-weight:700">Add button, in the panel header</div><div style="position:absolute;top:130px;left:0;right:0;display:flex;justify-content:center;gap:60px;transform-origin:top center;transform:scale(.72)">{phone(screen("closed"),"Panel closed: the floating + as it is today")}{phone(screen("open"),"Panel open: the + moves into the header, so you can add without closing it")}</div></body></html>'
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={"width":1100,"height":900}); pg.set_content(html); pg.wait_for_timeout(300)
    pg.screenshot(path=sys.argv[1] if len(sys.argv)>1 else "header_add_mock.png"); b.close()
