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

// Desenforna: shows 1ª + 2ª + Cru of each format.
document.querySelectorAll("[data-desenforna-item]").forEach(block => {
  const parts = [...block.querySelectorAll("[data-soma]")], out = block.querySelector("[data-soma-total]");
  const number = new Intl.NumberFormat("pt-BR");
  const update = () => { out.textContent = number.format(parts.reduce((sum, input) => sum + (Number(input.value) || 0), 0)); };
  parts.forEach(input => input.addEventListener("input", update)); update();
});

// Batch validation: "mark all" toggles the pending records of the page.
const markAll = document.getElementById("marcar-todos");
if (markAll) markAll.addEventListener("change", () => document.querySelectorAll("[data-lote]").forEach(box => { box.checked = markAll.checked; }));

document.querySelectorAll("[data-auto-submit]").forEach(select => select.addEventListener("change", () => select.form.submit()));
