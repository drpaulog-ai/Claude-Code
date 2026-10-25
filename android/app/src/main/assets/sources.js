// Fontes de dados gratuitas e sem chave:
//  - Wikidata (CC0): lista de filmes, títulos em português, datas, bilheteira, orçamento e duração
//  - Wikipédia: cartazes (imagem principal do artigo)
// O TMDB é opcional e só entra se o jogador puser a sua chave.
var SOURCES = (function () {
  const WD = 'https://query.wikidata.org/sparql';
  const WP = 'https://en.wikipedia.org/w/api.php';
  const TMDB = 'https://api.themoviedb.org/3';

  // Filmes conhecidos: com artigo em pelo menos 60 Wikipédias e artigo na Wikipédia inglesa (para o cartaz)
  const Q_LIST = `SELECT ?film ?pt ?en ?date ?art ?sl ?imdb WHERE {
  ?film wdt:P31 wd:Q11424; wikibase:sitelinks ?sl .
  FILTER(?sl >= 60)
  ?film wdt:P577 ?date .
  ?art schema:about ?film; schema:isPartOf <https://en.wikipedia.org/> .
  ?film rdfs:label ?en . FILTER(LANG(?en) = "en")
  OPTIONAL { ?film rdfs:label ?pt . FILTER(LANG(?pt) = "pt") }
  OPTIONAL { ?film wdt:P345 ?imdb }
}`;

  // Bilheteira e orçamento em dólares (Q4917), duração em minutos (Q7727)
  const qStats = ids => `SELECT ?film (MAX(?b) AS ?box) (MAX(?c) AS ?cost) (MAX(?d) AS ?dur) WHERE {
  VALUES ?film { ${ids.map(i => 'wd:' + i).join(' ')} }
  OPTIONAL { ?film p:P2142/psv:P2142 [ wikibase:quantityAmount ?b; wikibase:quantityUnit wd:Q4917 ] }
  OPTIONAL { ?film p:P2130/psv:P2130 [ wikibase:quantityAmount ?c; wikibase:quantityUnit wd:Q4917 ] }
  OPTIONAL { ?film p:P2047/psv:P2047 [ wikibase:quantityAmount ?d; wikibase:quantityUnit wd:Q7727 ] }
} GROUP BY ?film`;

  // Vitórias nos Óscares. A Wikidata tem estes prémios incompletos (Titanic aparece com 8 em vez de 11),
  // por isso o desafio de óscares usa esta tabela, ligada aos filmes da Wikidata pelo título inglês e pelo ano.
  const OSCARS = [
    ['Titanic',1997,11],['Ben-Hur',1959,11],['The Lord of the Rings: The Return of the King',2003,11],
    ['The English Patient',1996,9],['The Last Emperor',1987,9],
    ['Slumdog Millionaire',2008,8],['Amadeus',1984,8],['Gandhi',1982,8],
    ['Oppenheimer',2023,7],['Everything Everywhere All at Once',2022,7],["Schindler's List",1993,7],['Gravity',2013,7],['Lawrence of Arabia',1962,7],['Dances with Wolves',1990,7],['Shakespeare in Love',1998,7],['Out of Africa',1985,7],['The Sting',1973,7],
    ['Forrest Gump',1994,6],['La La Land',2016,6],['Mad Max: Fury Road',2015,6],['Dune',2021,6],['Star Wars',1977,6],['The Godfather Part II',1974,6],['Chicago',2002,6],['The Hurt Locker',2008,6],
    ['Gladiator',2000,5],['The Silence of the Lambs',1991,5],['Braveheart',1995,5],['American Beauty',1999,5],['Saving Private Ryan',1998,5],['The Artist',2011,5],["One Flew Over the Cuckoo's Nest",1975,5],['The Aviator',2004,5],['Hugo',2011,5],['Anora',2024,5],
    ['Parasite',2019,4],['Inception',2010,4],['The Matrix',1999,4],['The Departed',2006,4],['The Shape of Water',2017,4],['No Country for Old Men',2007,4],['A Beautiful Mind',2001,4],['Million Dollar Baby',2004,4],["The King's Speech",2010,4],['Birdman',2014,4],['Life of Pi',2012,4],['Bohemian Rhapsody',2018,4],['Poor Things',2023,4],['The Lord of the Rings: The Fellowship of the Ring',2001,4],
    ['The Godfather',1972,3],['Avatar',2009,3],['Jurassic Park',1993,3],['Black Panther',2018,3],['Whiplash',2014,3],['The Revenant',2015,3],['Green Book',2018,3],['Moonlight',2016,3],['Argo',2012,3],['12 Years a Slave',2013,3],['Rocky',1976,3],['Nomadland',2020,3],['CODA',2021,3],['Casablanca',1942,3],['E.T. the Extra-Terrestrial',1982,4],['Raiders of the Lost Ark',1981,4],
    ['The Dark Knight',2008,2],['Joker',2019,2],['Coco',2017,2],['Up',2009,2],['Frozen',2013,2],['Spotlight',2015,2],['Good Will Hunting',1997,2],['Dune: Part Two',2024,2],['Toy Story 3',2010,2],['The Lord of the Rings: The Two Towers',2002,2],['Terminator 2: Judgment Day',1991,4],['Alien',1979,1],['Aliens',1986,2],
    ['Pulp Fiction',1994,1],['Interstellar',2014,1],['Spirited Away',2001,1],['Top Gun: Maverick',2022,1],['Barbie',2023,1],['Inside Out',2015,1],['Spider-Man: Into the Spider-Verse',2018,1],['Back to the Future',1985,1],['Goodfellas',1990,1],['The Lion King',1994,2],['Django Unchained',2012,2],['Inglourious Basterds',2009,1],
    ['The Shawshank Redemption',1994,0],['Fight Club',1999,0],['Avengers: Endgame',2019,0],['Se7en',1995,0],['The Green Mile',1999,0],['The Wolf of Wall Street',2013,0],['Taxi Driver',1976,0],['Avengers: Infinity War',2018,0],['The Avengers',2012,0],['Harry Potter and the Deathly Hallows – Part 2',2011,0],['Star Wars: The Force Awakens',2015,0],['Psycho',1960,0]
  ];
  const norm = t => t.toLowerCase().replace(/[^a-z0-9]/g, '');
  // { id da Wikidata: número de óscares } para os filmes da lista que estão na tabela
  function oscarTable(films) {
    const out = {};
    for (const [t, y, n] of OSCARS) {
      const f = films.find(m => norm(m.en) === norm(t) && Math.abs(+m.date.slice(0, 4) - y) <= 1);
      if (f) out[f.id] = n;
    }
    return out;
  }

  const qid = u => u.slice(u.lastIndexOf('/') + 1);

  function make(fetchFn, headers) {
    headers = headers || {};
    async function json(url, extra) {
      const r = await fetchFn(url, { headers: Object.assign({}, headers, extra || {}) });
      if (r.status === 401) throw new Error('chave');
      if (!r.ok) throw new Error('HTTP ' + r.status + ' em ' + url.split('?')[0]);
      return r.json();
    }
    async function sparql(q) {
      const d = await json(WD + '?format=json&query=' + encodeURIComponent(q));
      return d.results.bindings;
    }
    async function films() {
      const rows = await sparql(Q_LIST);
      const map = new Map();
      for (const r of rows) {
        const id = qid(r.film.value), date = r.date.value.slice(0, 10);
        const prev = map.get(id);
        if (prev) { if (date < prev.date) prev.date = date; continue; }
        const art = decodeURIComponent(r.art.value.split('/wiki/')[1]).replace(/_/g, ' ');
        map.set(id, { id, title: (r.pt && r.pt.value) || r.en.value, en: r.en.value, date, art, sl: +r.sl.value, imdb: r.imdb ? r.imdb.value : null });
      }
      return [...map.values()].filter(m => /^\d{4}-/.test(m.date) && +m.date.slice(0, 4) > 1900);
    }
    async function stats(ids) {
      const rows = await sparql(qStats(ids));
      const out = {};
      for (const r of rows) out[qid(r.film.value)] = { box: r.box ? +r.box.value : 0, cost: r.cost ? +r.cost.value : 0, dur: r.dur ? +r.dur.value : 0 };
      return out;
    }
    // Cartazes da Wikipédia, em lotes de 50 artigos. Devolve { título do artigo: url da imagem }
    async function posters(titles, size) {
      const out = {};
      for (let i = 0; i < titles.length; i += 50) {
        const batch = titles.slice(i, i + 50);
        const d = await json(WP + '?action=query&prop=pageimages&piprop=thumbnail&pilicense=any&redirects=1&format=json&formatversion=2&origin=*&pithumbsize=' + (size || 500) + '&titles=' + encodeURIComponent(batch.join('|')));
        const q = d.query || {};
        const alias = {};
        for (const n of (q.normalized || [])) alias[n.to] = n.from;
        for (const n of (q.redirects || [])) alias[n.to] = alias[n.from] || n.from;
        for (const p of (q.pages || [])) if (p.thumbnail) out[alias[p.title] || p.title] = p.thumbnail.source;
      }
      return out;
    }
    // TMDB (opcional): cartaz sem texto quando existe, procurado pelo código IMDb que vem da Wikidata
    async function tmdbPoster(key, imdb) {
      const auth = key.startsWith('eyJ') ? { Authorization: 'Bearer ' + key } : null;
      const k = auth ? '' : '&api_key=' + encodeURIComponent(key);
      const f = await json(TMDB + '/find/' + imdb + '?external_source=imdb_id' + k, auth);
      const m = (f.movie_results || [])[0];
      if (!m) return null;
      const im = await json(TMDB + '/movie/' + m.id + '/images?include_image_language=null' + k, auth);
      const ps = (im.posters || []).sort((a, b) => b.vote_average - a.vote_average);
      if (ps.length) return { src: 'https://image.tmdb.org/t/p/w500' + ps[0].file_path, textless: true };
      return m.poster_path ? { src: 'https://image.tmdb.org/t/p/w500' + m.poster_path, textless: false } : null;
    }
    // ---------- Desafiar resposta ----------
    // Nome do ficheiro de uma imagem da Wikimedia, a partir do url (normal ou miniatura)
    function fileOf(src) {
      const p = new URL(src).pathname.split('/');
      const i = p.indexOf('thumb');
      return decodeURIComponent(i >= 0 ? p[i + 3] : p[p.length - 1]).replace(/_/g, ' ');
    }
    const words = t => t.toLowerCase().replace(/\(.*?\)/g, ' ').split(/[^a-z0-9]+/).filter(w => w.length > 2 && !['the', 'and', 'film', 'poster', 'part'].includes(w));
    async function tmdbFiles(key, imdb) {
      const auth = key.startsWith('eyJ') ? { Authorization: 'Bearer ' + key } : null;
      const k = auth ? '' : '&api_key=' + encodeURIComponent(key);
      const f = await json(TMDB + '/find/' + imdb + '?external_source=imdb_id' + k, auth);
      const m = (f.movie_results || [])[0];
      if (!m) return [];
      const im = await json(TMDB + '/movie/' + m.id + '/images' + (k ? '?' + k.slice(1) : ''), auth);
      return (im.posters || []).map(p => p.file_path).concat(m.poster_path ? [m.poster_path] : []);
    }
    // O jogador diz que o cartaz é de `player` e não de `game`. Cada fonte dá razão ao jogo, ao jogador, ou não decide (null).
    async function verifyPoster({ src, game, player, key }) {
      const checks = [];
      const add = async (source, fn) => {
        try { checks.push(Object.assign({ source }, await fn())); }
        catch (e) { checks.push({ source, verdict: null, detail: 'Não respondeu (' + e.message + ').' }); }
      };
      if (src.includes('image.tmdb.org')) {
        const path = src.slice(src.indexOf('/t/p/') + 5).replace(/^[^/]+/, '');
        await add('TMDB: imagens de cada filme', async () => {
          if (!key) return { verdict: null, detail: 'Sem chave do TMDB.' };
          const [g, p] = await Promise.all([game.imdb ? tmdbFiles(key, game.imdb) : [], player.imdb ? tmdbFiles(key, player.imdb) : []]);
          if (p.includes(path) && !g.includes(path)) return { verdict: 'jogador', detail: `O cartaz está nas imagens de «${player.en}».` };
          if (g.includes(path)) return { verdict: 'jogo', detail: `O cartaz está nas imagens de «${game.en}».` };
          return { verdict: null, detail: 'O cartaz não aparece em nenhum dos dois filmes.' };
        });
      } else {
        const file = fileOf(src);
        await add('Wikipédia: artigos que usam esta imagem', async () => {
          const d = await json(WP + '?action=query&list=imageusage&iunamespace=0&iulimit=50&format=json&formatversion=2&origin=*&iutitle=' + encodeURIComponent('File:' + file));
          const used = (d.query && d.query.imageusage || []).map(p => p.title);
          const g = used.includes(game.art), p = used.includes(player.art);
          if (p && !g) return { verdict: 'jogador', detail: `A imagem é usada no artigo «${player.art}» e não no de «${game.art}».` };
          if (g && !p) return { verdict: 'jogo', detail: `A imagem é usada no artigo «${game.art}».` };
          return { verdict: null, detail: g ? 'A imagem é usada nos artigos dos dois filmes.' : 'Nenhum dos dois artigos usa esta imagem.' };
        });
        await add(`Wikipédia: imagem principal de «${player.art}»`, async () => {
          const r = await posters([player.art]);
          if (!r[player.art]) return { verdict: null, detail: 'Esse artigo não tem imagem principal.' };
          return fileOf(r[player.art]) === file
            ? { verdict: 'jogador', detail: 'O artigo do filme que escolheste mostra exatamente este cartaz.' }
            : { verdict: 'jogo', detail: 'O artigo do filme que escolheste mostra outro cartaz.' };
        });
        await add('Wikidata: imagem e cartaz registados', async () => {
          const rows = await sparql(`SELECT ?film ?img WHERE { VALUES ?film { wd:${game.id} wd:${player.id} } ?film wdt:P3383|wdt:P18 ?img }`);
          const files = {};
          for (const r of rows) (files[qid(r.film.value)] = files[qid(r.film.value)] || []).push(decodeURIComponent(r.img.value.split('/').pop()).replace(/_/g, ' '));
          if ((files[player.id] || []).includes(file)) return { verdict: 'jogador', detail: `A Wikidata liga este cartaz a «${player.en}».` };
          if ((files[game.id] || []).includes(file)) return { verdict: 'jogo', detail: `A Wikidata liga este cartaz a «${game.en}».` };
          return { verdict: null, detail: 'A Wikidata não tem este cartaz em nenhum dos dois filmes.' };
        });
        await add('Nome do ficheiro', async () => {
          const f = file.toLowerCase(), hit = t => words(t).filter(w => f.includes(w)).length / Math.max(1, words(t).length);
          const g = hit(game.en), p = hit(player.en);
          if (p > g && p >= .5) return { verdict: 'jogador', detail: `«${file}» corresponde mais a «${player.en}».` };
          if (g > p && g >= .5) return { verdict: 'jogo', detail: `«${file}» corresponde mais a «${game.en}».` };
          return { verdict: null, detail: `«${file}» não decide.` };
        });
      }
      const pro = checks.filter(c => c.verdict === 'jogador').length, con = checks.filter(c => c.verdict === 'jogo').length;
      // Em caso de empate fica a resposta do jogo: o cartaz veio do artigo desse filme.
      return { checks, playerRight: pro > con };
    }

    return { films, stats, posters, tmdbPoster, verifyPoster, fileOf };
  }
  return { make, oscarTable };
})();
