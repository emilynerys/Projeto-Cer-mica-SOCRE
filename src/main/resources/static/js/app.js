"use strict";

document.querySelectorAll("[data-toggle-password]").forEach(button => {
  button.addEventListener("click", () => {
    const input = document.getElementById(button.getAttribute("aria-controls"));
    const visible = input.type === "password";
    input.type = visible ? "text" : "password";
    button.textContent = visible ? "Ocultar" : "Mostrar";
    button.setAttribute("aria-pressed", String(visible));
  });
});

document.querySelectorAll("[data-logout]").forEach(form => form.addEventListener("submit", () => {
  try {
    Object.keys(sessionStorage).filter(key => key.startsWith("socre.maromba.")).forEach(key => sessionStorage.removeItem(key));
  } catch { /* Storage can be disabled on shared browsers. */ }
}));

const form = document.getElementById("production-form");
if (form) {
  const quantities = [...form.querySelectorAll("[data-formato]")];
  const fields = ["data", "chaveEnvio", "versao", ...quantities.map(input => input.name)];
  const draftKey = form.dataset.draftKey;
  const error = document.getElementById("form-error");
  const fill = document.getElementById("fill-section");
  const review = document.getElementById("review-section");
  const save = document.getElementById("save-button");
  const field = name => form.elements.namedItem(name);
  const message = text => { error.textContent = text; error.hidden = false; error.scrollIntoView({block:"center"}); };
  const persist = () => {
    try { sessionStorage.setItem(draftKey, JSON.stringify(Object.fromEntries(fields.map(name => [name, field(name).value])))); }
    catch { /* A storage failure must not prevent a server save. */ }
  };
  try {
    const draft = JSON.parse(sessionStorage.getItem(draftKey));
    if (draft && fields.every(name => typeof draft[name] === "string")) {
      // Keep the draft's version: a changed server record must produce a conflict, never be overwritten.
      fields.forEach(name => { field(name).value = draft[name]; });
      document.getElementById("draft-notice").hidden = false;
    }
  } catch { /* Ignore an unavailable or malformed draft. */ }
  form.addEventListener("input", persist);
  document.getElementById("discard-draft").addEventListener("click", () => {
    try { sessionStorage.removeItem(draftKey); } catch { /* Optional storage. */ }
    window.location.reload();
  });
  const showReview = () => {
    if (!form.reportValidity()) return;
    if (quantities.reduce((total, input) => total + Number(input.value), 0) <= 0) {
      message("Informe a produção de pelo menos um formato. Um dia sem lançamento não é considerado produção zero.");
      return;
    }
    error.hidden = true;
    document.getElementById("review-date").textContent = field("data").value.split("-").reverse().join("/");
    const number = new Intl.NumberFormat("pt-BR");
    quantities.forEach(input => {
      const value = Number(input.value);
      document.getElementById("review-" + input.dataset.formato).textContent =
        value === 0 ? "sem produção (não gera registro)" : number.format(value) + " tijolos";
    });
    fill.hidden = true; review.hidden = false;
    document.getElementById("step-fill").classList.remove("active");
    document.getElementById("step-review").classList.add("active");
    persist(); save.focus();
  };
  document.getElementById("review-button").addEventListener("click", showReview);
  document.getElementById("edit-button").addEventListener("click", () => {
    fill.hidden = false; review.hidden = true;
    document.getElementById("step-fill").classList.add("active");
    document.getElementById("step-review").classList.remove("active");
    field("data").focus();
  });
  form.addEventListener("submit", async event => {
    event.preventDefault();
    if (review.hidden) { showReview(); return; }
    if (save.disabled) return;
    persist(); error.hidden = true; save.disabled = true; save.textContent = "Salvando…";
    document.getElementById("edit-button").disabled = true;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 20000);
    try {
      const response = await fetch(form.action, {method:"POST", body:new FormData(form), headers:{Accept:"application/json"}, signal:controller.signal, credentials:"same-origin"});
      if (response.redirected || response.status === 401 || response.status === 403) {
        message("Sua sessão precisa ser renovada. Entre novamente e reabra este formulário na mesma aba para recuperar o rascunho.");
        return;
      }
      const result = response.headers.get("content-type")?.includes("application/json") ? await response.json() : null;
      if (response.ok && result?.redirect) {
        try { sessionStorage.removeItem(draftKey); } catch { /* Optional storage. */ }
        window.location.assign(result.redirect);
      } else {
        message(result?.message || "Não foi possível salvar. Seus dados continuam preenchidos; confira os valores e tente novamente.");
      }
    } catch {
      message("Não foi possível confirmar o envio. Seus dados foram mantidos nesta aba. Quando a conexão voltar, tente salvar novamente: o mesmo envio não será duplicado.");
    } finally {
      clearTimeout(timer); save.disabled = false; save.textContent = "Confirmar e salvar";
      document.getElementById("edit-button").disabled = false;
    }
  });
}

// Enforna: the total follows lines × bricks per line until the user types a different total (RN12).
document.querySelectorAll("[data-enforna-item]").forEach(block => {
  // The bricks per line come from an input (Enforna) or from the format itself (initial inventory).
  const lines = block.querySelector("[data-linhas]"), perLine = block.querySelector("[data-por-linha]"), total = block.querySelector("[data-total]");
  const product = () => (Number(lines.value) || 0) * (Number(perLine ? perLine.value : block.dataset.tijolosPorLinha) || 0);
  let manual = total.value !== "" && Number(total.value) !== product();
  const update = () => { if (!manual) total.value = lines.value === "" ? "" : product(); };
  lines.addEventListener("input", update); if (perLine) perLine.addEventListener("input", update);
  total.addEventListener("input", () => { manual = total.value !== "" && Number(total.value) !== product(); });
  update();
});

// Enforna: lines in the kiln with this entry, adding every format, against the capacity.
const gauge = document.querySelector("[data-capacidade]");
if (gauge) {
  const capacity = Number(gauge.dataset.capacidade), taken = Number(gauge.dataset.ocupadas);
  const lineInputs = [...document.querySelectorAll("[data-enforna-item] [data-linhas]")];
  const update = () => {
    const total = taken + lineInputs.reduce((sum, input) => sum + (Number(input.value) || 0), 0);
    gauge.querySelector("[data-ocupacao]").textContent = total + " de " + capacity + " linhas ocupadas";
    gauge.querySelector("[data-restantes]").textContent = total > capacity ? "passou " + (total - capacity) + " do limite" : "cabem mais " + (capacity - total);
    gauge.classList.toggle("over", total > capacity);
  };
  lineInputs.forEach(input => input.addEventListener("input", update)); update();
}

// A zero already in a number field is selected on focus, so typing replaces it.
document.querySelectorAll("input[type=number]").forEach(input => {
  input.addEventListener("focus", () => { if (input.value === "0") input.select(); });
  input.addEventListener("mouseup", event => { if (input.value === "0") event.preventDefault(); });
});

// Desenforna: shows 1ª + 2ª + Cru of each format.
document.querySelectorAll("[data-desenforna-item]").forEach(block => {
  const parts = [...block.querySelectorAll("[data-soma]")], out = block.querySelector("[data-soma-total]");
  const number = new Intl.NumberFormat("pt-BR");
  const update = () => { out.textContent = number.format(parts.reduce((sum, input) => sum + (Number(input.value) || 0), 0)); };
  parts.forEach(input => input.addEventListener("input", update)); update();
});

// Review by day: each day's records open in a window on the same page.
document.querySelectorAll("[data-open-dialog]").forEach(button => button.addEventListener("click", () => {
  document.getElementById(button.dataset.openDialog).showModal();
}));
document.querySelectorAll("dialog").forEach(dialog => {
  dialog.querySelectorAll("[data-close-dialog]").forEach(button => button.addEventListener("click", () => dialog.close()));
  // A click on the dimmed area outside the window closes it.
  dialog.addEventListener("click", event => { if (event.target === dialog) dialog.close(); });
});

// Batch validation: "mark all" toggles the pending records of its own form.
document.querySelectorAll("[data-marcar-todos]").forEach(markAll => markAll.addEventListener("change", () =>
  markAll.form.querySelectorAll("[data-lote]").forEach(box => { box.checked = markAll.checked; })));

document.querySelectorAll("[data-auto-submit]").forEach(select => select.addEventListener("change", () => select.form.submit()));

// Importação: a chosen file only fills the text box, which is what gets checked and sent.
const importForm = document.querySelector("[data-importacao]");
if (importForm) {
  const field = importForm.querySelector("[data-arquivo-campo]");
  field.hidden = false;
  field.querySelector("[data-arquivo]").addEventListener("change", event => {
    const file = event.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.addEventListener("load", () => { importForm.querySelector("[data-arquivo-texto]").value = reader.result; });
    reader.readAsText(file, "UTF-8");
  });
}

// Status do Forno: each state cell takes the state's colour, and a range of lines can be filled at once.
const kilnForm = document.querySelector("[data-status-forno]");
if (kilnForm) {
  const paint = select => { select.closest("td").dataset.estado = select.value; };
  const line = (n, part) => kilnForm.elements.namedItem("linhas[" + n + "]." + part);
  kilnForm.querySelectorAll("[data-linha-estado]").forEach(select => select.addEventListener("change", () => paint(select)));
  // An empty line ("V") has no state.
  kilnForm.querySelectorAll("[data-linha-tijolo]").forEach(select => select.addEventListener("change", () => {
    if (select.value !== "") return;
    const state = line(select.name.match(/\d+/)[0], "estado");
    state.value = ""; paint(state);
  }));
  const fill = kilnForm.querySelector("[data-preencher]");
  const note = fill.querySelector("[data-preencher-msg]");
  fill.hidden = false;
  fill.querySelector("[data-aplicar]").addEventListener("click", () => {
    const from = Number(fill.querySelector("[data-de]").value), to = Number(fill.querySelector("[data-ate]").value) || from;
    const brick = fill.querySelector("[data-tijolo]").value, state = brick === "" ? "" : fill.querySelector("[data-estado-lote]").value;
    if (!from) { note.textContent = "Informe a primeira linha."; return; }
    if (brick !== "" && state === "") { note.textContent = "Escolha o status do tijolo."; return; }
    const low = Math.min(from, to), high = Math.max(from, to);
    let count = 0;
    for (let n = low; n <= high; n++) {
      if (!line(n, "idFormato")) continue;
      line(n, "idFormato").value = brick; line(n, "estado").value = state; paint(line(n, "estado")); count++;
    }
    note.textContent = count === 0 ? "Nenhuma linha nesse intervalo." : "Linhas " + low + " a " + high + " preenchidas. Confira no mapa e salve.";
  });

  // Fire: tapping a line number puts the fire there. Heating or burning lines before it are burnt; raw bricks put behind the fire stay raw.
  const fire = kilnForm.querySelector("[data-fogo]");
  const fireNote = fire.querySelector("[data-fogo-msg]"), undoFire = fire.querySelector("[data-fogo-desfazer]");
  const total = kilnForm.querySelectorAll("[data-linha-estado]").length;
  const undoSteps = [];
  const clearChanged = () => kilnForm.querySelectorAll(".kiln-changed").forEach(cell => cell.classList.remove("kiln-changed"));
  const setState = (n, value) => { const state = line(n, "estado"); if (state.value === value) return; state.value = value; paint(state); state.closest("td").classList.add("kiln-changed"); };
  fire.hidden = false;
  kilnForm.querySelectorAll("[data-fogo-aqui]").forEach(button => button.addEventListener("click", () => {
    const target = Number(button.dataset.fogoAqui);
    if (line(target, "idFormato").value === "") { fireNote.textContent = "A linha " + target + " está vazia. Toque em uma linha com tijolo."; return; }
    const before = Array.from({ length: total }, (_, i) => line(i + 1, "estado").value);
    clearChanged();
    for (let n = 1; n < target; n++) if (["ESQUENTE", "QUEIMA"].includes(line(n, "estado").value)) setState(n, "ASSADO");
    setState(target, "QUEIMA");
    for (let n = target + 1; n <= total; n++) if (line(n, "estado").value === "QUEIMA") setState(n, "ESQUENTE");
    undoSteps.push(before); undoFire.hidden = false;
    fireNote.textContent = "Fogo na linha " + target + ". As linhas em esquente antes dela ficaram Assado. Confira no mapa e salve.";
  }));
  undoFire.addEventListener("click", () => {
    const before = undoSteps.pop();
    if (!before) return;
    before.forEach((value, i) => { const state = line(i + 1, "estado"); state.value = value; paint(state); });
    clearChanged();
    undoFire.hidden = undoSteps.length === 0;
    fireNote.textContent = "Voltou como estava antes desse toque.";
  });
  // The quantity follows the brick's reference when the brick changes; an empty line has none.
  const reference = select => (select.selectedOptions[0] && select.selectedOptions[0].dataset.referencia) || "";
  kilnForm.querySelectorAll("[data-linha-tijolo]").forEach(select => select.addEventListener("change", () => {
    const quantity = line(select.name.match(/\d+/)[0], "quantidade");
    if (quantity) quantity.value = select.value === "" ? "" : reference(select);
  }));
  fill.querySelector("[data-aplicar]").addEventListener("click", () => {
    const brick = fill.querySelector("[data-tijolo]");
    const from = Number(fill.querySelector("[data-de]").value), to = Number(fill.querySelector("[data-ate]").value) || from;
    for (let n = Math.min(from, to); n <= Math.max(from, to); n++) {
      const quantity = line(n, "quantidade");
      if (quantity) quantity.value = brick.value === "" ? "" : reference(brick);
    }
  });
}

// Indicadores: charts drawn from the JSON block of the page; the chart library is loaded only there.
window.addEventListener("load", () => {
  const fonte = document.getElementById("dados-painel");
  if (!fonte || !window.Chart) return;
  const d = JSON.parse(fonte.textContent);
  const cor = { barro: "#a74123", verde: "#27604b", ambar: "#c98a1b", cinza: "#b9aea3", tinta: "#302b27", vazio: "#efe9e2" };
  Chart.defaults.font.family = '"Segoe UI", Arial, sans-serif';
  Chart.defaults.color = "#726a63";
  const vazio = valores => !valores || valores.every(v => !v);
  const semDados = (id, texto) => { const c = document.getElementById(id); if (c) c.closest("div").innerHTML = '<p class="dash-empty">' + texto + "</p>"; };
  const numero = v => v.toLocaleString("pt-BR", { maximumFractionDigits: 1 });

  // Gauges: half doughnut; green when at or above the factory's own average, amber a little below, red well below.
  const medidor = (id, valor, maximo, media, sufixo, neutro) => {
    const alvo = document.querySelector('[data-gauge="' + id + '"]');
    if (valor === null || valor === undefined) { alvo.textContent = "—"; semDados("g-" + id, "Sem dados ainda"); return; }
    let preenchimento = cor.barro;
    if (!neutro && media) preenchimento = valor >= media - 1 ? cor.verde : valor >= media - 5 ? cor.ambar : cor.barro;
    alvo.textContent = numero(valor) + sufixo;
    if (!neutro && media) document.querySelector('[data-gauge-nota="' + id + '"]').textContent += " · média das fornadas: " + numero(media) + sufixo;
    new Chart(document.getElementById("g-" + id), {
      type: "doughnut",
      data: { datasets: [{ data: [Math.min(valor, maximo), Math.max(maximo - valor, 0)], backgroundColor: [preenchimento, cor.vazio], borderWidth: 0 }] },
      options: { rotation: -90, circumference: 180, cutout: "72%", responsive: true, maintainAspectRatio: false, plugins: { legend: { display: false }, tooltip: { enabled: false } } }
    });
  };
  medidor("qualidade", d.qualidade, 100, d.qualidadeMedia, "%", false);
  medidor("ocupacao", d.ocupacao, Math.max(100, d.ocupacao || 0), null, "%", true);
  medidor("velocidade", d.velocidade, Math.max(15, (d.velocidade || 0) * 1.3), d.velocidadeMedia, "", false);

  const eixo = { grid: { color: "#f1ece6" }, ticks: { precision: 0 } };
  const base = { responsive: true, maintainAspectRatio: false, interaction: { mode: "index", intersect: false }, plugins: { legend: { position: "bottom", labels: { boxWidth: 12 } } } };
  if (vazio(d.maromba) && vazio(d.enforna) && vazio(d.desenforna)) semDados("c-producao", "Nenhum lançamento validado no período.");
  else new Chart(document.getElementById("c-producao"), { type: "line", data: { labels: d.dias, datasets: [
      { label: "Maromba", data: d.maromba, borderColor: cor.cinza, backgroundColor: cor.cinza, cubicInterpolationMode: "monotone", pointRadius: 2 },
      { label: "Enforna", data: d.enforna, borderColor: cor.barro, backgroundColor: cor.barro, cubicInterpolationMode: "monotone", pointRadius: 2 },
      { label: "Desenforna (1ª + 2ª)", data: d.desenforna, borderColor: cor.verde, backgroundColor: cor.verde, cubicInterpolationMode: "monotone", pointRadius: 2 }] },
    options: { ...base, scales: { x: { grid: { display: false } }, y: { ...eixo, beginAtZero: true } } } });
  if (!d.fornadas.length) semDados("c-qualidade", "Nenhuma fornada com Desenforna ainda.");
  else new Chart(document.getElementById("c-qualidade"), { type: "bar", data: { labels: d.fornadas, datasets: [
      { label: "1ª", data: d.primeira, backgroundColor: cor.verde }, { label: "2ª", data: d.segunda, backgroundColor: cor.ambar }, { label: "Cru", data: d.cru, backgroundColor: cor.barro }] },
    options: { ...base, scales: { x: { stacked: true, grid: { display: false } }, y: { stacked: true, max: 100, beginAtZero: true, ticks: { callback: v => v + "%" }, grid: eixo.grid } } } });
  if (vazio(d.patioMaromba) && vazio(d.patioEnforna)) semDados("c-patio", "Nenhum lançamento validado no período.");
  else new Chart(document.getElementById("c-patio"), { type: "bar", data: { labels: d.semanas, datasets: [
      { label: "Maromba", data: d.patioMaromba, backgroundColor: cor.cinza }, { label: "Enforna", data: d.patioEnforna, backgroundColor: cor.barro }] },
    options: { ...base, scales: { x: { grid: { display: false } }, y: { ...eixo, beginAtZero: true } } } });
  if (vazio(d.estoquePatio) && vazio(d.estoqueForno)) semDados("c-estoque", "Nenhum estoque contado ainda.");
  else new Chart(document.getElementById("c-estoque"), { type: "bar", data: { labels: d.estoqueRotulos, datasets: [
      { label: "No pátio", data: d.estoquePatio, backgroundColor: cor.verde }, { label: "No forno", data: d.estoqueForno, backgroundColor: cor.barro }] },
    options: { ...base, indexAxis: "y", scales: { x: { stacked: true, ...eixo, beginAtZero: true }, y: { stacked: true, grid: { display: false } } } } });

  // TV mode: refreshes itself every 5 minutes.
  if (document.body.dataset.tv) setTimeout(() => location.reload(), 5 * 60 * 1000);
});

