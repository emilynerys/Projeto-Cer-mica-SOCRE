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
