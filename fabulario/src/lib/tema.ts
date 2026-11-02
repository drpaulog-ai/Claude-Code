import { StyleSheet } from "react-native";

export const cores = {
  fundo: "#FFF8EE",
  cartao: "#FFFFFF",
  texto: "#2B2118",
  suave: "#7A6A5C",
  primaria: "#E2723B",
  primariaEscura: "#B9531F",
  destaque: "#FFD27A",
  fraseAtual: "#FFF0CC",
  borda: "#EEDFCB",
  perigo: "#C0392B",
};

export const estilos = StyleSheet.create({
  tela: { flex: 1, backgroundColor: cores.fundo },
  conteudo: { padding: 16, gap: 12 },
  titulo: { fontSize: 24, fontWeight: "700", color: cores.texto },
  subtitulo: { fontSize: 16, color: cores.suave },
  rotulo: { fontSize: 15, fontWeight: "600", color: cores.texto, marginTop: 8 },
  campo: {
    backgroundColor: cores.cartao,
    borderWidth: 1,
    borderColor: cores.borda,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    color: cores.texto,
  },
  cartao: {
    backgroundColor: cores.cartao,
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: cores.borda,
  },
  botao: {
    backgroundColor: cores.primaria,
    borderRadius: 14,
    paddingVertical: 14,
    alignItems: "center",
  },
  botaoTexto: { color: "#FFFFFF", fontSize: 17, fontWeight: "700" },
  botaoSecundario: {
    borderRadius: 14,
    paddingVertical: 12,
    alignItems: "center",
    borderWidth: 1.5,
    borderColor: cores.primaria,
  },
  botaoSecundarioTexto: { color: cores.primariaEscura, fontSize: 16, fontWeight: "600" },
  chips: { flexDirection: "row", flexWrap: "wrap", gap: 8 },
  erro: { color: cores.perigo, fontSize: 15 },
});
