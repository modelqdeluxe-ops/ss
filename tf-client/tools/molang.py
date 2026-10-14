"""Molang (las expresiones de los keyframes de Blockbench/ModelEngine: «math.sin(q.anim_time * 360) * 5»), para hornear
esas animaciones al convertir los modelos (tools/vfx_bb.py). Sin eval de Python: un analizador propio.

Lo que entiende:
  números, + - * / , paréntesis, - unario, ! , comparaciones (< <= > >= == !=), && || , ?: (ternario y «a ?? b»)
  math.sin/cos/tan/asin/acos/atan/atan2 (en grados, como Molang), abs, ceil, floor, round, trunc, sqrt, pow, exp, ln,
  min, max, clamp, lerp, lerprotate, mod, sign, pi, random (fijo: la mitad del rango, para que sea estable),
  random_integer, die_roll, die_roll_integer, hermite_blend, min_angle
  q./query. anim_time, life_time (= anim_time), delta_time (1/20), anim_length; variable./v. y temp./t. que se asignan
  dentro de la misma expresión («v.a = 3; v.a * 2») y «return».
Lo que no conoce sale en `unknown` (y vale 0), para que el conversor avise.
"""
import math
import re

TOKEN = re.compile(r'\s*(?:(\d+\.\d*|\.\d+|\d+)|([A-Za-z_][A-Za-z_0-9]*(?:\.[A-Za-z_][A-Za-z_0-9]*)*)|'
                   r'(\?\?|&&|\|\||==|!=|<=|>=|[-+*/(),?:<>!=;]))')


class MolangError(Exception):
    pass


def tokens(src):
    out, i = [], 0
    src = src.strip()
    while i < len(src):
        m = TOKEN.match(src, i)
        if not m or m.end() == i:
            if src[i:].strip() == '':
                break
            raise MolangError(f'no entiendo «{src[i:]}»')
        if m.group(1):
            out.append(('n', float(m.group(1))))
        elif m.group(2):
            out.append(('id', m.group(2).lower()))
        else:
            out.append(('op', m.group(3)))
        i = m.end()
    return out


def _deg(f):
    return lambda x: f(math.radians(x))


def _adeg(f):
    return lambda *a: math.degrees(f(*a))


FUNCS = {
    'math.sin': _deg(math.sin), 'math.cos': _deg(math.cos), 'math.tan': _deg(math.tan),
    'math.asin': _adeg(math.asin), 'math.acos': _adeg(math.acos), 'math.atan': _adeg(math.atan),
    'math.atan2': _adeg(math.atan2),
    'math.abs': abs, 'math.ceil': math.ceil, 'math.floor': math.floor, 'math.round': lambda x: math.floor(x + 0.5),
    'math.trunc': math.trunc, 'math.sqrt': lambda x: math.sqrt(max(0.0, x)), 'math.pow': math.pow,
    'math.exp': math.exp, 'math.ln': lambda x: math.log(x) if x > 0 else 0.0,
    'math.min': min, 'math.max': max, 'math.clamp': lambda v, a, b: max(a, min(b, v)),
    'math.lerp': lambda a, b, t: a + (b - a) * t,
    'math.lerprotate': lambda a, b, t: a + (((b - a + 180) % 360) - 180) * t,
    'math.mod': lambda a, b: math.fmod(a, b) if b else 0.0, 'math.sign': lambda x: (x > 0) - (x < 0),
    'math.random': lambda a, b: (a + b) / 2, 'math.random_integer': lambda a, b: math.floor((a + b) / 2),
    'math.die_roll': lambda n, a, b: n * (a + b) / 2, 'math.die_roll_integer': lambda n, a, b: math.floor(n * (a + b) / 2),
    'math.hermite_blend': lambda t: 3 * t * t - 2 * t * t * t,
    'math.min_angle': lambda a: ((a + 180) % 360) - 180,
}
CONSTS = {'math.pi': math.pi, 'true': 1.0, 'false': 0.0}


class Parser:
    def __init__(self, toks, env, unknown):
        self.t = toks
        self.i = 0
        self.env = env
        self.unknown = unknown

    def peek(self, v=None):
        if self.i >= len(self.t):
            return None
        tok = self.t[self.i]
        if v is None or tok[1] == v:
            return tok
        return None

    def take(self, v=None):
        tok = self.peek(v)
        if tok is None:
            raise MolangError(f'falta «{v}»' if v else 'fin inesperado')
        self.i += 1
        return tok

    # programa: sentencias separadas por «;» (asignaciones de variables y «return»)
    def program(self):
        last = 0.0
        while self.i < len(self.t):
            if self.peek(';'):
                self.i += 1
                continue
            tok = self.peek()
            if tok[0] == 'id' and tok[1] == 'return':
                self.i += 1
                return self.expr()
            if tok[0] == 'id' and self._is_var(tok[1]) and self.i + 1 < len(self.t) and self.t[self.i + 1] == ('op', '='):
                name = self._var_key(tok[1])
                self.i += 2
                self.env[name] = self.expr()
                last = self.env[name]
                continue
            last = self.expr()
        return last

    @staticmethod
    def _is_var(name):
        return name.startswith(('variable.', 'v.', 'temp.', 't.'))

    @staticmethod
    def _var_key(name):
        head, rest = name.split('.', 1)
        return {'v': 'variable', 't': 'temp'}.get(head, head) + '.' + rest

    def expr(self):
        cond = self.nullish()
        if self.peek('?'):
            self.i += 1
            a = self.expr()
            if self.peek(':'):
                self.i += 1
                b = self.expr()
            else:
                b = 0.0
            return a if cond else b
        return cond

    def nullish(self):
        v = self.or_()
        while self.peek('??'):
            self.i += 1
            w = self.or_()
            v = v if v is not None else w
        return v

    def or_(self):
        v = self.and_()
        while self.peek('||'):
            self.i += 1
            w = self.and_()
            v = 1.0 if (v or w) else 0.0
        return v

    def and_(self):
        v = self.cmp()
        while self.peek('&&'):
            self.i += 1
            w = self.cmp()
            v = 1.0 if (v and w) else 0.0
        return v

    def cmp(self):
        v = self.add()
        while self.peek() and self.peek()[1] in ('<', '<=', '>', '>=', '==', '!='):
            op = self.take()[1]
            w = self.add()
            v = 1.0 if {'<': v < w, '<=': v <= w, '>': v > w, '>=': v >= w, '==': v == w, '!=': v != w}[op] else 0.0
        return v

    def add(self):
        v = self.mul()
        while self.peek() and self.peek()[1] in ('+', '-'):
            op = self.take()[1]
            w = self.mul()
            v = v + w if op == '+' else v - w
        return v

    def mul(self):
        v = self.unary()
        while self.peek() and self.peek()[1] in ('*', '/'):
            op = self.take()[1]
            w = self.unary()
            v = v * w if op == '*' else (v / w if w else 0.0)
        return v

    def unary(self):
        if self.peek('-'):
            self.i += 1
            return -self.unary()
        if self.peek('+'):
            self.i += 1
            return self.unary()
        if self.peek('!'):
            self.i += 1
            return 0.0 if self.unary() else 1.0
        return self.atom()

    def atom(self):
        tok = self.take()
        if tok[0] == 'n':
            return tok[1]
        if tok == ('op', '('):
            v = self.expr()
            self.take(')')
            return v
        if tok[0] != 'id':
            raise MolangError(f'no esperaba «{tok[1]}»')
        name = tok[1]
        if name.startswith('query.'):
            name = 'q.' + name[6:]
        if self.peek('('):
            self.i += 1
            args = []
            if not self.peek(')'):
                args.append(self.expr())
                while self.peek(','):
                    self.i += 1
                    args.append(self.expr())
            self.take(')')
            f = FUNCS.get(name)
            if f is None:
                self.unknown.add(name)
                return 0.0
            try:
                return float(f(*args))
            except (TypeError, ValueError, OverflowError):
                self.unknown.add(name + ' (argumentos)')
                return 0.0
        if name in CONSTS:
            return CONSTS[name]
        if name in ('q.anim_time', 'q.life_time'):
            return self.env['q.anim_time']
        if name == 'q.delta_time':
            return 1 / 20
        if name == 'q.anim_length':
            return self.env.get('q.anim_length', 0.0)
        if self._is_var(name):
            key = self._var_key(name)
            if key not in self.env:
                self.unknown.add(name)
            return self.env.get(key, 0.0)
        self.unknown.add(name)
        return 0.0


def is_expression(v):
    """¿Es una expresión (no un número suelto)?"""
    if isinstance(v, (int, float)):
        return False
    s = str(v).strip()
    if s == '':
        return False
    try:
        float(s)
        return False
    except ValueError:
        return True


def evaluate(src, anim_time, unknown=None, anim_length=0.0, env=None):
    """Valor de la expresión en ese instante (segundos). Lo que no conoce va a «unknown» y vale 0."""
    if isinstance(src, (int, float)):
        return float(src)
    s = str(src).strip()
    if not s:
        return 0.0
    unknown = unknown if unknown is not None else set()
    e = dict(env or {})
    e['q.anim_time'] = anim_time
    e['q.anim_length'] = anim_length
    try:
        v = Parser(tokens(s), e, unknown).program()
        return float(v) if v is not None and math.isfinite(v) else 0.0
    except (MolangError, IndexError) as ex:
        unknown.add(f'«{s}» ({ex})')
        return 0.0


def depends_on_time(src):
    s = str(src).lower()
    return 'anim_time' in s or 'life_time' in s or 'random' in s or 'variable' in s or 'v.' in s


if __name__ == '__main__':
    u = set()
    assert abs(evaluate('math.sin(q.anim_time * 360) * 5', 0.25, u) - 5) < 1e-9
    assert evaluate('-2 + 3 * 4', 0) == 10
    assert evaluate('query.anim_time > 1 ? 7 : 3', 2) == 7
    assert evaluate('v.a = 2; v.a * 4', 0) == 8
    assert evaluate('math.clamp(10, 0, 4)', 0) == 4
    assert abs(evaluate('math.cos(180)', 0) + 1) < 1e-9
    assert evaluate('return 5;', 0) == 5
    assert not u, u
    evaluate('q.ground_speed * 2', 0, u)
    assert 'q.ground_speed' in u
    print('molang: ok')
