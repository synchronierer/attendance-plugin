const post = async (path, data = {}) => {
  const response = await fetch(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(data) });
  if (!response.ok) throw new Error(await response.text());
  return response.json();
};
const form = document.querySelector("#location-form"), body = document.querySelector("#locations"), status = document.querySelector("#status");
const typeLabel = type => type === "ARRIVAL" ? "Ankunft" : "Aufenthaltsort";
const cell = value => { const td = document.createElement("td"); td.textContent = value; return td; };
async function refresh() {
  const data = await post("/attendance-admin-data");
  body.replaceChildren();
  for (const x of data.locations) {
    const row = document.createElement("tr");
    row.append(cell(x.name), cell(`${typeLabel(x.type)} · ${x.type}`), cell(x.code), cell(x.active ? "Aktiv" : "Inaktiv"), cell(x.displayOrder));
    const qrCell = document.createElement("td"), qr = document.createElement("a");
    qr.href = `/attendance-display?location=${encodeURIComponent(x.code)}`; qr.target = "_blank"; qr.rel = "noopener"; qr.textContent = "QR anzeigen"; qr.title = "Dynamischer QR-Code für diesen Bereich";
    qrCell.append(qr); const hint = document.createElement("small"); hint.textContent = "Dynamischer QR-Code für diesen Bereich"; qrCell.append(document.createElement("br"), hint); row.append(qrCell);
    const editCell = document.createElement("td"), edit = document.createElement("button");
    edit.type = "button"; edit.textContent = "Bearbeiten"; edit.onclick = () => { form.elements.id.value = x.id; form.elements.code.value = x.code; form.elements.name.value = x.name; form.elements.type.value = x.type; form.elements.displayOrder.value = x.displayOrder; form.elements.active.checked = x.active; document.querySelector("#location-form-heading").textContent = "Bereich bearbeiten"; };
    editCell.append(edit); row.append(editCell); body.append(row);
  }
}
form.onsubmit = async event => { event.preventDefault(); const f = event.currentTarget, fd = new FormData(f), x = Object.fromEntries(fd); x.displayOrder = Number(x.displayOrder); x.active = f.elements.active.checked; if (x.id) x.id = Number(x.id); else delete x.id; try { await post("/attendance-location-save", x); status.textContent = "Bereich gespeichert."; f.reset(); f.elements.id.value = ""; f.elements.active.checked = true; document.querySelector("#location-form-heading").textContent = "Neuen Bereich anlegen"; await refresh(); } catch (e) { status.textContent = e.message; } };
document.querySelector("#reset").onclick = () => { form.reset(); form.elements.id.value = ""; form.elements.active.checked = true; document.querySelector("#location-form-heading").textContent = "Neuen Bereich anlegen"; };
refresh().catch(e => { status.textContent = e.message; });
