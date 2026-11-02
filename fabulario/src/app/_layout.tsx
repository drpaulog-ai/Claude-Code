import { Stack } from "expo-router";
import { StatusBar } from "expo-status-bar";
import { cores } from "../lib/tema";

export default function Layout() {
  return (
    <>
      <StatusBar style="dark" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: cores.fundo },
          headerTintColor: cores.texto,
          headerShadowVisible: false,
          contentStyle: { backgroundColor: cores.fundo },
        }}
      >
        <Stack.Screen name="index" options={{ title: "Fabulário" }} />
        <Stack.Screen name="criar" options={{ title: "Nova história" }} />
        <Stack.Screen name="importar" options={{ title: "Importar história" }} />
        <Stack.Screen name="historia/[id]" options={{ title: "" }} />
        <Stack.Screen name="manga/[id]" options={{ title: "Manga" }} />
        <Stack.Screen name="vocabulario" options={{ title: "Meu vocabulário" }} />
        <Stack.Screen name="configuracoes" options={{ title: "Configurações" }} />
      </Stack>
    </>
  );
}
