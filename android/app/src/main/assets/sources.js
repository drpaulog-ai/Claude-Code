// Fontes de dados gratuitas e sem chave:
//  - Wikidata (CC0): lista de filmes, títulos em português, datas, bilheteira, orçamento, duração e óscares
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

  // Óscares ganhos: prémios (P166) que são categorias dos Óscares (Q19020)
  const Q_OSCARS = `SELECT ?film (COUNT(DISTINCT ?award) AS ?n) WHERE {
  ?film wdt:P31 wd:Q11424; wdt:P166 ?award .
  ?award wdt:P31|wdt:P279 wd:Q19020 .
} GROUP BY ?film`;

  // Bilheteira e orçamento em dólares (Q4917), duração em minutos (Q7727)
  const qStats = ids => `SELECT ?film (MAX(?b) AS ?box) (MAX(?c) AS ?cost) (MAX(?d) AS ?dur) WHERE {
  VALUES ?film { ${ids.map(i => 'wd:' + i).join(' ')} }
  OPTIONAL { ?film p:P2142/psv:P2142 [ wikibase:quantityAmount ?b; wikibase:quantityUnit wd:Q4917 ] }
  OPTIONAL { ?film p:P2130/psv:P2130 [ wikibase:quantityAmount ?c; wikibase:quantityUnit wd:Q4917 ] }
  OPTIONAL { ?film p:P2047/psv:P2047 [ wikibase:quantityAmount ?d; wikibase:quantityUnit wd:Q7727 ] }
} GROUP BY ?film`;

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
    async function oscars() {
      const rows = await sparql(Q_OSCARS);
      const out = {};
      for (const r of rows) out[qid(r.film.value)] = +r.n.value;
      return out;
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
    return { films, oscars, stats, posters, tmdbPoster };
  }
  return { make };
})();
