"""Mock-up: three subtle ways to mark an empty day card."""
import sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent))
import screens as S
from playwright.sync_api import sync_playwright

def empty(v):
    if v == 1:  # hollow bar, dimmer text
        return '<div class="card" style="background:rgba(255,255,255,.12)"><div class="bar" style="background:transparent;border:1.5px solid rgba(255,255,255,.55)"></div><div><div class="t" style="opacity:.8">Nothing planned</div><div class="s" style="opacity:.6">Tap to add an event</div></div></div>'
    if v == 2:  # sun glyph in place of the bar
        sun = '<svg width="22" height="22" viewBox="0 0 24 24" style="flex:none;margin:0 -2px 0 -4px" fill="none" stroke="rgba(255,255,255,.8)" stroke-width="1.8" stroke-linecap="round"><circle cx="12" cy="12" r="4"/>' + ''.join(f'<line x1="{12+7*c}" y1="{12+7*s_}" x2="{12+9.5*c}" y2="{12+9.5*s_}"/>' for c,s_ in [(1,0),(0,1),(-1,0),(0,-1),(.7,.7),(-.7,.7),(.7,-.7),(-.7,-.7)]) + '</svg>'
        return f'<div class="card" style="background:rgba(255,255,255,.12)">{sun}<div><div class="t" style="opacity:.85">Clear day</div><div class="s" style="opacity:.6">Tap to add an event</div></div></div>'
    return '<div class="card" style="background:rgba(255,255,255,.07);border:1.5px dashed rgba(255,255,255,.4)"><div class="bar" style="background:transparent"></div><div><div class="t" style="opacity:.8">Nothing planned</div><div class="s" style="opacity:.6">Tap to add an event</div></div></div>'

def col(v, label):
    real = S.card("Black bin","All day",S.GREEN)
    return f'<div style="width:380px">{real}<div style="height:6px"></div>{S.day("Fri",30,empty(v),True,last=True)}<div style="margin-top:18px;font-size:21px;font-weight:600;line-height:1.3;text-align:center">{label}</div></div>'

css = S.CSS.replace("width:1080px;height:1920px","width:1300px;height:520px")
html = f'<html><head><style>{css}</style></head><body><div style="position:absolute;top:28px;left:0;right:0;text-align:center;font-family:Plex;font-size:44px;font-weight:700">Marking an empty day</div><div style="position:absolute;top:130px;left:40px;right:40px;display:flex;justify-content:space-between">{col(1,"A: hollow bar, dimmer text")}{col(2,"B: sun glyph and Clear day")}{col(3,"C: dashed outline")}</div></body></html>'
with sync_playwright() as p:
    b = p.chromium.launch(); pg = b.new_page(viewport={"width":1300,"height":520}); pg.set_content(html); pg.wait_for_timeout(300)
    pg.screenshot(path=sys.argv[1]); b.close()
