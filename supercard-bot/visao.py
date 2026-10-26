"""Reconhecimento de imagens no ecrã (template matching com OpenCV)."""
import os

import cv2
import numpy as np


def ler_imagem(caminho):
    # np.fromfile + imdecode funciona com acentos no caminho (cv2.imread não, no Windows)
    dados = np.fromfile(caminho, dtype=np.uint8)
    return cv2.imdecode(dados, cv2.IMREAD_COLOR)


def gravar_imagem(caminho, img):
    ok, dados = cv2.imencode(".png", img)
    if not ok:
        raise IOError(f"não foi possível gravar {caminho}")
    dados.tofile(caminho)


class Modelos:
    """Carrega (e guarda em memória) as imagens de referência da pasta templates."""

    def __init__(self, pasta):
        self.pasta = pasta
        self._cache = {}

    def caminho(self, nome):
        return os.path.join(self.pasta, nome)

    def existe(self, nome):
        return os.path.isfile(self.caminho(nome))

    def obter(self, nome):
        if nome not in self._cache:
            img = ler_imagem(self.caminho(nome))
            if img is None:
                raise FileNotFoundError(f"imagem de referência inválida: {nome}")
            self._cache[nome] = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
        return self._cache[nome]

    def todos(self):
        return sorted(f for f in os.listdir(self.pasta) if f.lower().endswith(".png"))


def cinzento(ecra):
    return cv2.cvtColor(ecra, cv2.COLOR_BGR2GRAY)


def procurar(ecra_cinza, modelo, regiao=None):
    """Procura o modelo no ecrã.

    regiao: [x1, y1, x2, y2] em frações do ecrã (0 a 1) para limitar a procura.
    Devolve (x_centro, y_centro, pontuacao) em píxeis do ecrã completo.
    """
    altura, largura = ecra_cinza.shape[:2]
    dx = dy = 0
    zona = ecra_cinza
    if regiao:
        x1, y1, x2, y2 = regiao
        dx, dy = int(x1 * largura), int(y1 * altura)
        zona = ecra_cinza[dy:int(y2 * altura), dx:int(x2 * largura)]
    mh, mw = modelo.shape[:2]
    if zona.shape[0] < mh or zona.shape[1] < mw:
        return None
    res = cv2.matchTemplate(zona, modelo, cv2.TM_CCOEFF_NORMED)
    _, pontuacao, _, (x, y) = cv2.minMaxLoc(res)
    return dx + x + mw // 2, dy + y + mh // 2, pontuacao
