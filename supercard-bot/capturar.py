"""Ferramenta para criar as imagens de referência (templates).

Uso:
    python capturar.py                 # captura o ecrã e recortas os botões que queres
    python capturar.py coordenadas     # clicas na imagem e vês a posição (para 'tocar_em' e 'regiao')
    python capturar.py --ficheiro x.png   # usa uma captura já existente em vez do telemóvel
"""
import argparse
import os
import sys

import cv2
import yaml

from adb import ADB, ErroADB
from visao import gravar_imagem, ler_imagem

PASTA = os.path.dirname(os.path.abspath(__file__))
ALTURA_JANELA = 900


def obter_ecra(args):
    if args.ficheiro:
        img = ler_imagem(args.ficheiro)
        if img is None:
            sys.exit(f"Não consegui abrir {args.ficheiro}")
        return img
    with open(os.path.join(PASTA, "config.yaml"), encoding="utf-8") as f:
        cfg = (yaml.safe_load(f) or {}).get("adb") or {}
    adb = ADB(cfg.get("caminho"), cfg.get("dispositivo"))
    try:
        adb.verificar()
        return adb.capturar()
    except ErroADB as e:
        sys.exit(f"Erro do ADB: {e}")


def reduzir(img):
    escala = min(1.0, ALTURA_JANELA / img.shape[0])
    if escala < 1.0:
        img = cv2.resize(img, None, fx=escala, fy=escala, interpolation=cv2.INTER_AREA)
    return img, escala


def recortar(ecra):
    pasta = os.path.join(PASTA, "templates")
    os.makedirs(pasta, exist_ok=True)
    pequeno, escala = reduzir(ecra)
    while True:
        print("\nSeleciona a área com o rato e carrega ENTER (ou C para cancelar).")
        x, y, w, h = cv2.selectROI("Seleciona o botao", pequeno, showCrosshair=False)
        cv2.destroyAllWindows()
        if w == 0 or h == 0:
            print("Nada selecionado.")
            break
        x1, y1 = int(x / escala), int(y / escala)
        x2, y2 = int((x + w) / escala), int((y + h) / escala)
        nome = input("Nome da imagem (ex.: botao_recolher): ").strip()
        if not nome:
            print("Sem nome, não gravado.")
        else:
            if not nome.lower().endswith(".png"):
                nome += ".png"
            gravar_imagem(os.path.join(pasta, nome), ecra[y1:y2, x1:x2])
            print(f"Gravado: templates/{nome}")
        if input("Recortar outra área deste ecrã? (s/n): ").strip().lower() != "s":
            break


def coordenadas(ecra):
    pequeno, escala = reduzir(ecra)
    altura, largura = ecra.shape[:2]

    def clique(evento, x, y, *_):
        if evento == cv2.EVENT_LBUTTONDOWN:
            fx, fy = x / escala / largura, y / escala / altura
            print(f"posicao: [{fx:.3f}, {fy:.3f}]")

    print("Clica na imagem para ver a posição. Carrega numa tecla para sair.")
    cv2.namedWindow("Coordenadas")
    cv2.setMouseCallback("Coordenadas", clique)
    cv2.imshow("Coordenadas", pequeno)
    cv2.waitKey(0)
    cv2.destroyAllWindows()


def main():
    p = argparse.ArgumentParser()
    p.add_argument("modo", nargs="?", default="recortar", choices=["recortar", "coordenadas"])
    p.add_argument("--ficheiro", help="usar uma imagem existente em vez de capturar")
    args = p.parse_args()
    ecra = obter_ecra(args)
    if args.modo == "coordenadas":
        coordenadas(ecra)
    else:
        recortar(ecra)


if __name__ == "__main__":
    main()
