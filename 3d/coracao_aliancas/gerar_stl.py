"""Gera um modelo 3D imprimivel da peca "Together Forever": fatia de tronco com
coracao vazado, duas aliancas entrelacadas com nomes e base com placa.

Uso:  python3 gerar_stl.py [NOME1] [NOME2] [FRASE] [DATA]
Requer: pip install manifold3d trimesh shapely matplotlib numpy
Unidades em mm. Imprime em pe, assente na base.
"""
import math
import sys

import numpy as np
import trimesh
from manifold3d import CrossSection, FillRule, Manifold, OpType
from matplotlib.font_manager import FontProperties
from matplotlib.textpath import TextPath
from shapely.geometry import Polygon

NOME1 = sys.argv[1] if len(sys.argv) > 1 else "JACK"
NOME2 = sys.argv[2] if len(sys.argv) > 2 else "EMILY"
FRASE = sys.argv[3] if len(sys.argv) > 3 else "Together Forever"
DATA = sys.argv[4] if len(sys.argv) > 4 else "2026.02.14"
FONTE = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"

rng = np.random.default_rng(7)

# ---------------------------------------------------------------- utilitarios


def texto_2d(txt, altura, centro=(0.0, 0.0)):
    """Texto -> CrossSection, centrado em `centro`, com altura de maiusculas `altura`."""
    path = TextPath((0, 0), txt, size=10, prop=FontProperties(fname=FONTE))
    forma = Polygon()
    for p in path.to_polygons():
        if len(p) >= 3:
            forma = forma.symmetric_difference(Polygon(p).buffer(0))
    k = altura / 7.3  # altura de maiusculas da DejaVu Serif ~0.73 * size
    minx, miny, maxx, maxy = forma.bounds
    cx, cy = (minx + maxx) / 2, (miny + maxy) / 2
    contornos = []
    geoms = getattr(forma, "geoms", [forma])
    for g in geoms:
        for anel in [g.exterior, *g.interiors]:
            pts = np.asarray(anel.coords)[:-1]
            contornos.append(((pts - [cx, cy]) * k + centro).tolist())
    return CrossSection(contornos, FillRule.EvenOdd)


def poligono(pts):
    return CrossSection([np.asarray(pts).tolist()], FillRule.NonZero)


def ruido_periodico(n, harmonicos, amp):
    """Ruido suave e periodico em [0, 2pi) feito com senos aleatorios."""
    t = np.linspace(0, 2 * np.pi, n, endpoint=False)
    r = np.zeros(n)
    for k in harmonicos:
        r += rng.uniform(0.3, 1.0) / k**0.6 * np.sin(k * t + rng.uniform(0, 2 * np.pi))
    return t, r / np.abs(r).max() * amp


def extrudir_y(cs, y0, y1):
    """Extruda um perfil desenhado no plano XZ (x, z) ao longo de Y, de y0 a y1."""
    return Manifold.extrude(cs, y1 - y0).rotate((90, 0, 0)).translate((0, y1, 0))


# ------------------------------------------------------------------- base

BASE_L, BASE_P, BASE_A = 128.0, 56.0, 28.0
base = Manifold.batch_hull([
    Manifold.cube((BASE_L, BASE_P, BASE_A - 2.5), True).translate((0, 0, (BASE_A - 2.5) / 2)),
    Manifold.cube((BASE_L - 5, BASE_P - 5, BASE_A), True).translate((0, 0, BASE_A / 2)),
])

# placa frontal com cantos recortados
PL_L, PL_A, PL_E = 76.0, 20.0, 1.6
c = 2.5
placa_pts = [(-PL_L / 2 + c, -PL_A / 2), (PL_L / 2 - c, -PL_A / 2), (PL_L / 2, -PL_A / 2 + c),
             (PL_L / 2, PL_A / 2 - c), (PL_L / 2 - c, PL_A / 2), (-PL_L / 2 + c, PL_A / 2),
             (-PL_L / 2, PL_A / 2 - c), (-PL_L / 2, -PL_A / 2 + c)]
zc = BASE_A / 2 - 0.5
frente = -BASE_P / 2
placa = extrudir_y(poligono(placa_pts).translate((0, zc)), frente - PL_E, frente + 0.5)
moldura = extrudir_y(
    poligono(placa_pts).translate((0, zc)) - poligono(placa_pts).offset(-1.4).translate((0, zc)),
    frente - PL_E - 0.6, frente - PL_E + 0.1)
letras = texto_2d(FRASE, 5.2, (0, zc + 3.6)) + texto_2d(DATA, 4.4, (0, zc - 4.6))
letras_3d = extrudir_y(letras, frente - PL_E - 0.8, frente - PL_E + 0.1)
base_completa = base + placa + moldura + letras_3d

# ---------------------------------------------------------- fatia de tronco

R = 56.0                 # raio exterior medio (com casca)
CASCA = 6.5              # espessura media da casca
ESP_MADEIRA = 26.0       # profundidade do cerne
ESP_CASCA = 29.0         # a casca sobressai um pouco
ZC = BASE_A - 3.0 + R - 7.0   # centro do disco (fundo achatado encaixa 3 mm na base)

n = 720
t, r_lento = ruido_periodico(n, range(2, 9), 2.2)
_, r_rugas = ruido_periodico(n, range(40, 90), 1.3)
r_ext = R + r_lento + r_rugas
_, r_int_ruido = ruido_periodico(n, range(3, 14), 1.0)
r_int = R - CASCA + r_lento * 0.6 + r_int_ruido

ext_cs = poligono(np.c_[r_ext * np.cos(t), r_ext * np.sin(t)])
int_cs = poligono(np.c_[r_int * np.cos(t), r_int * np.sin(t)])

# coracao parametrico
s = np.linspace(0, 2 * np.pi, 400, endpoint=False)
hx = 16 * np.sin(s) ** 3
hz = 13 * np.cos(s) - 5 * np.cos(2 * s) - 2 * np.cos(3 * s) - np.cos(4 * s)
esc = 2.2
coracao_cs = poligono(np.c_[hx * esc, (hz + 2.5) * esc])

madeira = extrudir_y(int_cs.offset(0.6), -ESP_MADEIRA / 2, ESP_MADEIRA / 2)
casca = extrudir_y(ext_cs - int_cs, -ESP_CASCA / 2, ESP_CASCA / 2)

# sulcos verticais na casca (fendas da casca, em ambas as faces e na lateral)
fendas = []
for _ in range(70):
    ang = rng.uniform(0, 2 * np.pi)
    largura = rng.uniform(0.6, 1.4)
    comp = rng.uniform(3, 9)
    f = Manifold.cube((largura, ESP_CASCA + 2, comp), True)
    f = f.rotate((0, rng.uniform(-25, 25), 0)).translate((R + 1.2, 0, 0))
    fendas.append(f.rotate((0, -math.degrees(ang), 0)))
casca = casca - Manifold.batch_boolean(fendas, OpType.Add)

# aneis de crescimento gravados na face frontal e traseira
aneis = []
for raio in np.arange(37, R - CASCA - 1.5, 3.2):
    _, w = ruido_periodico(n, range(2, 7), 0.8)
    rr = raio + w
    a_ext = poligono(np.c_[(rr + 0.3) * np.cos(t), (rr + 0.3) * np.sin(t)])
    a_int = poligono(np.c_[(rr - 0.3) * np.cos(t), (rr - 0.3) * np.sin(t)])
    aneis.append(a_ext - a_int)
aneis_cs = CrossSection.batch_boolean(aneis, OpType.Add)
sulcos = (extrudir_y(aneis_cs, -ESP_MADEIRA / 2 - 1, -ESP_MADEIRA / 2 + 0.5)
          + extrudir_y(aneis_cs, ESP_MADEIRA / 2 - 0.5, ESP_MADEIRA / 2 + 1))

# rachas radiais
rachas = []
for ang, comp in [(84, 22), (150, 12), (322, 14), (25, 9)]:
    cs = poligono([(R - CASCA - comp, -0.15), (R - CASCA + 1, -0.9), (R - CASCA + 1, 0.9),
                   (R - CASCA - comp, 0.15)]).rotate(ang)
    rachas.append(extrudir_y(cs, -ESP_CASCA / 2 - 1, -ESP_MADEIRA / 2 + 2.5))
    rachas.append(extrudir_y(cs, ESP_MADEIRA / 2 - 2.5, ESP_CASCA / 2 + 1))

tronco = (madeira + casca) - sulcos
for rc in rachas:
    tronco = tronco - rc
tronco = tronco - extrudir_y(coracao_cs, -ESP_CASCA, ESP_CASCA)
# achatar o fundo para assentar na base
corte_z = -R + 7.0
tronco = tronco.trim_by_plane((0, 0, 1), corte_z)
tronco = tronco.translate((0, 0, ZC))

# ------------------------------------------------------------------ aliancas

RO, RI, LARG = 15.0, 12.7, 10.0   # raio exterior, interior e largura da alianca


def alianca():
    # perfil ligeiramente abaulado por fora (comfort fit)
    zs = np.linspace(-LARG / 2, LARG / 2, 25)
    fora = RO - 0.6 + 0.6 * np.sqrt(np.clip(1 - (zs / (LARG / 2)) ** 2, 0, 1))
    pts = [(RI, -LARG / 2)] + list(zip(fora, zs)) + [(RI, LARG / 2)]
    return Manifold.revolve(poligono(pts), 160)


def gravar_nome(anel, nome, theta, sinal):
    """Grava `nome` na face exterior da alianca (eixo Z), centrado no angulo theta."""
    cs = texto_2d(nome, 5.6)
    txt = Manifold.extrude(cs, 1.6).translate((0, 0, -0.7)).refine_to_length(0.6)

    def dobrar(v):
        s_, t_, h = v[:, 0], v[:, 1], v[:, 2]
        phi = theta + sinal * s_ / RO
        r = RO + h
        return np.c_[r * np.cos(phi), r * np.sin(phi), sinal * t_]

    return anel - txt.warp_batch(dobrar)


def rot(eixo_graus):
    return trimesh.transformations.euler_matrix(*np.radians(eixo_graus), "sxyz")[:3, :3]


# corrente: A deitada (eixo Z) e B em pe (eixo Y), afastadas RO-1 -> entrelacadas
d = (RO + RI) / 2 / 2
glob = rot((-45, 0, 0)) @ rot((0, 0, 8))   # ambas a ~45 graus do observador: os dois nomes ficam legiveis
loc = {"A": (rot((0, 0, 0)), np.array([-d, 0, 0])),
       "B": (rot((90, 0, 0)), np.array([d, 0, 0]))}
vista = np.array([0, -1, 0])  # o observador esta em -Y
aneis_final = []
for chave, nome in (("A", NOME1), ("B", NOME2)):
    Rl, pos = loc[chave]
    M = glob @ Rl
    v_loc = M.T @ vista
    theta = math.atan2(v_loc[1], v_loc[0])
    # quando o eixo local Z aponta para baixo no mundo, inverte o texto para ficar direito
    sinal = 1 if (M @ [0, 0, 1])[2] >= 0 else -1
    a = gravar_nome(alianca(), nome, theta, sinal)
    T = np.eye(4)
    T[:3, :3] = M
    T[:3, 3] = glob @ pos
    aneis_final.append(a.transform(T[:3, :].tolist()))

par = aneis_final[0] + aneis_final[1]
# posicionar o par no centro do coracao
centro_coracao = np.array([0.0, 0.0, ZC + 2.0])
par = par.translate(centro_coracao.tolist())

# suspensao: cada alianca (entrelacadas, sem se tocarem) recebe uma haste fina
# desde o seu ponto mais alto ate ao bico superior do coracao, que entra 4 mm na madeira
z_bico = ZC + (5 + 2.5) * esc   # bico superior do coracao (s=0: hx=0, hz=5)
ancora = Manifold.sphere(1.8, 32).translate((0, 0, z_bico + 4))
suspensao = Manifold()
for p in par.decompose():
    Vp = np.asarray(p.to_mesh().vert_properties)[:, :3]
    tp = Vp[np.argmax(Vp[:, 2])] - [0, 0, 0.9]
    suspensao += Manifold.batch_hull([Manifold.sphere(1.4, 24).translate(tp.tolist()), ancora])

# remover lascas de casca soltas criadas pelas fendas
tronco = Manifold.batch_boolean([p for p in tronco.decompose() if p.volume() > 500], OpType.Add)

# --------------------------------------------------------------- exportar


def guardar(m, nome):
    mm = m.simplify(0.002).to_mesh()   # remove triangulos degenerados
    tm = trimesh.Trimesh(np.asarray(mm.vert_properties)[:, :3], np.asarray(mm.tri_verts))
    tm.export(nome)
    print(f"{nome}: {len(tm.faces)} triangulos, estanque={tm.is_watertight}, "
          f"dimensoes={np.round(tm.extents, 1)} mm")


tronco_com_aliancas = tronco + par + suspensao
guardar(base_completa + tronco_com_aliancas, "coracao_aliancas_completo.stl")
guardar(base_completa, "parte1_base_placa.stl")
guardar(tronco_com_aliancas, "parte2_tronco_aliancas.stl")
