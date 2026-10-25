// Verifica, no GitHub Actions, que as fontes de «Onde Ver» respondem como a app espera.
import fs from 'node:fs';
import vm from 'node:vm';

const ctx = { URL };
vm.createContext(ctx);
vm.runInContext(fs.readFileSync(new URL('../app/src/main/assets/sources.js', import.meta.url), 'utf8') + ';this.SOURCES=SOURCES;', ctx);
const SRC = ctx.SOURCES.make(fetch, { 'User-Agent': 'OndeVer/1.0 (https://github.com/drpaulog-ai/Claude-Code)' });
const step = async (name, fn) => { try { console.log(`\n## ${name}`); await fn(); } catch (e) { console.log('FALHOU:', e.message); } };

await step('Wikidata: pesquisa em várias línguas', async () => {
  for (const q of ['O Padrinho', 'La casa de papel', 'Le Fabuleux Destin d\'Amélie Poulain', '千と千尋の神隠し', 'Avenida Brasil']) {
    const r = await SRC.wdSearch(q);
    console.log(q, '→', r.slice(0, 3).map(x => `${x.title} (${x.year}) ${x.wikidata} imdb=${x.imdb} tmdb=${x.tmdbMovie || x.tmdbTv} canais=${x.broadcasters.join(',')} serviços=${x.services.map(s => s.prop + ':' + s.id).join(',')}`).join(' | '));
  }
  const props = await SRC.wdServiceProps();
  console.log('Propriedades de serviços:', JSON.stringify(props));
  const l = await SRC.wdLabels(['Q1153416', 'Q1062244']);
  console.log('Etiquetas:', JSON.stringify(l));
});

await step('Capas da Wikipédia', async () => {
  const w = await SRC.wikiPosters([['en', 'Avenida Brasil (TV series)'], ['pt', 'Avenida Brasil (telenovela)'], ['en', 'Money Heist']]);
  console.log(JSON.stringify(w));
});

await step('TVmaze', async () => {
  const r = await SRC.tvmazeSearch('Dark');
  console.log('Pesquisa Dark →', r.slice(0, 3).map(s => `${s.title} (${s.year}) ${s.channel && s.channel.name} imdb=${s.imdb}`).join(' | '));
  const s = await SRC.tvmazeByImdb('tt5753856');
  console.log('Dark por IMDb →', s && s.title, JSON.stringify(s && s.channel), 'akas:', s && s.akas.slice(0, 5).map(a => a.name + '/' + a.where).join(', '));
  for (const imdb of ['tt0108778', 'tt11126994', 'tt0386676']) { const x = await SRC.tvmazeByImdb(imdb); console.log('Próximo episódio', imdb, x && x.title, JSON.stringify(x && x.nextEpisode)); }
  const n = await SRC.tvmazeSearch('Avenida Brasil');
  console.log('Avenida Brasil →', n.slice(0, 2).map(s => `${s.title} ${s.channel && s.channel.name}/${s.channel && s.channel.country}`).join(' | '));
});

await step('AniList', async () => {
  for (const q of ['進撃の巨人', 'Shingeki no Kyojin', 'Frieren']) {
    const r = await SRC.anilistSearch(q);
    console.log(q, '→', r.slice(0, 2).map(a => `${a.title} / ${a.original} (${a.year}) links: ${a.links.map(l => l.name + (l.lang ? '[' + l.lang + ']' : '')).join(', ')}`).join(' | '));
  }
});

if (process.env.TMDB_KEY) await step('TMDB', async () => {
  const t = SRC.tmdb(process.env.TMDB_KEY);
  const r = await t.search('O Padrinho', 'pt-PT');
  console.log('Pesquisa →', r.slice(0, 3).map(x => `${x.title} / ${x.original} ${x.kind}:${x.tmdb}`).join(' | '));
  const d = await t.details('movie', 238, 'pt-PT');
  console.log('Países com serviços:', Object.keys(d.providers).length, 'PT:', JSON.stringify(d.providers.PT && d.providers.PT.flatrate && d.providers.PT.flatrate.map(p => p.provider_name)));
});
else console.log('\n## TMDB\nSem o segredo TMDB_KEY no repositório: não testado.');
