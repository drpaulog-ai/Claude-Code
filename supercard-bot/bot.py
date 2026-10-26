"""Bot para o WWE SuperCard: recolhe recompensas diárias e repete eventos.

Uso:
    python bot.py tarefa diarias     # corre a tarefa 'diarias' do config.yaml
    python bot.py tarefa evento      # corre a tarefa 'evento'
    python bot.py testar             # mostra que imagens são reconhecidas no ecrã atual
    python bot.py listar             # lista as tarefas definidas
"""
import argparse
import os
import sys
import time
from collections import Counter

import yaml

from adb import ADB, ErroADB
from visao import Modelos, cinzento, gravar_imagem, procurar

PASTA = os.path.dirname(os.path.abspath(__file__))
ACOES = {"tocar", "tocar_em", "voltar", "esperar", "parar"}


def log(msg):
    print(time.strftime("[%H:%M:%S]"), msg, flush=True)


def carregar_config(caminho):
    with open(caminho, encoding="utf-8") as f:
        return yaml.safe_load(f)


def guardar_captura(ecra, motivo):
    pasta = os.path.join(PASTA, "capturas")
    os.makedirs(pasta, exist_ok=True)
    caminho = os.path.join(pasta, f"{time.strftime('%Y%m%d_%H%M%S')}_{motivo}.png")
    gravar_imagem(caminho, ecra)
    return caminho


def validar_tarefa(nome, tarefa, modelos):
    erros = []
    regras = tarefa.get("regras") or []
    if not regras:
        erros.append(f"a tarefa '{nome}' não tem regras")
    for i, r in enumerate(regras, 1):
        acao = r.get("acao")
        if acao not in ACOES:
            erros.append(f"regra {i}: ação '{acao}' inválida (usa: {', '.join(sorted(ACOES))})")
        if not r.get("imagem"):
            erros.append(f"regra {i}: falta 'imagem'")
        elif not modelos.existe(r["imagem"]):
            erros.append(f"regra {i}: imagem em falta: templates/{r['imagem']} (cria-a com capturar.py)")
        if acao == "tocar_em" and len(r.get("posicao") or []) != 2:
            erros.append(f"regra {i}: 'tocar_em' precisa de 'posicao: [x, y]' (frações de 0 a 1)")
    return erros


def encontrar_regra(ecra_cinza, regras, modelos, limiar_geral, usos):
    for r in regras:
        if r.get("max_vezes") and usos[id(r)] >= r["max_vezes"]:
            continue
        res = procurar(ecra_cinza, modelos.obter(r["imagem"]), r.get("regiao"))
        if res and res[2] >= r.get("limiar", limiar_geral):
            return r, res
    return None, None


def correr_tarefa(adb, config, nome):
    tarefas = config.get("tarefas") or {}
    if nome not in tarefas:
        log(f"Tarefa '{nome}' não existe. Disponíveis: {', '.join(tarefas)}")
        return 1
    tarefa = tarefas[nome]
    geral = config.get("geral") or {}
    modelos = Modelos(os.path.join(PASTA, "templates"))

    erros = validar_tarefa(nome, tarefa, modelos)
    if erros:
        log("Configuração com problemas:")
        for e in erros:
            print("   -", e)
        return 1

    regras = tarefa["regras"]
    limiar = geral.get("limiar", 0.85)
    pausa = geral.get("pausa", 1.5)
    tempo_max_desconhecido = geral.get("tempo_max_desconhecido", 90)
    max_seguidas_geral = geral.get("max_seguidas", 8)
    repeticoes = tarefa.get("repeticoes")
    max_minutos = tarefa.get("max_minutos")

    log(f"A iniciar '{nome}'" + (f" ({repeticoes} repetições)" if repeticoes else "") + ". Ctrl+C para parar.")
    inicio = ultimo_reconhecido = time.time()
    usos = Counter()
    contagem = 0
    ultima, seguidas = None, 0
    ecra = None

    try:
        while True:
            if max_minutos and time.time() - inicio > max_minutos * 60:
                log(f"Tempo máximo de {max_minutos} min atingido.")
                break

            ecra = adb.capturar()
            regra, res = encontrar_regra(cinzento(ecra), regras, modelos, limiar, usos)

            if regra is None:
                if time.time() - ultimo_reconhecido > tempo_max_desconhecido:
                    caminho = guardar_captura(ecra, "desconhecido")
                    log(f"Nenhum ecrã reconhecido há {tempo_max_desconhecido}s. A parar. Captura: {caminho}")
                    break
                time.sleep(pausa)
                continue

            ultimo_reconhecido = time.time()
            seguidas = seguidas + 1 if regra is ultima else 1
            ultima = regra
            acao = regra["acao"]
            rotulo = regra.get("nome") or regra["imagem"]
            x, y, pont = res

            if acao == "parar":
                precisa = regra.get("confirmar", 1)
                if seguidas >= precisa:
                    log(f"'{rotulo}' detetado: tarefa concluída.")
                    break
                time.sleep(pausa)
                continue

            max_seguidas = regra.get("max_seguidas", max_seguidas_geral)
            if acao != "esperar" and seguidas > max_seguidas:
                caminho = guardar_captura(ecra, "preso")
                log(f"'{rotulo}' apareceu {seguidas} vezes seguidas; parece preso. A parar. Captura: {caminho}")
                break

            usos[id(regra)] += 1
            if acao == "tocar":
                adb.tocar(x, y)
                log(f"{rotulo}: toquei em ({x}, {y}) [{pont:.2f}]")
            elif acao == "tocar_em":
                altura, largura = ecra.shape[:2]
                px, py = regra["posicao"]
                adb.tocar(px * largura, py * altura)
                log(f"{rotulo}: toquei na posição {px:.3f}, {py:.3f}")
            elif acao == "voltar":
                adb.voltar()
                log(f"{rotulo}: botão voltar")
            elif acao == "esperar":
                log(f"{rotulo}: a aguardar")

            if regra.get("contar") and seguidas == 1:
                contagem += 1
                log(f"=== Repetição {contagem}" + (f"/{repeticoes}" if repeticoes else "") + " ===")
                if repeticoes and contagem >= repeticoes:
                    log("Número de repetições atingido.")
                    break

            time.sleep(regra.get("espera", pausa))
    except KeyboardInterrupt:
        log("Parado pelo utilizador.")

    minutos = (time.time() - inicio) / 60
    log(f"Fim. Duração: {minutos:.1f} min" + (f", repetições: {contagem}" if tarefa_conta(regras) else ""))
    return 0


def tarefa_conta(regras):
    return any(r.get("contar") for r in regras)


def testar(adb, config):
    """Captura o ecrã e mostra a pontuação de cada imagem de referência."""
    geral = config.get("geral") or {}
    limiar = geral.get("limiar", 0.85)
    modelos = Modelos(os.path.join(PASTA, "templates"))
    nomes = modelos.todos()
    ecra = adb.capturar()
    caminho = guardar_captura(ecra, "teste")
    altura, largura = ecra.shape[:2]
    log(f"Ecrã {largura}x{altura}, captura guardada em {caminho}")
    if not nomes:
        log("A pasta templates está vazia. Cria imagens com capturar.py.")
        return 0
    ecra_cinza = cinzento(ecra)
    for nome in nomes:
        res = procurar(ecra_cinza, modelos.obter(nome))
        if res is None:
            print(f"   {nome:30s}  (maior que o ecrã)")
            continue
        x, y, pont = res
        marca = "SIM" if pont >= limiar else "não"
        print(f"   {nome:30s}  {pont:.2f}  {marca:3s}  em ({x}, {y})")
    return 0


def main():
    p = argparse.ArgumentParser(description="Bot WWE SuperCard")
    p.add_argument("--config", default=os.path.join(PASTA, "config.yaml"))
    sub = p.add_subparsers(dest="comando", required=True)
    t = sub.add_parser("tarefa", help="corre uma tarefa do config.yaml")
    t.add_argument("nome")
    sub.add_parser("testar", help="mostra as imagens reconhecidas no ecrã atual")
    sub.add_parser("listar", help="lista as tarefas")
    args = p.parse_args()

    config = carregar_config(args.config)
    if args.comando == "listar":
        for nome, t in (config.get("tarefas") or {}).items():
            print(f"- {nome}: {t.get('descricao', '')}")
        return 0

    cfg_adb = config.get("adb") or {}
    adb = ADB(cfg_adb.get("caminho"), cfg_adb.get("dispositivo"))
    try:
        adb.verificar()
        if args.comando == "testar":
            return testar(adb, config)
        return correr_tarefa(adb, config, args.nome)
    except ErroADB as e:
        log(f"Erro do ADB: {e}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
