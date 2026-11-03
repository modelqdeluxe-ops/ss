# ck_listar.py <bundle...>: cuántos objetos de cada tipo hay y los primeros nombres
import sys, UnityPy
from collections import Counter
env = UnityPy.load(*sys.argv[1:])
c = Counter(); nombres = {}
for o in env.objects:
    t = o.type.name; c[t] += 1
    if t in ('Texture2D', 'Sprite', 'MonoBehaviour', 'AnimationClip', 'GameObject') and len(nombres.setdefault(t, [])) < 25:
        try: nombres[t].append(o.read().m_Name)
        except Exception as e: nombres[t].append('?' + str(e)[:30])
print(c.most_common())
for t, n in nombres.items(): print(t, n)
