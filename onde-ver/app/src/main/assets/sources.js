// Fontes de dados de «Onde Ver»:
//  - TMDB (chave gratuita): pesquisa, títulos noutras línguas e serviços por país (dados da JustWatch)
//  - Wikidata (sem chave): pesquisa em todas as línguas, título original, canal de estreia, IDs de serviços
//  - TVmaze (sem chave): canal ou serviço de séries e novelas, nomes noutros países
//  - AniList (sem chave): anime, títulos em japonês e romaji, links de streaming
var SOURCES = (function () {
  const TMDB = 'https://api.themoviedb.org/3';
  const WD = 'https://www.wikidata.org/w/api.php';
  const TVM = 'https://api.tvmaze.com';
  const ANI = 'https://graphql.anilist.co';
  const IMG = 'https://image.tmdb.org/t/p/';

  // Propriedades da Wikidata com o ID do título num serviço de streaming, e o nome do serviço.
  // O formato do link (P1630) vem da própria Wikidata.
  const WD_SERVICE_NAMES = { P1874: 'Netflix', P8055: 'Prime Video', P7595: 'Disney+', P9586: 'Apple TV', P11460: 'Plex' };
  const WD_SERVICE_PROPS = Object.keys(WD_SERVICE_NAMES);

  const norm = t => (t || '').toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[^a-z0-9぀-ヿ一-鿿가-힯]/g, '');

  function make(fetchFn, headers) {
    headers = headers || {};
    async function json(url, opts) {
      opts = opts || {};
      const r = await fetchFn(url, Object.assign({}, opts, { headers: Object.assign({}, headers, opts.headers || {}) }));
      if (r.status === 401) throw new Error('chave');
      if (r.status === 404) return null;
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    }

    /* ---------- TMDB ---------- */
    function tmdb(key) {
      const bearer = key.startsWith('eyJ');
      const get = (path, params) => {
        const u = new URL(TMDB + path);
        for (const [k, v] of Object.entries(params || {})) if (v != null) u.searchParams.set(k, v);
        if (!bearer) u.searchParams.set('api_key', key);
        return json(u.toString(), bearer ? { headers: { Authorization: 'Bearer ' + key } } : {});
      };
      return {
        async search(q, lang) {
          const d = await get('/search/multi', { query: q, language: lang, include_adult: false });
          return (d.results || []).filter(r => r.media_type === 'movie' || r.media_type === 'tv').map(r => ({
            kind: r.media_type, tmdb: r.id,
            title: r.title || r.name, original: r.original_title || r.original_name,
            year: (r.release_date || r.first_air_date || '').slice(0, 4),
            poster: r.poster_path ? IMG + 'w185' + r.poster_path : null,
            anime: (r.genre_ids || []).includes(16) && r.original_language === 'ja',
            lang: r.original_language, pop: r.popularity
          }));
        },
        async details(kind, id, lang) {
          const d = await get(`/${kind}/${id}`, { language: lang, append_to_response: 'external_ids,alternative_titles,watch/providers,translations' + (kind === 'movie' ? ',release_dates' : '') });
          if (!d) return null;
          const alt = (d.alternative_titles && (d.alternative_titles.titles || d.alternative_titles.results)) || [];
          const tr = (d.translations && d.translations.translations) || [];
          const titles = [];
          for (const t of tr) { const n = (t.data && (t.data.title || t.data.name)); if (n) titles.push({ name: n, where: t.iso_3166_1, lang: t.iso_639_1 }); }
          for (const t of alt) titles.push({ name: t.title, where: t.iso_3166_1 });
          return {
            title: d.title || d.name, original: d.original_title || d.original_name,
            year: (d.release_date || d.first_air_date || '').slice(0, 4),
            overview: d.overview, poster: d.poster_path ? IMG + 'w342' + d.poster_path : null,
            networks: (d.networks || []).map(n => n.name),
            ids: Object.assign({ tmdb: id }, d.external_ids || {}),
            titles, providers: (d['watch/providers'] && d['watch/providers'].results) || {},
            // filmes: datas por país e tipo (3 cinema, 4 digital, 5 físico, 6 TV)
            releases: ((d.release_dates && d.release_dates.results) || []).flatMap(r => r.release_dates.map(x => ({ country: r.iso_3166_1, type: x.type, date: x.release_date.slice(0, 10), note: x.note }))),
            // séries: próximo episódio e estado
            nextEpisode: d.next_episode_to_air ? { date: d.next_episode_to_air.air_date, season: d.next_episode_to_air.season_number, episode: d.next_episode_to_air.episode_number } : null,
            firstAir: d.first_air_date || null, status: d.status || null,
            hasPortuguese: tr.some(t => t.iso_639_1 === 'pt')
          };
        },
        async findByImdb(imdb) {
          const d = await get('/find/' + imdb, { external_source: 'imdb_id' });
          const m = d && ((d.movie_results || [])[0] ? ['movie', d.movie_results[0]] : (d.tv_results || [])[0] ? ['tv', d.tv_results[0]] : null);
          return m ? { kind: m[0], tmdb: m[1].id } : null;
        }
      };
    }

    /* ---------- Wikidata: pesquisa em todas as línguas ---------- */
    async function wdSearch(q) {
      // filmes e séries têm código IMDb (P345); a pesquisa cobre títulos e nomes alternativos em todas as línguas
      const s = await json(WD + '?action=query&list=search&srlimit=8&format=json&origin=*&srsearch=' + encodeURIComponent(q + ' haswbstatement:P345'));
      const ids = ((s && s.query && s.query.search) || []).map(r => r.title);
      if (!ids.length) return [];
      return wdEntities(ids);
    }
    async function wdEntities(ids) {
      const d = await json(WD + '?action=wbgetentities&props=labels|aliases|claims|sitelinks&format=json&origin=*&ids=' + ids.join('|'));
      const out = [];
      for (const id of ids) {
        const e = d && d.entities && d.entities[id];
        if (!e || e.missing !== undefined) continue;
        const c = e.claims || {};
        const val = p => (c[p] || []).map(x => x.mainsnak && x.mainsnak.datavalue && x.mainsnak.datavalue.value).filter(Boolean);
        // só títulos (códigos IMDb tt…), não pessoas (nm…)
        if (!val('P345').some(v => /^tt/.test(v))) continue;
        const lab = l => e.labels && e.labels[l] && e.labels[l].value;
        const date = val('P577').concat(val('P580')).map(v => v.time).sort()[0] || '';
        const titles = [];
        for (const [l, v] of Object.entries(e.labels || {})) titles.push({ name: v.value, lang: l });
        for (const [l, vs] of Object.entries(e.aliases || {})) for (const v of vs) titles.push({ name: v.value, lang: l });
        const services = [];
        for (const p of WD_SERVICE_PROPS) for (const v of val(p)) services.push({ prop: p, id: v });
        const sl = e.sitelinks || {};
        out.push({
          wikidata: id, enwiki: sl.enwiki && sl.enwiki.title, ptwiki: sl.ptwiki && sl.ptwiki.title, date: date.slice(1, 11),
          title: lab('pt') || lab('pt-br') || lab('en') || Object.values(e.labels || {})[0]?.value || id,
          original: (val('P1476')[0] || {}).text || lab('en'),
          year: date.slice(1, 5),
          imdb: val('P345').find(v => /^tt/.test(v)) || null,
          tmdbMovie: val('P4947')[0] || null, tmdbTv: val('P4983')[0] || null,
          broadcasters: val('P449').map(v => v.id),
          countries: val('P495').map(v => v.id),
          titles, services
        });
      }
      return out;
    }
    // nomes em português dos itens (canais, países) referidos pela Wikidata
    async function wdLabels(ids) {
      if (!ids.length) return {};
      const d = await json(WD + '?action=wbgetentities&props=labels&languages=pt|en&format=json&origin=*&ids=' + ids.slice(0, 50).join('|'));
      const out = {};
      for (const [id, e] of Object.entries((d && d.entities) || {})) out[id] = (e.labels.pt || e.labels.en || {}).value || id;
      return out;
    }

    // Nome e formato de link de cada propriedade de serviço: { P1874: { name: 'Netflix ID', url: 'https://…/$1' } }
    let propInfo = null;
    async function wdServiceProps() {
      if (propInfo) return propInfo;
      const d = await json(WD + '?action=wbgetentities&props=labels|claims&languages=pt|en&format=json&origin=*&ids=' + WD_SERVICE_PROPS.join('|'));
      propInfo = {};
      for (const [id, e] of Object.entries((d && d.entities) || {})) {
        const f = ((e.claims || {}).P1630 || [])[0];
        propInfo[id] = { name: WD_SERVICE_NAMES[id] || id, url: f && f.mainsnak.datavalue ? f.mainsnak.datavalue.value : null };
      }
      return propInfo;
    }

    /* ---------- TVmaze ---------- */
    async function tvmazeSearch(q) {
      const d = await json(TVM + '/search/shows?q=' + encodeURIComponent(q));
      return (d || []).slice(0, 6).map(r => tvmazeShow(r.show));
    }
    function tvmazeShow(s) {
      const ch = s.network ? { name: s.network.name, country: s.network.country && s.network.country.code, kind: 'canal' }
        : s.webChannel ? { name: s.webChannel.name, country: s.webChannel.country && s.webChannel.country.code, kind: 'streaming' } : null;
      return {
        tvmaze: s.id, title: s.name, year: (s.premiered || '').slice(0, 4), imdb: s.externals && s.externals.imdb,
        poster: s.image && s.image.medium, channel: ch, url: s.officialSite, type: s.type, lang: s.language
      };
    }
    async function tvmazeByImdb(imdb) {
      const s = await json(TVM + '/lookup/shows?imdb=' + imdb);
      if (!s) return null;
      const show = tvmazeShow(s);
      const [akas, full] = await Promise.all([json(TVM + '/shows/' + s.id + '/akas').catch(() => []), json(TVM + '/shows/' + s.id + '?embed=nextepisode').catch(() => null)]);
      show.akas = (akas || []).map(a => ({ name: a.name, where: a.country && a.country.code }));
      const ne = full && full._embedded && full._embedded.nextepisode;
      show.nextEpisode = ne ? { date: ne.airdate, season: ne.season, episode: ne.number, name: ne.name } : null;
      return show;
    }

    /* ---------- AniList ---------- */
    const ANI_Q = `query($q:String){Page(perPage:6){media(search:$q,type:ANIME){id idMal format seasonYear
      title{romaji english native} synonyms coverImage{medium}
      externalLinks{site url type language}}}}`;
    async function anilistSearch(q) {
      const d = await json(ANI, { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' }, body: JSON.stringify({ query: ANI_Q, variables: { q } }) });
      return ((d && d.data && d.data.Page.media) || []).map(m => ({
        anilist: m.id, title: m.title.english || m.title.romaji, original: m.title.native, romaji: m.title.romaji,
        year: m.seasonYear ? String(m.seasonYear) : '', poster: m.coverImage && m.coverImage.medium, format: m.format,
        titles: [m.title.romaji, m.title.english, m.title.native].concat(m.synonyms || []).filter(Boolean).map(n => ({ name: n })),
        links: (m.externalLinks || []).filter(l => l.type === 'STREAMING').map(l => ({ name: l.site, url: l.url, lang: l.language }))
      }));
    }

    // Capas da Wikipédia (imagem principal do artigo). titles: [[língua, título]]. Devolve { 'língua:título': url }
    async function wikiPosters(titles, size) {
      const out = {};
      for (const lang of ['en', 'pt']) {
        const list = titles.filter(t => t[0] === lang).map(t => t[1]);
        for (let i = 0; i < list.length; i += 50) {
          const d = await json(`https://${lang}.wikipedia.org/w/api.php?action=query&prop=pageimages&piprop=thumbnail&pilicense=any&redirects=1&format=json&formatversion=2&origin=*&pithumbsize=${size || 342}&titles=` + encodeURIComponent(list.slice(i, i + 50).join('|')));
          const q = (d && d.query) || {}, alias = {};
          for (const n of (q.normalized || [])) alias[n.to] = n.from;
          for (const n of (q.redirects || [])) alias[n.to] = alias[n.from] || n.from;
          for (const p of (q.pages || [])) if (p.thumbnail) out[lang + ':' + (alias[p.title] || p.title)] = p.thumbnail.source;
        }
      }
      return out;
    }

    return { tmdb, wikiPosters, wdSearch, wdEntities, wdLabels, wdServiceProps, tvmazeSearch, tvmazeByImdb, anilistSearch };
  }
  return { make, norm };
})();
