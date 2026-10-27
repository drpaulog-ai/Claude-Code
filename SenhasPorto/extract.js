// Lê, da página oficial já carregada, quantas pessoas/senhas estão em espera em cada balcão.
// Não depende da estrutura exata do site: procura tabelas, grelhas e "cartões" com textos
// como "em espera", "a aguardar" ou "à sua frente" e o número que os acompanha.
(function (clickTexts) {
  'use strict';

  var norm = function (s) { return (s || '').replace(/\s+/g, ' ').trim(); };
  var COUNT_RE = /em\s+espera|a\s+aguardar|aguardam|à\s+(?:sua\s+)?frente|em\s+fila|na\s+fila|waiting/i;
  var COUNT_RE_G = /em\s+espera|a\s+aguardar|aguardam|à\s+(?:sua\s+)?frente|em\s+fila|na\s+fila|waiting/gi;
  var SKIP_TAGS = /^(SCRIPT|STYLE|NOSCRIPT|TEMPLATE|svg)$/i;

  var isCountText = function (s) {
    return !!s && s.length <= 80 && COUNT_RE.test(s) && !/tempo|time/i.test(s);
  };
  var toInt = function (s) {
    var m = /^\s*(\d{1,4})\s*$/.exec(s || '');
    return m ? parseInt(m[1], 10) : null;
  };
  var firstInt = function (s) {
    var m = /^\s*(\d{1,4})(?:\s|$)/.exec(s || '');
    return m ? parseInt(m[1], 10) : null;
  };
  var ownText = function (el) {
    var t = '';
    for (var i = 0; i < el.childNodes.length; i++) {
      if (el.childNodes[i].nodeType === 3) t += ' ' + el.childNodes[i].nodeValue;
    }
    return norm(t);
  };
  var lines = function (root) {
    var out = [];
    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
      acceptNode: function (n) {
        var p = n.parentElement;
        if (!p || SKIP_TAGS.test(p.tagName)) return NodeFilter.FILTER_REJECT;
        return norm(n.nodeValue) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
      }
    });
    while (walker.nextNode()) out.push(norm(walker.currentNode.nodeValue));
    return out;
  };

  // 1) Abrir separadores como "Balcões e senhas" (só uma vez por carregamento da página).
  var clicked = false;
  if (!window.__senhasPortoClicked && clickTexts.length) {
    window.__senhasPortoClicked = true;
    var wanted = clickTexts.map(function (t) { return norm(t).toLowerCase(); });
    var all = document.querySelectorAll('a, button, [role="tab"], [role="button"], li, span, label, div, h2, h3, h4');
    for (var i = 0; i < all.length; i++) {
      var el = all[i];
      var t = norm(el.textContent).toLowerCase();
      if (wanted.indexOf(t) < 0) continue;
      // Só o elemento mais interior com esse texto.
      var inner = el.querySelectorAll('*');
      var hasInner = false;
      for (var j = 0; j < inner.length; j++) {
        if (norm(inner[j].textContent).toLowerCase() === t) { hasInner = true; break; }
      }
      if (hasInner) continue;
      // Não seguir links que mudam de página (apenas âncoras/separadores).
      var link = el.closest('a[href]');
      if (link) {
        var href = link.getAttribute('href') || '';
        var sameDoc = href.charAt(0) === '#' || /^javascript:/i.test(href) ||
          (link.pathname === location.pathname && link.host === location.host);
        if (!sameDoc) continue;
      }
      try { el.click(); clicked = true; } catch (e) { /* ignorar */ }
    }
  }

  var items = [];
  var seen = {};
  var add = function (name, waiting, info) {
    name = norm(name).slice(0, 120) || 'Geral';
    var key = name + '|' + waiting;
    if (seen[key]) return;
    seen[key] = true;
    items.push({ name: name, waiting: waiting, info: norm(info).slice(0, 200) });
  };

  // 2) Tabelas com uma coluna "Em espera".
  var tables = document.querySelectorAll('table');
  for (var ti = 0; ti < tables.length; ti++) {
    var rows = Array.prototype.slice.call(tables[ti].rows);
    var hIdx = -1, col = -1;
    for (var r = 0; r < rows.length && hIdx < 0; r++) {
      var cells = Array.prototype.map.call(rows[r].cells, function (c) { return norm(c.textContent); });
      for (var c = 0; c < cells.length; c++) {
        if (isCountText(cells[c]) && firstInt(cells[c]) === null) { hIdx = r; col = c; break; }
      }
    }
    if (hIdx < 0) continue;
    var headers = Array.prototype.map.call(rows[hIdx].cells, function (c) { return norm(c.textContent); });
    rows.slice(hIdx + 1).forEach(function (row) {
      var cs = Array.prototype.map.call(row.cells, function (c) { return norm(c.textContent); });
      if (cs.length <= col) return;
      var n = firstInt(cs[col]);
      if (n === null) return;
      var info = cs.map(function (v, k) {
        if (k === col || k === 0 || !v) return null;
        return headers[k] ? headers[k] + ': ' + v : v;
      }).filter(Boolean).join(' · ');
      add(cs[0], n, info);
    });
  }

  var candidates = [];
  if (!items.length) {
    var els = document.body ? document.body.querySelectorAll('*') : [];
    for (var e = 0; e < els.length; e++) {
      if (SKIP_TAGS.test(els[e].tagName)) continue;
      if (isCountText(ownText(els[e]))) candidates.push(els[e]);
    }
  }

  // 3) Grelhas (linhas de <div>) com um cabeçalho "Em espera".
  var gridFrom = function (label) {
    var a = label;
    for (var k = 0; k < 4 && a && a.parentElement; k++, a = a.parentElement) {
      var row = a.parentElement;
      var container = row && row.parentElement;
      if (!container || row.children.length < 2) continue;
      var idx = Array.prototype.indexOf.call(row.children, a);
      var width = row.children.length;
      var peers = Array.prototype.filter.call(container.children, function (x) {
        return x !== row && x.children.length === width;
      });
      var got = [];
      peers.forEach(function (p) {
        var cs = Array.prototype.map.call(p.children, function (x) { return norm(x.textContent); });
        var n = firstInt(cs[idx]);
        if (n !== null) got.push({ cs: cs, n: n });
      });
      if (got.length && got.length * 2 >= peers.length) {
        var hs = Array.prototype.map.call(row.children, function (x) { return norm(x.textContent); });
        return got.map(function (g) {
          var nameIdx = -1;
          for (var q = 0; q < g.cs.length; q++) {
            if (q !== idx && g.cs[q] && firstInt(g.cs[q]) === null) { nameIdx = q; break; }
          }
          var info = g.cs.map(function (v, q) {
            if (q === idx || q === nameIdx || !v) return null;
            return hs[q] && hs[q] !== v ? hs[q] + ': ' + v : v;
          }).filter(Boolean).join(' · ');
          return { name: nameIdx >= 0 ? g.cs[nameIdx] : g.cs[0], waiting: g.n, info: info };
        });
      }
    }
    return null;
  };
  for (var g = 0; g < candidates.length; g++) {
    var lt = ownText(candidates[g]);
    if (/\d/.test(lt)) continue; // um cabeçalho de coluna não tem números
    var res = gridFrom(candidates[g]);
    if (res) res.forEach(function (x) { add(x.name, x.waiting, x.info); });
  }

  // 4) "Cartões" por serviço: "63 pessoas em espera", "Em espera: 63", "Em espera" + "63"...
  var countNear = function (ls, i) {
    var l = ls[i], m;
    m = /(\d{1,4})\s*(?:pessoas?|senhas?|utentes?|clientes?)?\s*(?:em\s+espera|a\s+aguardar|aguardam|à\s+(?:sua\s+)?frente|em\s+fila|na\s+fila)/i.exec(l);
    if (m) return parseInt(m[1], 10);
    m = /(?:espera|aguardar|aguardam|frente|fila)\s*[:\-–]?\s*(\d{1,4})(?![\d:h])/i.exec(l);
    if (m) return parseInt(m[1], 10);
    if (i + 1 < ls.length && toInt(ls[i + 1]) !== null) return toInt(ls[i + 1]);
    if (i > 0 && toInt(ls[i - 1]) !== null) return toInt(ls[i - 1]);
    if (i + 1 < ls.length) {
      m = /^(\d{1,4})\s*(?:pessoas?|senhas?|utentes?)\b/i.exec(ls[i + 1]);
      if (m) return parseInt(m[1], 10);
    }
    return null;
  };
  if (!items.length) {
    for (var ci = 0; ci < candidates.length; ci++) {
      var card = candidates[ci];
      for (var up = 0; up < 6; up++) {
        var p = card.parentElement;
        if (!p || p === document.body) break;
        var pt = norm(p.textContent);
        if (pt.length > 500 || (pt.match(COUNT_RE_G) || []).length > 1) break;
        card = p;
      }
      var ls = lines(card);
      var at = -1;
      for (var li = 0; li < ls.length; li++) { if (isCountText(ls[li])) { at = li; break; } }
      if (at < 0) continue;
      var n = countNear(ls, at);
      if (n === null) continue;
      var name = null;
      for (var ni = 0; ni < ls.length; ni++) {
        var s = ls[ni];
        if (ni !== at && !isCountText(s) && toInt(s) === null && s.length > 1 && !/^(senha|tempo|pessoas?)\b/i.test(s)) { name = s; break; }
      }
      var rest = ls.filter(function (s, k) {
        return k !== at && s !== name && toInt(s) !== n;
      }).join(' · ');
      add(name || 'Geral', n, rest);
    }
  }

  return { clicked: clicked, url: location.href, items: items };
})(__CLICK_TEXTS__);
