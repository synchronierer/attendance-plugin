(() => {
  const postJson = async (path, data = {}) => {
    const response = await fetch(path, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data)
    });

    const text = await response.text();

    if (!response.ok) {
      let message = text || `Serverfehler (${response.status})`;

      if (/<!doctype html|<html/i.test(message)) {
        message = `Serverfehler (${response.status})`;
      }

      throw new Error(message);
    }

    return text ? JSON.parse(text) : {};
  };

  const typeLabel = type =>
    type === "ARRIVAL" ? "Ankunft" : "Aufenthaltsort";

  const cell = value => {
    const td = document.createElement("td");
    td.textContent = value;
    return td;
  };

  function init() {
    const form = document.querySelector("#location-form");
    const body = document.querySelector("#locations");
    const status = document.querySelector("#status");
    const heading = document.querySelector("#location-form-heading");
    const resetButton = document.querySelector("#resetButton");
    const type = form?.elements?.type;
    const typeHelp = document.querySelector("#type-help");

    if (!form || !body || !status || !heading ||
        !resetButton || !type || !typeHelp) {
      throw new Error(
        "Attendance-Admin: erforderliche Seitenelemente fehlen"
      );
    }

    const updateTypeHelp = () => {
      typeHelp.textContent =
        type.value === "ARRIVAL"
          ? "Registriert die Ankunft in der Schule."
          : "Registriert den aktuellen Arbeits- oder Aufenthaltsort.";
    };

    const resetForm = () => {
      HTMLFormElement.prototype.reset.call(form);
      form.elements.id.value = "";
      form.elements.active.checked = true;
      heading.textContent = "Neuen Bereich anlegen";
      updateTypeHelp();
    };

    type.addEventListener("change", updateTypeHelp);

    async function refresh() {
      const data = await postJson("/attendance-admin-data");

      body.replaceChildren();

      for (const x of data.locations) {
        const row = document.createElement("tr");

        row.append(
          cell(x.name),
          cell(`${typeLabel(x.type)} · ${x.type}`),
          cell(x.code),
          cell(x.active ? "Aktiv" : "Inaktiv"),
          cell(x.displayOrder)
        );

        const qrCell = document.createElement("td");
        const qr = document.createElement("a");

        qr.href =
          `/attendance-display?location=${encodeURIComponent(x.code)}`;
        qr.target = "_blank";
        qr.rel = "noopener";
        qr.textContent = "QR anzeigen";
        qr.title = "Dynamischer QR-Code für diesen Bereich";

        const hint = document.createElement("small");
        hint.textContent = "Dynamischer QR-Code für diesen Bereich";

        qrCell.append(
          qr,
          document.createElement("br"),
          hint
        );

        row.append(qrCell);

        const actionCell = document.createElement("td");

        const edit = document.createElement("button");
        edit.type = "button";
        edit.textContent = "Bearbeiten";

        edit.onclick = () => {
          form.elements.id.value = x.id;
          form.elements.code.value = x.code;
          form.elements.name.value = x.name;
          form.elements.type.value = x.type;
          form.elements.displayOrder.value = x.displayOrder;
          form.elements.active.checked = x.active;
          heading.textContent = "Bereich bearbeiten";
          updateTypeHelp();
        };

        actionCell.append(edit);

        if (x.type !== "ARRIVAL") {
          const del = document.createElement("button");
          del.type = "button";
          del.textContent = "Löschen";
          del.style.marginLeft = ".5rem";

          del.onclick = async () => {
            const confirmed = window.confirm(
              `Bereich „${x.name}“ (${x.code}) wirklich löschen?`
            );

            if (!confirmed) return;

            try {
              await postJson("/attendance-location-delete", {
                id: x.id
              });

              status.textContent =
                `Bereich „${x.name}“ wurde gelöscht.`;

              if (Number(form.elements.id.value) === Number(x.id)) {
                resetForm();
              }

              await refresh();
            } catch (e) {
              status.textContent = e.message;
            }
          };

          actionCell.append(del);
        } else {
          const protectedHint = document.createElement("small");
          protectedHint.textContent = " · Systembereich";
          actionCell.append(protectedHint);
        }

        row.append(actionCell);
        body.append(row);
      }
    }

    form.onsubmit = async event => {
      event.preventDefault();

      const f = event.currentTarget;
      const fd = new FormData(f);
      const x = Object.fromEntries(fd);

      x.displayOrder = Number(x.displayOrder);
      x.active = f.elements.active.checked;

      if (x.id) {
        x.id = Number(x.id);
      } else {
        delete x.id;
      }

      try {
        await postJson("/attendance-location-save", x);

        status.textContent = "Bereich gespeichert.";

        resetForm();
        await refresh();
      } catch (e) {
        status.textContent = e.message;
      }
    };

    resetButton.onclick = resetForm;

    updateTypeHelp();

    refresh().catch(e => {
      status.textContent = e.message;
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener(
      "DOMContentLoaded",
      init,
      { once: true }
    );
  } else {
    init();
  }
})();
