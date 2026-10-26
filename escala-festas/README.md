# Escala das Festas

App de uma só página para criar a escala rotativa de 24, 25, 30 e 31 de dezembro.

Abra `index.html` no browser. O assistente pergunta, por esta ordem:

1. **Dias e turnos** – datas (24/25 = bloco Natal, 30/31 = bloco Ano Novo, editáveis), turnos com horário e pessoas necessárias por turno em cada dia.
2. **Equipa** – número total de funcionários, nomes, dias de indisponibilidade e o bloco que cada um fez no ano passado.
3. **Regras** – um turno por dia, descanso mínimo entre turnos (11 h por defeito), máximo de dias seguidos, máximo de turnos por pessoa, separação Natal/Ano Novo (obrigatória ou preferência), rotação face ao ano passado e distribuição equilibrada das noites.
4. **Escala** – gera a melhor de centenas de tentativas, mostra a grelha por turno e por funcionário, assinala turnos por preencher e permite copiar tudo para Excel.

As configurações ficam guardadas no browser (localStorage). Não precisa de servidor nem de instalação.
