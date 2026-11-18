(function () {
  const P = window.ManisrejoPortal;
  const SESSION_KEY = "manisrejo_editor_unlocked";
  let store = P.loadStore();

  const pinGateEl = document.getElementById("pinGateSection");
  const workspaceEl = document.getElementById("editorWorkspace");
  const lockBtnEl = document.getElementById("lockEditorBtn");

  const todayNavEl = document.getElementById("editorTodayNav");
  if (todayNavEl) {
    todayNavEl.textContent = `Panel Editor • ${P.formatTodayFull()}`;
  }

  function checkAuth() {
    const unlocked = sessionStorage.getItem(SESSION_KEY) === "1";
    pinGateEl.classList.toggle("hidden", unlocked);
    workspaceEl.classList.toggle("hidden", !unlocked);
    lockBtnEl.classList.toggle("hidden", !unlocked);
    if (unlocked) renderAll();
  }

  document.getElementById("pinLoginForm").addEventListener("submit", (e) => {
    e.preventDefault();
    store = P.loadStore();
    const entered = document.getElementById("pinInput").value.trim();
    if (entered === String(store.editorPin || P.DEFAULT_EDITOR_PIN || "00000")) {
      sessionStorage.setItem(SESSION_KEY, "1");
      document.getElementById("pinInput").value = "";
      checkAuth();
      P.showToast("Mode Editor Website aktif.");
    } else {
      P.showToast("PIN Editor salah. Default: 00000.");
    }
  });

  lockBtnEl.onclick = () => {
    sessionStorage.removeItem(SESSION_KEY);
    checkAuth();
    P.showToast("Editor dikunci.");
  };

  function renderSummary() {
    const paid = store.bookings.reduce((s, b) => s + Number(b.paidAmount || 0), 0);
    const pending = store.bookings
      .filter((b) => b.paymentStatus !== "PAID")
      .reduce((s, b) => s + Math.max(b.totalPrice - b.paidAmount, 0), 0);
    document.getElementById("edPaidTotal").textContent = P.formatRupiah(paid);
    document.getElementById("edPendingTotal").textContent = P.formatRupiah(pending);
    document.getElementById("edBookingCount").textContent = `${store.bookings.length} jadwal`;
  }

  function syncEditorDateBadge(dateStr) {
    const badgeEl = document.getElementById("edBookDayBadge");
    if (badgeEl && dateStr) {
      badgeEl.textContent = P.formatDayAndDate(P.isoDateToDayOfWeek(dateStr), dateStr);
    }
  }

  function renderAdminFormSelects() {
    const courtSel = document.getElementById("edBookCourt");
    const dateInput = document.getElementById("edBookDate");
    const statusSel = document.getElementById("edBookStatus");
    const dpField = document.getElementById("edBookDpField");

    courtSel.innerHTML = "";
    store.courts.filter((c) => c.active).forEach((c) => {
      const opt = document.createElement("option");
      opt.value = c.id;
      opt.textContent = `${c.name} (${P.formatRupiah(c.rate)}/jam)`;
      courtSel.appendChild(opt);
    });
    if (store.courts.filter((c) => c.active).length >= 2) {
      const bothOpt = document.createElement("option");
      bothOpt.value = "BOTH";
      bothOpt.textContent = "A & B (2 Lapangan)";
      courtSel.appendChild(bothOpt);
    }
    if (dateInput && !dateInput.dataset.bound) {
      dateInput.dataset.bound = "1";
      const todayDay = P.todayIsoDay();
      const todayDate = P.getDateForDayOfWeek(todayDay);
      if (!dateInput.value) dateInput.value = todayDate;
      syncEditorDateBadge(dateInput.value);
      dateInput.addEventListener("change", () => syncEditorDateBadge(dateInput.value));
      courtSel.addEventListener("change", renderEditorPerCourtHours);
      if (statusSel && dpField) {
        statusSel.addEventListener("change", () => {
          dpField.classList.toggle("hidden", statusSel.value !== "DP");
        });
      }
    }
    renderEditorPerCourtHours();
  }

  function renderEditorPerCourtHours() {
    const container = document.getElementById("edPerCourtHours");
    const courtSel = document.getElementById("edBookCourt");
    if (!container || !courtSel) return;
    const activeCourts = store.courts.filter((c) => c.active);
    const val = courtSel.value;
    const targetCourts = val === "BOTH"
      ? activeCourts.slice(0, 2)
      : activeCourts.filter((c) => String(c.id) === String(val));

    container.innerHTML = "";
    targetCourts.forEach((c) => {
      const box = document.createElement("div");
      box.className = "stat-box";
      box.style.background = "var(--surface-soft)";
      box.innerHTML = `
        <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:8px">
          <strong style="font-size:14px;color:var(--emerald-dark)">Kolom ${c.name}</strong>
          <span class="status-pill status-paid">${P.formatRupiah(c.rate)}/jam</span>
        </div>
        <div class="grid-2">
          <div class="field">
            <label for="edStart_${c.id}">Jam Mulai (08:00–23:00)</label>
            <input id="edStart_${c.id}" type="text" value="18:00" placeholder="08:00" required />
          </div>
          <div class="field">
            <label for="edEnd_${c.id}">Jam Selesai (maks 00:00)</label>
            <input id="edEnd_${c.id}" type="text" value="20:00" placeholder="00:00" required />
          </div>
        </div>
      `;
      container.appendChild(box);
    });
  }

  function renderBookings() {
    const listEl = document.getElementById("editorBookingsList");
    listEl.innerHTML = "";
    if (store.bookings.length === 0) {
      listEl.innerHTML = `<p style="color:var(--muted)">Belum ada jadwal pemesanan aktif.</p>`;
      return;
    }
    const sorted = [...store.bookings].sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startHour - b.startHour);
    sorted.forEach((b) => {
      const remaining = Math.max(b.totalPrice - b.paidAmount, 0);
      const statusClass = b.paymentStatus === "PAID" ? "status-paid" : "status-pending";
      const statusLabel = b.paymentStatus === "PAID" ? "Lunas" : b.paymentStatus === "DP" ? "DP Sebagian" : "Belum Bayar";
      const dayDateText = P.formatDayAndDate(b.dayOfWeek, b.bookingDate);

      const card = document.createElement("div");
      card.className = "slot-item";
      card.innerHTML = `
        <div style="flex:1 1 260px">
          <div style="display:flex;align-items:center;gap:8px;flex-wrap:wrap">
            <span class="time-badge mono">${dayDateText} • ${P.formatRange(b.startHour, b.durationHours)}</span>
            <span class="status-pill ${statusClass}">${statusLabel}</span>
          </div>
          <div style="font-weight:800;margin-top:6px">${b.customerName} — ${b.courtName}</div>
          <div style="font-size:13px;color:var(--muted)">
            HP: ${b.customerPhone || "-"} • Total: <strong class="mono">${P.formatRupiah(b.totalPrice)}</strong>
            ${remaining > 0 ? ` • Sisa: <strong class="mono">${P.formatRupiah(remaining)}</strong>` : ""}
          </div>
          ${b.notes ? `<div style="font-size:12px;color:var(--emerald-dark);font-weight:600;margin-top:3px">${b.notes}</div>` : ""}
        </div>
        <div class="chip-row">
          ${b.paymentStatus !== "PAID" ? `<button type="button" class="btn btn-primary" data-act="paid">Tandai Lunas</button>` : ""}
          ${b.paymentStatus !== "PAID" ? `<button type="button" class="btn btn-warning" data-act="remind">Pengingat WA (${b.reminderSentCount || 0}x)</button>` : ""}
          <button type="button" class="btn btn-danger" data-act="cancel">Batalkan</button>
        </div>
      `;

      const paidBtn = card.querySelector('[data-act="paid"]');
      if (paidBtn) {
        paidBtn.onclick = () => {
          b.paymentStatus = "PAID";
          b.paidAmount = b.totalPrice;
          P.saveStore(store, { source: "editor_paid" });
          if (typeof P.updateCloudSlot === "function") {
            P.updateCloudSlot(b, { paymentStatus: "PAID", paidAmount: b.totalPrice, reminderEnabled: false });
          }
          P.showToast(`${b.customerName} ditandai Lunas.`);
          renderAll();
        };
      }

      const remindBtn = card.querySelector('[data-act="remind"]');
      if (remindBtn) {
        remindBtn.onclick = () => {
          b.reminderSentCount = (b.reminderSentCount || 0) + 1;
          P.saveStore(store, { source: "editor_remind" });
          if (typeof P.updateCloudSlot === "function") {
            P.updateCloudSlot(b, { reminderSentCount: b.reminderSentCount });
          }
          const msg = `Halo ${b.customerName}, pengingat pembayaran sewa ${b.courtName} (${dayDateText}, ${P.formatRange(b.startHour, b.durationHours)}) sebesar ${P.formatRupiah(remaining)}. Terima kasih — ${store.venue.name}.`;
          P.showToast(`Pengingat ke-${b.reminderSentCount} dicatat!`);
          if (b.customerPhone) window.open(P.buildWhatsAppUrl(b.customerPhone, msg), "_blank", "noopener");
          renderAll();
        };
      }

      card.querySelector('[data-act="cancel"]').onclick = () => {
        store.bookings = store.bookings.filter((item) => item.id !== b.id);
        store.history.unshift({
          id: Date.now(),
          courtId: b.courtId,
          courtName: b.courtName,
          dayOfWeek: b.dayOfWeek,
          bookingDate: b.bookingDate,
          startHour: b.startHour,
          durationHours: b.durationHours,
          customerName: b.customerName,
          totalPrice: b.totalPrice,
          status: "CANCELLED",
          createdAt: Date.now(),
        });
        P.saveStore(store, { source: "editor_cancel" });
        if (typeof P.updateCloudSlot === "function") {
          P.updateCloudSlot(b, { status: "DIBATALKAN" });
        }
        P.showToast(`Jadwal ${b.courtName} milik ${b.customerName} dibatalkan.`);
        renderAll();
      };

      listEl.appendChild(card);
    });
  }

  function getFilteredDatabaseBookings() {
    const q = (document.getElementById("dbSearchInput")?.value || "").trim().toLowerCase();
    const statusF = document.getElementById("dbStatusFilter")?.value || "ALL";
    const dayF = Number(document.getElementById("dbDayFilter")?.value || 0);

    return [...store.bookings]
      .filter((b) => {
        if (statusF !== "ALL" && b.paymentStatus !== statusF) return false;
        if (dayF !== 0 && Number(b.dayOfWeek) !== dayF) return false;
        if (q) {
          const hay = `${b.customerName || ""} ${b.customerPhone || ""} ${b.courtName || ""} ${b.notes || ""}`.toLowerCase();
          if (!hay.includes(q)) return false;
        }
        return true;
      })
      .sort((a, b) => Number(b.createdAt || 0) - Number(a.createdAt || 0));
  }

  function renderDatabaseTable() {
    const tbody = document.getElementById("edDatabaseTableBody");
    if (!tbody) return;
    const rows = getFilteredDatabaseBookings();
    tbody.innerHTML = "";
    if (rows.length === 0) {
      tbody.innerHTML = `<tr><td colspan="9" style="text-align:center;color:var(--muted);padding:18px">Belum ada data orang pesan yang cocok.</td></tr>`;
      return;
    }

    rows.forEach((b, idx) => {
      const remaining = Math.max(Number(b.totalPrice || 0) - Number(b.paidAmount || 0), 0);
      const statusClass = b.paymentStatus === "PAID" ? "status-paid" : "status-pending";
      const statusLabel = b.paymentStatus === "PAID" ? "Lunas" : b.paymentStatus === "DP" ? "DP Sebagian" : "Belum Bayar";
      const dayDateText = P.formatDayAndDate(b.dayOfWeek, b.bookingDate);
      const extras = [
        b.racketCount > 0 ? `Raket ${b.racketCount}x` : "",
        b.cockCount > 0 ? `Cock ${b.cockCount}x` : "",
        b.notes || ""
      ].filter(Boolean).join(" • ") || "-";

      const tr = document.createElement("tr");
      tr.innerHTML = `
        <td class="mono">${idx + 1}</td>
        <td>
          <div style="font-weight:800">${b.customerName}</div>
          <div class="mono" style="font-size:12px;color:var(--muted)">${b.customerPhone || "-"}</div>
        </td>
        <td><strong>${b.courtName}</strong></td>
        <td>${dayDateText}</td>
        <td class="mono" style="white-space:nowrap">${P.formatRange(b.startHour, b.durationHours)}<br/><span style="font-size:11px;color:var(--muted)">${b.durationHours} jam</span></td>
        <td style="font-size:12px;max-width:190px">${extras}</td>
        <td class="mono" style="white-space:nowrap">
          <strong>${P.formatRupiah(b.totalPrice)}</strong>
          ${remaining > 0 ? `<div style="font-size:11px;color:var(--pending-fg)">Sisa: ${P.formatRupiah(remaining)}</div>` : ""}
        </td>
        <td><span class="status-pill ${statusClass}">${statusLabel}</span></td>
        <td>
          <div class="chip-row">
            ${b.paymentStatus !== "PAID" ? `<button type="button" class="btn btn-primary" style="padding:6px 10px;min-height:36px;font-size:12px" data-db-act="paid">Lunas</button>` : ""}
            ${b.customerPhone ? `<button type="button" class="btn btn-outline" style="padding:6px 10px;min-height:36px;font-size:12px" data-db-act="wa">WA</button>` : ""}
            <button type="button" class="btn btn-danger" style="padding:6px 10px;min-height:36px;font-size:12px" data-db-act="del">Hapus</button>
          </div>
        </td>
      `;

      const dbPaid = tr.querySelector('[data-db-act="paid"]');
      if (dbPaid) {
        dbPaid.onclick = () => {
          b.paymentStatus = "PAID";
          b.paidAmount = b.totalPrice;
          P.saveStore(store, { source: "editor_db_paid" });
          if (typeof P.updateCloudSlot === "function") {
            P.updateCloudSlot(b, { paymentStatus: "PAID", paidAmount: b.totalPrice, reminderEnabled: false });
          }
          P.showToast(`${b.customerName} ditandai Lunas.`);
          renderAll();
        };
      }

      const dbWa = tr.querySelector('[data-db-act="wa"]');
      if (dbWa) {
        dbWa.onclick = () => {
          const msg = `Halo ${b.customerName}, konfirmasi jadwal ${b.courtName} (${dayDateText}, ${P.formatRange(b.startHour, b.durationHours)}). Total: ${P.formatRupiah(b.totalPrice)}. — ${store.venue.name}`;
          window.open(P.buildWhatsAppUrl(b.customerPhone, msg), "_blank", "noopener");
        };
      }

      tr.querySelector('[data-db-act="del"]').onclick = () => {
        store.bookings = store.bookings.filter((item) => item.id !== b.id);
        store.history.unshift({
          id: Date.now(),
          courtId: b.courtId,
          courtName: b.courtName,
          dayOfWeek: b.dayOfWeek,
          bookingDate: b.bookingDate,
          startHour: b.startHour,
          durationHours: b.durationHours,
          customerName: b.customerName,
          totalPrice: b.totalPrice,
          status: "CANCELLED",
          createdAt: Date.now(),
        });
        P.saveStore(store, { source: "editor_db_delete" });
        if (typeof P.updateCloudSlot === "function") {
          P.updateCloudSlot(b, { status: "DIBATALKAN" });
        }
        P.showToast(`Data ${b.customerName} (${b.courtName}) dihapus dari database aktif.`);
        renderAll();
      };

      tbody.appendChild(tr);
    });
  }

  function renderCourts() {
    const gridEl = document.getElementById("editorCourtsGrid");
    gridEl.innerHTML = "";
    store.courts.forEach((c) => {
      const box = document.createElement("div");
      box.className = "card";
      box.style.boxShadow = "none";
      box.innerHTML = `
        <div class="form-grid">
          <div class="field">
            <label>Nama Lapangan</label>
            <input type="text" value="${c.name}" data-field="name" />
          </div>
          <div class="field">
            <label>Tarif Sewa per Jam (Rp)</label>
            <input type="number" min="0" step="1000" value="${c.rate}" data-field="rate" />
          </div>
          <label style="display:flex;align-items:center;gap:8px;font-weight:700;cursor:pointer">
            <input type="checkbox" ${c.active ? "checked" : ""} data-field="active" />
            Lapangan Aktif (Tampil di Halaman Orang Pesan)
          </label>
          <button type="button" class="btn btn-primary">Simpan Perubahan ${c.name}</button>
        </div>
      `;
      box.querySelector("button").onclick = () => {
        c.name = box.querySelector('[data-field="name"]').value.trim() || c.name;
        c.rate = Number(box.querySelector('[data-field="rate"]').value || 15000);
        c.active = box.querySelector('[data-field="active"]').checked;
        P.saveStore(store);
        P.showToast(`Pengaturan ${c.name} disimpan.`);
        renderAll();
      };
      gridEl.appendChild(box);
    });
  }

  function renderVenueForm() {
    document.getElementById("edVenueName").value = store.venue.name;
    document.getElementById("edVenuePhone").value = store.venue.phone;
    document.getElementById("edVenueMaps").value = store.venue.mapsUrl;
    document.getElementById("edNewPin").value = store.editorPin || P.DEFAULT_EDITOR_PIN || "00000";
  }

  function renderAll() {
    store = P.loadStore();
    renderSummary();
    renderDatabaseTable();
    renderAdminFormSelects();
    renderBookings();
    renderCourts();
    renderVenueForm();
  }

  document.getElementById("editorBookingForm").addEventListener("submit", (e) => {
    e.preventDefault();
    store = P.loadStore();
    const courtVal = document.getElementById("edBookCourt").value;
    const activeCourts = store.courts.filter((c) => c.active);
    const targetCourts = courtVal === "BOTH"
      ? activeCourts.slice(0, 2)
      : activeCourts.filter((c) => String(c.id) === String(courtVal));
    const chosenDate = document.getElementById("edBookDate").value || P.getDateForDayOfWeek(P.todayIsoDay());
    const dayOfWeek = P.isoDateToDayOfWeek(chosenDate);
    const courtEntries = targetCourts.map((c) => ({
      courtId: c.id,
      startHour: P.parseManualHour(document.getElementById(`edStart_${c.id}`).value, false),
      endHour: P.parseManualHour(document.getElementById(`edEnd_${c.id}`).value, true),
    }));
    const res = P.createCourtTimeBookings({
      store,
      courtEntries,
      dayOfWeek,
      bookingDate: chosenDate,
      customerName: document.getElementById("edBookName").value,
      customerPhone: document.getElementById("edBookPhone").value,
      paymentStatus: document.getElementById("edBookStatus").value,
      paidAmount: Number(document.getElementById("edBookDp").value || 0),
      racketCount: Number(document.getElementById("edBookRackets").value || 0),
      cockCount: Number(document.getElementById("edBookCocks").value || 0),
      notes: "Input Editor Website",
    });
    if (res.error) {
      P.showToast(res.error);
      return;
    }
    const todayDate = P.getDateForDayOfWeek(P.todayIsoDay());
    document.getElementById("editorBookingForm").reset();
    document.getElementById("edBookDate").value = todayDate;
    syncEditorDateBadge(todayDate);
    document.getElementById("edBookDp").value = "10000";
    document.getElementById("edBookDpField").classList.add("hidden");
    P.showToast("Jadwal admin berhasil disimpan.");
    renderAll();
  });

  document.getElementById("addCourtBtn").onclick = () => {
    store = P.loadStore();
    const nextIdx = store.courts.length;
    const nextName = nextIdx < 26 ? `Lapangan ${String.fromCharCode(65 + nextIdx)}` : `Lapangan ${nextIdx + 1}`;
    store.courts.push({ id: Date.now(), name: nextName, rate: 15000, active: true, note: "" });
    P.saveStore(store);
    P.showToast(`${nextName} ditambahkan.`);
    renderAll();
  };

  document.getElementById("venueEditorForm").addEventListener("submit", (e) => {
    e.preventDefault();
    store.venue.name = document.getElementById("edVenueName").value.trim() || store.venue.name;
    store.venue.phone = document.getElementById("edVenuePhone").value.trim() || store.venue.phone;
    store.venue.mapsUrl = document.getElementById("edVenueMaps").value.trim() || store.venue.mapsUrl;
    store.editorPin = document.getElementById("edNewPin").value.trim() || P.DEFAULT_EDITOR_PIN || "00000";
    P.saveStore(store, { source: "editor_venue" });
    P.showToast("Informasi website & PIN berhasil disimpan.");
    renderAll();
  });

  document.getElementById("exportJsonBtn").onclick = () => {
    const blob = new Blob([JSON.stringify(store, null, 2)], { type: "application/json" });
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = "manisrejo-badminton-backup.json";
    a.click();
  };

  document.getElementById("resetDefaultBtn").onclick = () => {
    store = P.resetStore();
    P.showToast("Data website dikembalikan ke pengaturan awal.");
    renderAll();
  };

  ["dbSearchInput", "dbStatusFilter", "dbDayFilter"].forEach((id) => {
    const el = document.getElementById(id);
    if (el) {
      el.addEventListener(id === "dbSearchInput" ? "input" : "change", renderDatabaseTable);
    }
  });

  const exportCsvBtn = document.getElementById("exportCsvBtn");
  if (exportCsvBtn) {
    exportCsvBtn.onclick = () => {
      store = P.loadStore();
      const csv = P.exportBookingsCsv(getFilteredDatabaseBookings());
      const blob = new Blob(["\uFEFF" + csv], { type: "text/csv;charset=utf-8;" });
      const a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = "database-orang-pesan-manisrejo.csv";
      a.click();
      P.showToast("Database orang pesan berhasil diunduh (.csv).");
    };
  }

  function updateEditorSyncBadge() {
    const btn = document.getElementById("edSyncStatusBtn");
    const labelEl = document.getElementById("edSyncStatusLabel");
    const cloudBtn = document.getElementById("edConnectCloudDbBtn");
    if (!btn || !labelEl || typeof P.getSyncStatus !== "function") return;
    const st = P.getSyncStatus();
    const clock = P.formatTimeClock(st.lastSyncedAt);
    if (st.mode === "CLOUD_LIVE") {
      btn.classList.add("cloud");
      btn.title = `Database Cloud aktif (${st.userEmail || "Firestore"}) — klik untuk putuskan`;
      labelEl.textContent = `Cloud DB Aktif • ${clock}`;
      if (cloudBtn) cloudBtn.textContent = "Putuskan Cloud DB";
    } else {
      btn.classList.remove("cloud");
      btn.title = "Database Lokal Real-Time aktif — klik untuk hubungkan Cloud DB lintas perangkat";
      labelEl.textContent = `Sinkron Otomatis • ${clock}`;
      if (cloudBtn) cloudBtn.textContent = "Hubungkan Cloud DB";
    }
  }

  async function toggleEditorCloudSync() {
    const st = P.getSyncStatus();
    if (st.mode === "CLOUD_LIVE" && typeof P.disconnectCloudSync === "function") {
      await P.disconnectCloudSync();
    } else if (typeof P.connectCloudSync === "function") {
      await P.connectCloudSync();
    }
    updateEditorSyncBadge();
  }

  const edSyncBtn = document.getElementById("edSyncStatusBtn");
  if (edSyncBtn) {
    edSyncBtn.onclick = toggleEditorCloudSync;
  }
  const edCloudBtn = document.getElementById("edConnectCloudDbBtn");
  if (edCloudBtn) {
    edCloudBtn.onclick = toggleEditorCloudSync;
  }

  if (typeof P.onSync === "function") {
    P.onSync((ev) => {
      updateEditorSyncBadge();
      if (ev.reason === "local_write") return;
      if (sessionStorage.getItem(SESSION_KEY) === "1") {
        store = P.loadStore();
        renderSummary();
        renderDatabaseTable();
        renderBookings();
        const activeEl = document.activeElement;
        const editingForm =
          (document.getElementById("editorBookingForm")?.contains(activeEl)) ||
          (document.getElementById("editorCourtsGrid")?.contains(activeEl)) ||
          (document.getElementById("venueEditorForm")?.contains(activeEl));
        if (!editingForm) {
          renderCourts();
          renderVenueForm();
        }
        if (ev.reason === "cross_tab" || ev.reason === "storage_event" || ev.reason === "cloud_snapshot") {
          P.showToast("Data orang pesan baru otomatis disinkronkan ke Editor!");
        }
      }
    });
  }

  checkAuth();
  updateEditorSyncBadge();
})();
