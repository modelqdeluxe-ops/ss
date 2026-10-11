"""Iconos de los botones del menú principal (textures/gui/menu_icons.png): los logos de WhatsApp y Discord (Simple
Icons, CC0: https://simpleicons.org) y el globo de la web (Lucide, ISC: https://lucide.dev), blancos sobre transparente,
256x256 cada uno con 16 de margen (WhatsApp, Discord, web). TFMenuButton los tiñe y les pone contorno. Se dibujan con
el Chromium del sistema (los SVG se bajan de jsDelivr). Uso: python3 tools/gen_menu_icons.py [ruta de chrome]
"""
import os
import re
import subprocess
import sys
import tempfile
import urllib.request

from PIL import Image

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'assets', 'tfclient',
                   'textures', 'gui', 'menu_icons.png')
CHROME = sys.argv[1] if len(sys.argv) > 1 else '/opt/pw-browsers/chromium-1194/chrome-linux/chrome'
CELL, PAD = 256, 16


def get(url):
    with urllib.request.urlopen(url, timeout=30) as r:
        return r.read().decode('utf-8')


def main():
    path = lambda svg: re.search(r'd="([^"]+)"', svg).group(1)
    whatsapp = path(get('https://cdn.jsdelivr.net/npm/simple-icons@13.21.0/icons/whatsapp.svg'))
    discord = path(get('https://cdn.jsdelivr.net/npm/simple-icons@13.21.0/icons/discord.svg'))
    globe = re.sub(r'<!--.*?-->', '', get('https://cdn.jsdelivr.net/npm/lucide-static@0.460.0/icons/globe.svg'), flags=re.S)
    globe = globe.replace('stroke="currentColor"', 'stroke="#fff"').replace('stroke-width="2"', 'stroke-width="2.4"')
    globe = re.sub(r'width="24"\s*height="24"', 'width="%d" height="%d"' % (CELL, CELL), globe)
    html = ('<html><body style="margin:0;background:transparent"><div style="display:flex">'
            '<svg width="{c}" height="{c}" viewBox="0 0 24 24"><path fill="#fff" d="{w}"/></svg>'
            '<svg width="{c}" height="{c}" viewBox="0 0 24 24"><path fill="#fff" d="{d}"/></svg>{g}</div></body></html>'
            ).format(c=CELL, w=whatsapp, d=discord, g=globe)
    with tempfile.TemporaryDirectory() as tmp:
        page, shot = os.path.join(tmp, 'icons.html'), os.path.join(tmp, 'icons.png')
        with open(page, 'w') as f:
            f.write(html)
        subprocess.run([CHROME, '--headless=new', '--no-sandbox', '--disable-gpu', '--hide-scrollbars',
                        '--default-background-color=00000000', '--window-size=%d,%d' % (3 * CELL, 2 * CELL),
                        '--screenshot=' + shot, 'file://' + page], check=True, capture_output=True)
        im = Image.open(shot).convert('RGBA')
        atlas = Image.new('RGBA', (3 * (CELL + PAD) + PAD, CELL + 2 * PAD), (255, 255, 255, 0))
        for i in range(3):
            atlas.alpha_composite(im.crop((i * CELL, 0, (i + 1) * CELL, CELL)), (PAD + i * (CELL + PAD), PAD))
        atlas.save(OUT, optimize=True)
    print(OUT, atlas.size)


if __name__ == '__main__':
    main()
