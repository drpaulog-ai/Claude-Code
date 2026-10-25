// Verifica, no GitHub Actions, que as fontes de dados respondem e que os números fazem sentido.
import fs from 'node:fs';
import vm from 'node:vm';

const ctx = {};
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(new URL('../app/src/main/assets/sources.js', import.meta.url), 'utf8') + ';this.SOURCES=SOURCES;', ctx);
const SRC = ctx.SOURCES.make(fetch, { 'User-Agent': 'CapaDesfocada/1.0 (https://github.com/drpaulog-ai/Claude-Code)' });

const t0 = Date.now();
const films = await SRC.films();
console.log(`Filmes: ${films.length} em ${Date.now() - t0} ms`);
const pick = q => films.find(f => f.id === q);
for (const q of ['Q44578', 'Q24871', 'Q47703', 'Q83495']) console.log(q, JSON.stringify(pick(q)));
console.log('Com título em português:', films.filter(f => f.title !== f.en).length);

const o = ctx.SOURCES.oscarTable(films);
const byN = {};
for (const n of Object.values(o)) byN[n] = (byN[n] || 0) + 1;
console.log(`Óscares: ${Object.keys(o).length} filmes da lista estão na tabela. Por número de óscares:`, JSON.stringify(byN));
console.log('Titanic', o.Q44578, '· Matrix', o.Q83495, '· O Padrinho', o.Q47703);

const st = await SRC.stats(['Q44578', 'Q24871', 'Q47703', 'Q83495']);
console.log('Bilheteira/orçamento/duração:', JSON.stringify(st));

const sample = films.slice(0, 50);
const ps = await SRC.posters(sample.map(f => f.art));
console.log(`Cartazes da Wikipédia: ${Object.keys(ps).length} de ${sample.length}`);
console.log('Exemplo:', Object.entries(ps).slice(0, 3));

// Desafiar resposta: cartaz do Titanic. Quem escolheu Avatar não tem razão; quem escolheu Titanic (com o jogo a dizer Avatar) tem.
const titanic = pick('Q44578'), avatar = pick('Q24871');
if (titanic && avatar) {
  const src = (await SRC.posters([titanic.art]))[titanic.art];
  for (const [game, player, esperado] of [[titanic, avatar, 'jogo'], [avatar, titanic, 'jogador']]) {
    const r = await SRC.verifyPoster({ src, game, player });
    console.log(`Desafio: jogo diz ${game.en}, jogador diz ${player.en} → ${r.playerRight ? 'jogador' : 'jogo'} tem razão (esperado: ${esperado})`);
    for (const c of r.checks) console.log('   ', c.verdict || '-', '|', c.source, '|', c.detail);
  }
}
