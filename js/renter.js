(function () {
  const P = window.ManisrejoPortal;
  let store = P.loadStore();
  let selectedDay = P.todayIsoDay();
  let selectedDate = P.getDateForDayOfWeek(selectedDay);
  let courtFilter = 0;
  let statusFilter = "ALL";
  let selectedCourtIds = new Set([1]);
  const courtHoursMap = {}; // { [courtId]: { start: "18:00", end: "20:00" } }
  let historyCourtFilter = 0;
  let racketCount = 0;
  let cockCount = 0;

  const dayStripEl = document.getElementById("dayStrip");
  const slotListEl = document.getElementById("slotList");
  const weeklyListEl = document.getElementById("weeklyOverviewList");
  const historyListEl = document.getElementById("historyList");
  const inputDateEl = document.getElementById("inputDate");
  const inputDayBadgeEl = document.getElementById("inputDayBadge");
  const perCourtHoursEl = document.getElementById("perCourtHoursContainer");
  const syncHoursBtn = document.getElementById("syncHoursBtn");

  function syncDateAndDayBadge(dayId, dateStr) {
    if (inputDateEl) inputDateEl.value = dateStr;
    if (inputDayBadgeEl) inputDayBadgeEl.textContent = P.formatDayAndDate(dayId, dateStr);
  }

  function ensureCourtHoursInitialized() {
    const active = store.courts.filter((c) => c.active);
    active.forEach((c) => {
      if (!courtHoursMap[c.id]) {
        courtHoursMap[c.id] = { start: "18:00", end: "20:00" };
      }
    });
    const validIds = [...selectedCourtIds].filter((id) => active.some((c) => c.id === id));
    if (validIds.length === 0 && active.length > 0) {
      selectedCourtIds = new Set([active[0].id]);
    } else {
      selectedCourtIds = new Set(validIds);
    }
  }

  syncDateAndDayBadge(selectedDay, selectedDate);

  function switchTab(tab) {
    const tabs = {
      jadwal: ["tabJadwalBtn", "sectionJadwal"],
      mingguan: ["tabMingguanBtn", "sectionMingguan"],
      riwayat: ["tabRiwayatBtn", "sectionRiwayat"],
    };
    Object.entries(tabs).forEach(([key, [btnId, secId]]) => {
      document.getElementById(btnId).classList.toggle("active", key === tab);
      document.getElementById(secId).classList.toggle("hidden", key !== tab);
    });
    if (tab === "mingguan") renderWeekly();
    if (tab === "riwayat") renderHistory();
  }

  document.getElementById("tabJadwalBtn").onclick = () => switchTab("jadwal");
  document.getElementById("tabMingguanBtn").onclick = () => switchTab("mingguan");
  document.getElementById("tabRiwayatBtn").onclick = () => switchTab("riwayat");

  function getSelectedCourts() {
    return store.courts.filter((c) => c.active && selectedCourtIds.has(c.id));
  }

  function updatePricePreview() {
    const courts = getSelectedCourts();
    const summaryEl = document.getElementById("priceSummaryText");
    const totalEl = document.getElementById("priceTotalText");

    if (courts.length === 0) {
      summaryEl.textContent = "Pilih minimal 1 lapangan";
      totalEl.textContent = "-";
      return;
    }

    let totalPrice = 0;
    const summaryParts = [];

    for (const c of courts) {
      const pair = courtHoursMap[c.id] || { start: "", end: "" };
      const s = P.parseManualHour(pair.start, false);
      const e = P.parseManualHour(pair.end, true);
      if (s === null || e === null || s < P.OPEN_HOUR || s >= P.CLOSE_HOUR || e <= s || e > P.CLOSE_HOUR) {
        summaryEl.textContent = `Jam ${c.name} belum valid (isi 08:00 – 00:00)`;
        totalEl.textContent = "-";
        return;
      }
      const dur = e - s;
      totalPrice += c.rate * dur;
      summaryParts.push(`${c.name} (${P.formatRange(s, dur)})`);
    }

    const chosenDate = inputDateEl.value || selectedDate;
    const chosenDay = P.isoDateToDayOfWeek(chosenDate);
    const dateText = P.formatDayAndDate(chosenDay, chosenDate);
    const extrasPrice = P.calculateExtrasPrice(racketCount, cockCount);
    const extrasSummary = P.formatExtrasSummary(racketCount, cockCount);
    const summaryText = extrasSummary
      ? `${summaryParts.join(" & ")} • ${extrasSummary} • ${dateText}`
      : `${summaryParts.join(" & ")} • ${dateText}`;
    summaryEl.textContent = summaryText;
    totalEl.textContent = P.formatRupiah(totalPrice + extrasPrice);
  }

  function renderAddonsCount() {
    const rEl = document.getElementById("racketCountText");
    const cEl = document.getElementById("cockCountText");
    if (rEl) rEl.textContent = String(racketCount);
    if (cEl) cEl.textContent = String(cockCount);
  }

  document.getElementById("racketMinusBtn").onclick = () => {
    racketCount = Math.max(0, racketCount - 1);
    renderAddonsCount();
    updatePricePreview();
  };
  document.getElementById("racketPlusBtn").onclick = () => {
    racketCount += 1;
    renderAddonsCount();
    updatePricePreview();
  };
  document.getElementById("cockMinusBtn").onclick = () => {
    cockCount = Math.max(0, cockCount - 1);
    renderAddonsCount();
    updatePricePreview();
  };
  document.getElementById("cockPlusBtn").onclick = () => {
    cockCount += 1;
    renderAddonsCount();
    updatePricePreview();
  };

  function renderPerCourtHourInputs() {
    ensureCourtHoursInitialized();
    const courts = getSelectedCourts();
    perCourtHoursEl.innerHTML = "";
    syncHoursBtn.classList.toggle("hidden", courts.length < 2);

    courts.forEach((court) => {
      const pair = courtHoursMap[court.id] || { start: "18:00", end: "20:00" };
      const box = document.createElement("div");
      box.className = "stat-box";
      box.style.background = "var(--surface-soft)";
      box.innerHTML = `
        <div style="display:flex;justify-content:space-between;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:8px">
          <div style="font-weight:800;font-size:14px;color:var(--emerald-dark)">
            Kolom ${court.name}
          </div>
          <span class="status-pill status-paid">${P.formatRupiah(court.rate)}/jam</span>
        </div>
        <div class="grid-2">
          <div class="field">
            <label for="startHour_${court.id}">Jam Mulai</label>
            <input id="startHour_${court.id}" type="text" inputmode="numeric" value="${pair.start}" placeholder="08:00" required />
          </div>
          <div class="field">
            <label for="endHour_${court.id}">Jam Selesai</label>
            <input id="endHour_${court.id}" type="text" inputmode="numeric" value="${pair.end}" placeholder="00:00" required />
          </div>
        </div>
      `;
      const startInput = box.querySelector(`#startHour_${court.id}`);
      const endInput = box.querySelector(`#endHour_${court.id}`);
      startInput.addEventListener("input", () => {
        courtHoursMap[court.id].start = startInput.value;
        updatePricePreview();
      });
      endInput.addEventListener("input", () => {
        courtHoursMap[court.id].end = endInput.value;
        updatePricePreview();
      });
      perCourtHoursEl.appendChild(box);
    });
  }

  syncHoursBtn.onclick = () => {
    const courts = getSelectedCourts();
    if (courts.length < 2) return;
    const first = courtHoursMap[courts[0].id];
    courts.forEach((c) => {
      courtHoursMap[c.id] = { start: first.start, end: first.end };
    });
    renderPerCourtHourInputs();
    updatePricePreview();
    P.showToast("Jam manual disamakan untuk semua lapangan terpilih.");
  };

  function renderVenueLinks() {
    document.getElementById("venueNameNav").textContent = store.venue.name;
    document.getElementById("venueNameHero").textContent = store.venue.name;
    document.getElementById("footerVenueName").textContent = store.venue.name;

    const todayFull = P.formatTodayFull();
    const navDateEl = document.getElementById("todayNavDate");
    if (navDateEl) navDateEl.textContent = `Portal Penyewa • ${todayFull} • 08:00 – 00:00`;
    const heroDateEl = document.getElementById("heroTodayBadge");
    if (heroDateEl) heroDateEl.textContent = `HARI INI: ${todayFull.toUpperCase()}`;

    const waUrl = P.buildWhatsAppUrl(store.venue.phone, `Halo Admin ${store.venue.name}, saya ingin konfirmasi jadwal lapangan.`);
    ["waHeroBtn", "waFooterBtn"].forEach((id) => (document.getElementById(id).href = waUrl));
    document.getElementById("waHeroBtn").textContent = `WhatsApp ${store.venue.phone}`;
    document.getElementById("waFooterBtn").textContent = `WA ${store.venue.phone}`;
    ["mapsHeroBtn", "mapsFooterBtn"].forEach((id) => (document.getElementById(id).href = store.venue.mapsUrl));
  }

  function matchesDayOrDate(booking, dayId, dateStr) {
    if (booking.bookingDate) return booking.bookingDate === dateStr;
    return booking.dayOfWeek === dayId;
  }

  function renderDayStrip() {
    dayStripEl.innerHTML = "";
    P.DAYS.forEach((d) => {
      const dDate = P.getDateForDayOfWeek(d.id);
      const count = store.bookings.filter(
        (b) => matchesDayOrDate(b, d.id, dDate) && (courtFilter === 0 || b.courtId === courtFilter)
      ).length;
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = `day-btn ${selectedDay === d.id && selectedDate === dDate ? "active" : ""}`;
      btn.innerHTML = `
        <span>${d.short}</span>
        <span style="font-size:11px;opacity:0.85">${P.formatDateShort(dDate)}</span>
        <span class="day-count">${count}</span>
      `;
      btn.onclick = () => {
        selectedDay = d.id;
        selectedDate = dDate;
        syncDateAndDayBadge(d.id, dDate);
        renderAll();
      };
      dayStripEl.appendChild(btn);
    });
  }

  function renderFilters() {
    ensureCourtHoursInitialized();
    const activeCourts = store.courts.filter((c) => c.active);
    const courtChipsEl = document.getElementById("courtFilterChips");
    courtChipsEl.innerHTML = "";

    const allBtn = document.createElement("button");
    allBtn.type = "button";
    allBtn.className = `chip ${courtFilter === 0 ? "active" : ""}`;
    allBtn.textContent = "Semua Lapangan";
    allBtn.onclick = () => { courtFilter = 0; renderAll(); };
    courtChipsEl.appendChild(allBtn);

    activeCourts.forEach((c) => {
      const chip = document.createElement("button");
      chip.type = "button";
      chip.className = `chip ${courtFilter === c.id ? "active" : ""}`;
      chip.textContent = c.name;
      chip.onclick = () => { courtFilter = c.id; renderAll(); };
      courtChipsEl.appendChild(chip);
    });

    const statusEl = document.getElementById("statusFilterChips");
    statusEl.innerHTML = "";
    [
      ["ALL", "Semua Slot"],
      ["AVAILABLE", "Tersedia"],
      ["BOOKED", "Sudah Dipesan"],
    ].forEach(([code, label]) => {
      const chip = document.createElement("button");
      chip.type = "button";
      chip.className = `chip ${statusFilter === code ? "active" : ""}`;
      chip.textContent = label;
      chip.onclick = () => { statusFilter = code; renderFilters(); renderSlots(); };
      statusEl.appendChild(chip);
    });

    const modeEl = document.getElementById("formCourtModeChips");
    modeEl.innerHTML = "";
    activeCourts.forEach((c) => {
      const isSingleSelected = selectedCourtIds.size === 1 && selectedCourtIds.has(c.id);
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = `chip ${isSingleSelected ? "active" : ""}`;
      btn.textContent = c.name;
      btn.onclick = () => {
        selectedCourtIds = new Set([c.id]);
        renderFilters();
        renderPerCourtHourInputs();
        updatePricePreview();
      };
      modeEl.appendChild(btn);
    });

    if (activeCourts.length >= 2) {
      const twoIds = activeCourts.slice(0, 2).map((c) => c.id);
      const isBoth = selectedCourtIds.size === twoIds.length && twoIds.every((id) => selectedCourtIds.has(id));
      const bothBtn = document.createElement("button");
      bothBtn.type = "button";
      bothBtn.className = `chip ${isBoth ? "active" : ""}`;
      bothBtn.textContent = "A & B (2 Lapangan)";
      bothBtn.onclick = () => {
        selectedCourtIds = new Set(twoIds);
        renderFilters();
        renderPerCourtHourInputs();
        updatePricePreview();
      };
      modeEl.appendChild(bothBtn);
    }
  }

  function renderSlots() {
    const visibleCourts = store.courts.filter((c) => c.active && (courtFilter === 0 || c.id === courtFilter));
    const dayBookings = store.bookings.filter(
      (b) => matchesDayOrDate(b, selectedDay, selectedDate) && (courtFilter === 0 || b.courtId === courtFilter)
    );
    const bookedHours = dayBookings.reduce((sum, b) => sum + b.durationHours, 0);
    const totalCap = Math.max(visibleCourts.length * (P.CLOSE_HOUR - P.OPEN_HOUR), 1);
    document.getElementById("statBookedHours").textContent = `${bookedHours} jam`;
    document.getElementById("statAvailableHours").textContent = `${Math.max(totalCap - bookedHours, 0)} jam`;
    document.getElementById("scheduleHeading").textContent = `Jadwal Lapangan • ${P.formatDayAndDate(selectedDay, selectedDate)}`;

    slotListEl.innerHTML = "";
    const hoursPerCourt = P.CLOSE_HOUR - P.OPEN_HOUR;

    visibleCourts.forEach((court) => {
      const courtBookings = dayBookings.filter((b) => b.courtId === court.id);
      const courtBookedHours = courtBookings.reduce((sum, b) => sum + b.durationHours, 0);
      const courtAvailableHours = Math.max(hoursPerCourt - courtBookedHours, 0);

      const columnEl = document.createElement("div");
      columnEl.className = "court-column";
      columnEl.innerHTML = `
        <div class="court-column-header">
          <div>
            <h3 class="court-column-title">Kolom ${court.name}</h3>
            <div style="font-size:12px;color:var(--muted);font-weight:600">${courtBookedHours} jam dipesan • ${courtAvailableHours} jam tersedia</div>
          </div>
          <span class="status-pill status-paid">${P.formatRupiah(court.rate)}/jam</span>
        </div>
        <div class="slot-list"></div>
      `;

      const listEl = columnEl.querySelector(".slot-list");
      const renderedBookingIds = new Set();

      for (let h = P.OPEN_HOUR; h < P.CLOSE_HOUR; h++) {
        const booking = courtBookings.find(
          (b) => h >= b.startHour && h < b.startHour + b.durationHours
        );
        if (booking) {
          if (statusFilter !== "AVAILABLE" && !renderedBookingIds.has(booking.id)) {
            renderedBookingIds.add(booking.id);
            const item = document.createElement("div");
            item.className = "slot-item booked";
            const dayDateLabel = P.formatDayAndDate(booking.dayOfWeek, booking.bookingDate);
            const waConfirmUrl = P.buildWhatsAppUrl(
              store.venue.phone,
              `Halo Admin ${store.venue.name}, konfirmasi jadwal ${booking.courtName} (${dayDateLabel}, ${P.formatRange(booking.startHour, booking.durationHours)}) atas nama ${booking.customerName}.`
            );
            item.innerHTML = `
              <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
                <span class="time-badge mono">${P.formatRange(booking.startHour, booking.durationHours)}</span>
                <div>
                  <div style="font-weight:800">${booking.customerName}</div>
                  <div style="font-size:12px;color:var(--muted);margin-bottom:4px">${dayDateLabel}</div>
                  <span class="status-pill status-booked">Sudah Dipesan</span>
                </div>
              </div>
              <a href="${waConfirmUrl}" target="_blank" rel="noopener" class="btn btn-outline">Konfirmasi WA</a>
            `;
            listEl.appendChild(item);
          }
        } else if (statusFilter !== "BOOKED") {
          const item = document.createElement("div");
          item.className = "slot-item";
          item.innerHTML = `
            <div style="display:flex;align-items:center;gap:10px;flex-wrap:wrap">
              <span class="time-badge mono">${P.formatRange(h, 1)}</span>
              <div>
                <div style="font-weight:800;color:var(--paid-fg);font-size:13px">Tersedia</div>
                <div style="font-size:12px;color:var(--muted)">${P.formatRupiah(court.rate)}/jam</div>
              </div>
            </div>
            <button type="button" class="btn btn-primary">Pesan</button>
          `;
          item.querySelector("button").onclick = () => {
            selectedCourtIds = new Set([court.id]);
            courtHoursMap[court.id] = {
              start: P.formatHour(h),
              end: P.formatHour(Math.min(h + 1, P.CLOSE_HOUR)),
            };
            syncDateAndDayBadge(selectedDay, selectedDate);
            renderFilters();
            renderPerCourtHourInputs();
            updatePricePreview();
            document.getElementById("form-booking").scrollIntoView({ behavior: "smooth" });
          };
          listEl.appendChild(item);
        }
      }

      if (listEl.children.length === 0) {
        const emptyMsg = document.createElement("p");
        emptyMsg.style.cssText = "color:var(--muted);font-size:13px;margin:4px 0";
        emptyMsg.textContent = statusFilter === "BOOKED"
          ? `Belum ada jadwal dipesan di ${court.name}.`
          : `Tidak ada slot kosong tersisa di ${court.name}.`;
        listEl.appendChild(emptyMsg);
      }

      slotListEl.appendChild(columnEl);
    });
  }

  function renderWeekly() {
    weeklyListEl.innerHTML = "";
    const activeCourts = store.courts.filter((c) => c.active);
    P.DAYS.forEach((d) => {
      const dDate = P.getDateForDayOfWeek(d.id);
      const dayBookings = store.bookings
        .filter((b) => matchesDayOrDate(b, d.id, dDate))
        .sort((a, b) => a.startHour - b.startHour);
      const bookedSummary =
        dayBookings.length === 0
          ? "Belum ada jadwal yang dipesan"
          : dayBookings
              .map((b) => `• ${b.courtName} (${P.formatRange(b.startHour, b.durationHours)}) — ${b.customerName}`)
              .join("<br/>");
      const availSummary = activeCourts
        .map((c) => `• ${c.name}: ${P.formatAvailableRangesSummary(dayBookings.filter((b) => b.courtId === c.id))}`)
        .join("<br/>");

      const card = document.createElement("div");
      card.className = "card";
      card.innerHTML = `
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:10px;gap:8px;flex-wrap:wrap">
          <strong style="font-size:17px">${P.formatDayAndDate(d.id, dDate)}</strong>
          <button type="button" class="btn btn-outline">Lihat / Pesan</button>
        </div>
        <div class="grid-2">
          <div class="stat-box stat-booked">
            <div style="font-size:12px;font-weight:800;margin-bottom:4px">SUDAH DIPESAN (${dayBookings.length})</div>
            <div style="font-size:13px">${bookedSummary}</div>
          </div>
          <div class="stat-box stat-available">
            <div style="font-size:12px;font-weight:800;margin-bottom:4px">JAM TERSEDIA</div>
            <div style="font-size:13px">${availSummary}</div>
          </div>
        </div>
      `;
      card.querySelector("button").onclick = () => {
        selectedDay = d.id;
        selectedDate = dDate;
        syncDateAndDayBadge(d.id, dDate);
        renderAll();
        switchTab("jadwal");
      };
      weeklyListEl.appendChild(card);
    });
  }

  function renderHistory() {
    const chipsEl = document.getElementById("historyFilterChips");
    chipsEl.innerHTML = "";
    [{ id: 0, name: "Semua Lapangan" }, ...store.courts].forEach((c) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.className = `chip ${historyCourtFilter === c.id ? "active" : ""}`;
      btn.textContent = c.name;
      btn.onclick = () => { historyCourtFilter = c.id; renderHistory(); };
      chipsEl.appendChild(btn);
    });

    historyListEl.innerHTML = "";
    const list = store.history.filter((h) => historyCourtFilter === 0 || h.courtId === historyCourtFilter);
    if (list.length === 0) {
      historyListEl.innerHTML = `<p style="color:var(--muted)">Belum ada riwayat pemesanan.</p>`;
      return;
    }
    list.forEach((h) => {
      const row = document.createElement("div");
      row.className = "slot-item";
      row.innerHTML = `
        <div>
          <div style="font-weight:800">${h.customerName} — ${h.courtName}</div>
          <div style="font-size:13px;color:var(--muted)">${P.formatDayAndDate(h.dayOfWeek, h.bookingDate)}, ${P.formatRange(h.startHour, h.durationHours)} (${h.durationHours} jam)</div>
          ${h.notes ? `<div style="font-size:12px;color:var(--emerald-dark);font-weight:600;margin-top:2px">${h.notes}</div>` : ""}
        </div>
        <div style="text-align:right">
          <div class="mono" style="font-weight:800">${P.formatRupiah(h.totalPrice)}</div>
          <span class="status-pill ${h.status === "CANCELLED" ? "status-booked" : "status-paid"}">${h.status === "CANCELLED" ? "Dibatalkan" : "Tercatat"}</span>
        </div>
      `;
      historyListEl.appendChild(row);
    });
  }

  function renderAll() {
    store = P.loadStore();
    renderVenueLinks();
    renderDayStrip();
    renderFilters();
    renderPerCourtHourInputs();
    renderSlots();
    updatePricePreview();
  }

  inputDateEl.addEventListener("change", () => {
    if (inputDateEl.value) {
      selectedDate = inputDateEl.value;
      selectedDay = P.isoDateToDayOfWeek(inputDateEl.value);
      syncDateAndDayBadge(selectedDay, selectedDate);
      renderAll();
    }
  });

  document.getElementById("renterBookingForm").addEventListener("submit", (e) => {
    e.preventDefault();
    store = P.loadStore();
    const courts = getSelectedCourts();
    const chosenDate = inputDateEl.value || selectedDate;
    const day = P.isoDateToDayOfWeek(chosenDate);
    const name = document.getElementById("inputCustomerName").value;
    const phone = document.getElementById("inputCustomerPhone").value;

    const courtEntries = courts.map((c) => {
      const pair = courtHoursMap[c.id] || { start: "", end: "" };
      return {
        courtId: c.id,
        startHour: P.parseManualHour(pair.start, false),
        endHour: P.parseManualHour(pair.end, true),
      };
    });

    const res = P.createCourtTimeBookings({
      store,
      courtEntries,
      dayOfWeek: day,
      bookingDate: chosenDate,
      customerName: name,
      customerPhone: phone,
      paymentStatus: "UNPAID",
      paidAmount: 0,
      racketCount,
      cockCount,
      notes: "",
    });

    if (res.error) {
      P.showToast(res.error);
      return;
    }

    const totalCombined = res.bookings.reduce((sum, b) => sum + b.totalPrice, 0);
    const courtNames = res.bookings.map((b) => b.courtName).join(" & ");
    const perCourtSummary = res.bookings
      .map((b) => `${b.courtName} (${P.formatRange(b.startHour, b.durationHours)})`)
      .join(" & ");
    const extrasLine = P.formatExtrasSummary(racketCount, cockCount);
    const summary = `${perCourtSummary} • ${P.formatDayAndDate(day, chosenDate)}`;
    const waText = `Halo Admin ${store.venue.name} (${store.venue.phone}), saya ${name.trim()} ingin konfirmasi pemesanan:\n• Jadwal: ${summary}` +
      (extrasLine ? `\n• Tambahan: ${extrasLine}` : "") +
      `\n• Total: ${P.formatRupiah(totalCombined)}`;

    selectedDay = day;
    selectedDate = chosenDate;
    racketCount = 0;
    cockCount = 0;
    renderAddonsCount();
    document.getElementById("renterBookingForm").reset();
    syncDateAndDayBadge(selectedDay, selectedDate);
    renderAll();
    P.showToast(`Jadwal ${courtNames} tersimpan! Membuka WhatsApp konfirmasi...`);
    window.open(P.buildWhatsAppUrl(store.venue.phone, waText), "_blank", "noopener");
  });

  renderAll();
})();
