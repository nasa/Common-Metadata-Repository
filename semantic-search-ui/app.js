const form = document.querySelector("#search-form");
const queryInput = document.querySelector("#query");
const searchButton = document.querySelector("#search-button");
const resultsSection = document.querySelector("#results-section");
const results = document.querySelector("#results");
const emptyState = document.querySelector("#empty-state");
const toast = document.querySelector("#toast");
const filters = document.querySelector("#filters");
const filterToggle = document.querySelector("#filter-toggle");

const el = (tag, className, text) => {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (text !== undefined) node.textContent = text;
  return node;
};

function showError(message) {
  toast.textContent = message;
  toast.hidden = false;
  window.clearTimeout(showError.timer);
  showError.timer = window.setTimeout(() => { toast.hidden = true; }, 6500);
}

function evidenceNode(evidence) {
  const box = el("div", "evidence");
  const header = el("div", "evidence-header");
  header.append(el("span", "", evidence.source), el("span", "", evidence.type.replaceAll("_", " ")));
  if (evidence.variable_name) header.append(el("span", "", `· ${evidence.variable_name}`));
  const snippet = el("div");
  // Elasticsearch highlighting only adds em tags; render all other content as text.
  const pieces = String(evidence.snippet || "").split(/(<\/?em>)/i);
  let highlighted = false;
  for (const piece of pieces) {
    if (piece.toLowerCase() === "<em>") { highlighted = true; continue; }
    if (piece.toLowerCase() === "</em>") { highlighted = false; continue; }
    const part = highlighted ? el("em", "", piece) : document.createTextNode(piece);
    snippet.append(part);
  }
  box.append(header, snippet);
  return box;
}

function render(data) {
  results.replaceChildren();
  document.querySelector("#results-title").textContent = `“${data.query}”`;
  document.querySelector("#results-meta").textContent = `${data.returned} shown · ${data.candidate_collections} candidates · ${data.took_ms} ms`;
  for (const item of data.entries) {
    const card = el("article", "result-card");
    card.style.animationDelay = `${Math.min(item.rank * 35, 300)}ms`;
    const rank = el("div", "rank", String(item.rank).padStart(2, "0"));
    const body = el("div");
    const top = el("div", "result-top");
    const titleGroup = el("div");
    titleGroup.append(el("h3", "", item.title), el("div", "concept-id", `${item.short_name} · ${item.concept_id}`));
    top.append(titleGroup, el("div", "score", `RRF ${Number(item.score).toFixed(4)}`));
    body.append(top, ...item.evidence.map(evidenceNode));
    card.append(rank, body);
    results.append(card);
  }
  if (!data.entries.length) results.append(el("p", "empty-state", "No matching collections. Try broader language or remove a filter."));
  emptyState.hidden = true;
  resultsSection.hidden = false;
  resultsSection.scrollIntoView({ behavior: "smooth", block: "start" });
}

function searchParams() {
  const params = new URLSearchParams({
    q: queryInput.value.trim(),
    mode: new FormData(form).get("mode"),
    page_size: document.querySelector("#page-size").value,
  });
  const start = document.querySelector("#start-date").value;
  const end = document.querySelector("#end-date").value;
  const bbox = document.querySelector("#bounding-box").value.trim();
  if (start || end) {
    if (!start || !end) throw new Error("Choose both a start and end date.");
    params.set("temporal", `${start}T00:00:00Z,${end}T23:59:59Z`);
  }
  if (bbox) params.set("bounding_box", bbox);
  return params;
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!queryInput.value.trim()) return queryInput.focus();
  searchButton.disabled = true;
  searchButton.querySelector("span").textContent = "Searching";
  toast.hidden = true;
  try {
    const response = await fetch(`/api/semantic-collections?${searchParams()}`);
    const data = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(data.detail || data.errors?.[0] || `Search failed (${response.status})`);
    render(data);
  } catch (error) {
    showError(error.message || "Search could not be completed.");
  } finally {
    searchButton.disabled = false;
    searchButton.querySelector("span").textContent = "Search";
  }
});

queryInput.addEventListener("input", () => {
  queryInput.style.height = "auto";
  queryInput.style.height = `${queryInput.scrollHeight}px`;
});
queryInput.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && !event.shiftKey) { event.preventDefault(); form.requestSubmit(); }
});
filterToggle.addEventListener("click", () => {
  filters.hidden = !filters.hidden;
  filterToggle.setAttribute("aria-expanded", String(!filters.hidden));
});
filters.addEventListener("input", () => {
  const count = ["start-date", "end-date", "bounding-box"].filter(id => document.querySelector(`#${id}`).value).length;
  document.querySelector("#filter-count").textContent = count ? String(count) : "";
});
document.querySelectorAll("#suggestions button").forEach(button => button.addEventListener("click", () => {
  queryInput.value = button.textContent;
  form.requestSubmit();
}));

async function checkService() {
  const dot = document.querySelector("#status-dot");
  const text = document.querySelector("#status-text");
  try {
    const [health, version] = await Promise.all([fetch("/api/health"), fetch("/api/version")]);
    if (!health.ok) throw new Error();
    const details = version.ok ? await version.json() : null;
    dot.className = "status-dot online";
    text.textContent = "Prototype online";
    if (details?.version) document.querySelector("#version-text").textContent = `Service v${details.version} · schema ${details.schema_version}`;
  } catch (_) {
    dot.className = "status-dot offline";
    text.textContent = "Prototype unavailable";
  }
}
checkService();
