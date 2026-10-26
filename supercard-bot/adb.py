"""Comunicação com o telemóvel através do ADB."""
import os
import subprocess

import cv2
import numpy as np

PASTA = os.path.dirname(os.path.abspath(__file__))


class ErroADB(Exception):
    pass


def resolver_caminho(caminho):
    """Usa o adb da pasta do projeto se existir; senão, o do PATH."""
    if not caminho:
        return "adb"
    local = caminho if os.path.isabs(caminho) else os.path.join(PASTA, caminho)
    if os.path.exists(local):
        return local
    return "adb"


class ADB:
    def __init__(self, caminho="adb", dispositivo=None):
        self.base = [resolver_caminho(caminho)]
        if dispositivo:
            self.base += ["-s", dispositivo]

    def _correr(self, *args, timeout=20):
        try:
            r = subprocess.run(self.base + list(args), capture_output=True, timeout=timeout)
        except FileNotFoundError:
            raise ErroADB(
                "adb não encontrado. Extrai o 'platform-tools' para a pasta do bot "
                "ou indica o caminho em config.yaml (adb -> caminho)."
            )
        except subprocess.TimeoutExpired:
            raise ErroADB("o adb não respondeu a tempo (telemóvel desligado?)")
        if r.returncode != 0:
            raise ErroADB(r.stderr.decode(errors="replace").strip() or "erro desconhecido do adb")
        return r.stdout

    def verificar(self):
        estado = self._correr("get-state").decode().strip()
        if estado != "device":
            raise ErroADB(f"telemóvel no estado '{estado}' (aceitaste a depuração USB?)")

    def capturar(self):
        """Devolve o ecrã atual como imagem OpenCV (BGR)."""
        dados = self._correr("exec-out", "screencap", "-p")
        img = cv2.imdecode(np.frombuffer(dados, np.uint8), cv2.IMREAD_COLOR)
        if img is None:
            raise ErroADB("captura de ecrã inválida")
        return img

    def tocar(self, x, y):
        self._correr("shell", "input", "tap", str(int(x)), str(int(y)))

    def voltar(self):
        self._correr("shell", "input", "keyevent", "4")
