(function () {
  const STORAGE_KEY = "manisrejo_badminton_portal_v3";
  const SYNC_CHANNEL_NAME = "manisrejo_badminton_sync_v1";
  const DEFAULT_EDITOR_PIN = "00000";
  const OPEN_HOUR = 8;
  const CLOSE_HOUR = 24;
  const RACKET_PRICE = 10000;
  const COCK_PRICE = 12000;

  // Provisioned Firebase config for real-time cross-device cloud sync
  const FIREBASE_CONFIG = {
    apiKey: "AIzaSyC40vJWxGNQrTmFK6dj6afzkDjMNIYN7y4",
    authDomain: "gen-lang-client-0641484956.firebaseapp.com",
    projectId: "gen-lang-client-0641484956",
    storageBucket: "gen-lang-client-0641484956.firebasestorage.app",
    messagingSenderId: "990123663113",
    appId: "1:990123663113:android:e69743e1ac47316fc97dd8",
  };
  const FIRESTORE_DATABASE_ID = "ai-studio-android-shuttlec-cbccc3c7-a99c-484f-9d0e-055fde3b0723";

  const DAYS = [
    { id: 1, name: "Senin", short: "Sen" },
    { id: 2, name: "Selasa", short: "Sel" },
    { id: 3, name: "Rabu", short: "Rab" },
    { id: 4, name: "Kamis", short: "Kam" },
    { id: 5, name: "Jumat", short: "Jum" },
    { id: 6, name: "Sabtu", short: "Sab" },
    { id: 7, name: "Minggu", short: "Min" },
  ];

  const MONTHS_SHORT = ["Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des"];
  const MONTHS_LONG = [
    "Januari", "Februari", "Maret", "April", "Mei", "Juni",
    "Juli", "Agustus", "September", "Oktober", "November", "Desember"
  ];

  const syncListeners = new Set();
  const broadcast = typeof BroadcastChannel !== "undefined" ? new BroadcastChannel(SYNC_CHANNEL_NAME) : null;
  const cloudState = {
    initialized: false,
    user: null,
    mode: "LOCAL_REALTIME", // LOCAL_REALTIME | CLOUD_LIVE | CLOUD_ERROR
    lastSyncedAt: Date.now(),
    errorMsg: "",
    app: null,
    auth: null,
    db: null,
    firestoreApi: null,
    authApi: null,
  };

  function emitSyncEvent(reason, detail) {
    cloudState.lastSyncedAt = Date.now();
    syncListeners.forEach((cb) => {
      try {
        cb({ reason, detail, state: getSyncStatus() });
      } catch (_) {}
    });
  }

  function getSyncStatus() {
    return {
      mode: cloudState.mode,
      userEmail: cloudState.user ? cloudState.user.email : null,
      lastSyncedAt: cloudState.lastSyncedAt,
      errorMsg: cloudState.errorMsg,
    };
  }

  function formatTimeClock(ts) {
    const d = new Date(ts || Date.now());
    return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}:${String(d.getSeconds()).padStart(2, "0")}`;
  }

  function toIsoDate(dateObj) {
    const y = dateObj.getFullYear();
    const m = String(dateObj.getMonth() + 1).padStart(2, "0");
    const d = String(dateObj.getDate()).padStart(2, "0");
    return `${y}-${m}-${d}`;
  }

  function parseIsoDate(isoStr) {
    if (!isoStr || !/^\d{4}-\d{2}-\d{2}$/.test(isoStr)) return null;
    const [y, m, d] = isoStr.split("-").map(Number);
    return new Date(y, m - 1, d);
  }

  function dateToIsoDay(dateObj) {
    const jsDay = dateObj.getDay();
    return jsDay === 0 ? 7 : jsDay;
  }

  function todayIsoDay() {
    return dateToIsoDay(new Date());
  }

  function getDateForDayOfWeek(dayId) {
    const now = new Date();
    const currentIso = dateToIsoDay(now);
    const diff = Number(dayId) - currentIso;
    const target = new Date(now.getFullYear(), now.getMonth(), now.getDate() + diff);
    return toIsoDate(target);
  }

  function isoDateToDayOfWeek(isoStr) {
    const d = parseIsoDate(isoStr);
    return d ? dateToIsoDay(d) : todayIsoDay();
  }

  function formatDateShort(isoStr) {
    const d = parseIsoDate(isoStr);
    if (!d) return "";
    return `${d.getDate()} ${MONTHS_SHORT[d.getMonth()]}`;
  }

  function formatDayAndDate(dayId, isoStr) {
    const targetIso = isoStr || getDateForDayOfWeek(dayId);
    const d = parseIsoDate(targetIso);
    const label = dayName(d ? dateToIsoDay(d) : dayId);
    if (!d) return label;
    return `${label}, ${d.getDate()} ${MONTHS_SHORT[d.getMonth()]} ${d.getFullYear()}`;
  }

  function formatTodayFull() {
    const now = new Date();
    const label = dayName(dateToIsoDay(now));
    return `${label}, ${now.getDate()} ${MONTHS_LONG[now.getMonth()]} ${now.getFullYear()}`;
  }

  function dayName(dayId) {
    return (DAYS.find((d) => d.id === Number(dayId)) || DAYS[0]).name;
  }

  function createDefaultData() {
    const d1 = getDateForDayOfWeek(1);
    const d3 = getDateForDayOfWeek(3);
    return {
      editorPin: DEFAULT_EDITOR_PIN,
      updatedAt: Date.now(),
      venue: {
        name: "Lapangan Bulutangkis Manisrejo",
        phone: "082142272367",
        mapsUrl: "https://maps.app.goo.gl/xEdtA2gNiUx9pz9f9",
        openHoursText: "08:00 – 00:00",
      },
      courts: [
        { id: 1, name: "Lapangan A", rate: 15000, active: true, note: "" },
        { id: 2, name: "Lapangan B", rate: 15000, active: true, note: "" },
      ],
      bookings: [
        {
          id: 101,
          courtId: 1,
          courtName: "Lapangan A",
          dayOfWeek: 1,
          bookingDate: d1,
          startHour: 18,
          durationHours: 2,
          customerName: "PB Manisrejo",
          customerPhone: "081234567890",
          totalPrice: 30000,
          paidAmount: 30000,
          paymentStatus: "PAID",
          reminderSentCount: 0,
          notes: "Rutin Senin",
          createdAt: Date.now() - 86400000 * 2,
        },
        {
          id: 102,
          courtId: 2,
          courtName: "Lapangan B",
          dayOfWeek: 3,
          bookingDate: d3,
          startHour: 19,
          durationHours: 2,
          customerName: "Tim Badminton RT 04",
          customerPhone: "085711223344",
          totalPrice: 30000,
          paidAmount: 10000,
          paymentStatus: "DP",
          reminderSentCount: 1,
          notes: "DP Rp 10.000",
          createdAt: Date.now() - 86400000,
        },
      ],
      history: [
        {
          id: 1,
          courtId: 1,
          courtName: "Lapangan A",
          dayOfWeek: 1,
          bookingDate: d1,
          startHour: 18,
          durationHours: 2,
          customerName: "PB Manisrejo",
          totalPrice: 30000,
          status: "CONFIRMED",
          createdAt: Date.now() - 86400000 * 2,
        },
        {
          id: 2,
          courtId: 2,
          courtName: "Lapangan B",
          dayOfWeek: 3,
          bookingDate: d3,
          startHour: 19,
          durationHours: 2,
          customerName: "Tim Badminton RT 04",
          totalPrice: 30000,
          status: "CONFIRMED",
          createdAt: Date.now() - 86400000,
        },
      ],
    };
  }

  function loadStore() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) {
        const fresh = createDefaultData();
        localStorage.setItem(STORAGE_KEY, JSON.stringify(fresh));
        return fresh;
      }
      const parsed = JSON.parse(raw);
      if (!parsed.editorPin || parsed.editorPin === "1234") {
        parsed.editorPin = DEFAULT_EDITOR_PIN;
        localStorage.setItem(STORAGE_KEY, JSON.stringify(parsed));
      }
      return parsed;
    } catch (_) {
      return createDefaultData();
    }
  }

  function saveStore(data, options = {}) {
    data.updatedAt = Date.now();
    localStorage.setItem(STORAGE_KEY, JSON.stringify(data));
    if (!options.silentBroadcast && broadcast) {
      try {
        broadcast.postMessage({ type: "STORE_UPDATED", updatedAt: data.updatedAt, source: options.source || "local" });
      } catch (_) {}
    }
    if (!options.silentEmit) {
      emitSyncEvent("local_write", options.source || "save");
    }
  }

  // Real-time local cross-tab / cross-page synchronization
  if (typeof window !== "undefined") {
    if (broadcast) {
      broadcast.onmessage = (ev) => {
        if (ev && ev.data && ev.data.type === "STORE_UPDATED") {
          emitSyncEvent("cross_tab", "Perubahan jadwal otomatis disinkronkan");
        }
      };
    }
    window.addEventListener("storage", (ev) => {
      if (ev.key === STORAGE_KEY) {
        emitSyncEvent("storage_event", "Jadwal terbaru dari tab lain disinkronkan");
      }
    });
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "visible") {
        emitSyncEvent("tab_visible", "Refresh otomatis saat kembali");
      }
    });
    window.addEventListener("focus", () => {
      emitSyncEvent("window_focus", "Refresh otomatis");
    });
  }

  async function initCloudSdk() {
    if (cloudState.initialized) return true;
    if (typeof window === "undefined" || window.location.protocol === "file:") return false;
    try {
      const [appMod, authMod, fsMod] = await Promise.all([
        import("https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js"),
        import("https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js"),
        import("https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js"),
      ]);
      const app = appMod.getApps().length ? appMod.getApp() : appMod.initializeApp(FIREBASE_CONFIG);
      const auth = authMod.getAuth(app);
      const db = fsMod.getFirestore(app, FIRESTORE_DATABASE_ID);
      cloudState.app = app;
      cloudState.auth = auth;
      cloudState.db = db;
      cloudState.authApi = authMod;
      cloudState.firestoreApi = fsMod;
      cloudState.initialized = true;

      authMod.onAuthStateChanged(auth, (user) => {
        cloudState.user = user || null;
        if (user) {
          startCloudSnapshotSync();
        } else {
          cloudState.mode = "LOCAL_REALTIME";
          emitSyncEvent("cloud_signed_out");
        }
      });
      return true;
    } catch (err) {
      cloudState.errorMsg = err && err.message ? err.message : "Offline / Cloud SDK tidak tersedia";
      cloudState.mode = "LOCAL_REALTIME";
      emitSyncEvent("cloud_unavailable");
      return false;
    }
  }

  let unsubSlots = null;
  let unsubCourts = null;

  function stopCloudSnapshotSync() {
    if (typeof unsubSlots === "function") unsubSlots();
    if (typeof unsubCourts === "function") unsubCourts();
    unsubSlots = null;
    unsubCourts = null;
  }

  function startCloudSnapshotSync() {
    if (!cloudState.db || !cloudState.firestoreApi || !cloudState.user) return;
    stopCloudSnapshotSync();
    const { collection, query, where, onSnapshot } = cloudState.firestoreApi;
    cloudState.mode = "CLOUD_LIVE";
    cloudState.errorMsg = "";

    const slotsQuery = query(collection(cloudState.db, "schedule_slots"), where("venueId", "==", "manisrejo"));
    unsubSlots = onSnapshot(
      slotsQuery,
      (snapshot) => {
        const store = loadStore();
        const remoteSlots = [];
        const remoteHistory = [];
        snapshot.forEach((docSnap) => {
          const d = docSnap.data();
          const numId = docSnap.id;
          const createdMs = d.createdAt && typeof d.createdAt.toMillis === "function" ? d.createdAt.toMillis() : Date.now();
          if (d.status === "AKTIF") {
            remoteSlots.push({
              id: numId,
              slotDocId: docSnap.id,
              userId: d.userId || "",
              courtId: Number(d.courtId || 1),
              courtName: d.courtName || "Lapangan A",
              dayOfWeek: Number(d.dayOfWeek || 1),
              bookingDate: d.bookingDate || getDateForDayOfWeek(Number(d.dayOfWeek || 1)),
              startHour: Number(d.startHour || 18),
              durationHours: Number(d.durationHours || 1),
              customerName: d.displayName || "Penyewa",
              customerPhone: "",
              racketCount: Number(d.racketCount || 0),
              cockCount: Number(d.cockCount || 0),
              totalPrice: Number(d.totalPrice || 0),
              paidAmount: Number(d.paidAmount || 0),
              paymentStatus: d.paymentStatus || "UNPAID",
              reminderSentCount: Number(d.reminderSentCount || 0),
              notes: d.notes || "",
              createdAt: createdMs,
            });
          }
          remoteHistory.push({
            id: docSnap.id,
            courtId: Number(d.courtId || 1),
            courtName: d.courtName || "Lapangan A",
            dayOfWeek: Number(d.dayOfWeek || 1),
            bookingDate: d.bookingDate || getDateForDayOfWeek(Number(d.dayOfWeek || 1)),
            startHour: Number(d.startHour || 18),
            durationHours: Number(d.durationHours || 1),
            customerName: d.displayName || "Penyewa",
            totalPrice: Number(d.totalPrice || 0),
            notes: d.notes || "",
            status: d.status === "DIBATALKAN" ? "CANCELLED" : "CONFIRMED",
            createdAt: createdMs,
          });
        });

        store.bookings = remoteSlots;
        remoteHistory.sort((a, b) => b.createdAt - a.createdAt);
        store.history = remoteHistory;
        cloudState.mode = "CLOUD_LIVE";
        saveStore(store, { source: "cloud_snapshot", silentEmit: false });
      },
      (err) => {
        cloudState.mode = "LOCAL_REALTIME";
        cloudState.errorMsg = err && err.message ? err.message : "Sinkron cloud terhenti";
        emitSyncEvent("cloud_error", cloudState.errorMsg);
      }
    );

    const courtsQuery = query(collection(cloudState.db, "courts"), where("venueId", "==", "manisrejo"));
    unsubCourts = onSnapshot(courtsQuery, (snapshot) => {
      if (snapshot.empty) return;
      const store = loadStore();
      const syncedCourts = [];
      snapshot.forEach((docSnap) => {
        const d = docSnap.data();
        syncedCourts.push({
          id: Number(d.courtNumber || 1),
          docId: docSnap.id,
          name: d.name || `Lapangan ${d.courtNumber || 1}`,
          rate: Number(d.hourlyRateRegular || 15000),
          active: d.active !== false,
          note: d.surfaceType || "",
        });
      });
      syncedCourts.sort((a, b) => a.id - b.id);
      if (syncedCourts.length > 0) {
        store.courts = syncedCourts;
        saveStore(store, { source: "cloud_courts" });
      }
    });
  }

  async function pushBookingsToCloud(createdBookings) {
    if (!cloudState.user || !cloudState.db || !cloudState.firestoreApi) return;
    const { doc, writeBatch, serverTimestamp } = cloudState.firestoreApi;
    const uid = cloudState.user.uid;
    const batch = writeBatch(cloudState.db);
    createdBookings.forEach((b, idx) => {
      const slotId = `web_${b.id}_${idx}`;
      b.slotDocId = slotId;
      b.userId = uid;
      batch.set(doc(cloudState.db, "schedule_slots", slotId), {
        venueId: "manisrejo",
        userId: uid,
        courtId: Number(b.courtId),
        courtName: String(b.courtName).slice(0, 50),
        dayOfWeek: Number(b.dayOfWeek),
        bookingDate: String(b.bookingDate || "").slice(0, 20),
        startHour: Number(b.startHour),
        durationHours: Number(b.durationHours),
        displayName: String(b.customerName).slice(0, 60),
        totalPrice: Number(b.totalPrice),
        paidAmount: Number(b.paidAmount || 0),
        paymentStatus: b.paymentStatus || "UNPAID",
        racketCount: Number(b.racketCount || 0),
        cockCount: Number(b.cockCount || 0),
        reminderEnabled: b.paymentStatus !== "PAID",
        reminderSentCount: 0,
        notes: String(b.notes || "").slice(0, 250),
        status: "AKTIF",
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
      batch.set(doc(cloudState.db, "users", uid, "bookings", slotId), {
        userId: uid,
        slotId,
        customerName: String(b.customerName).slice(0, 60),
        customerPhone: String(b.customerPhone || "").slice(0, 30),
        notes: String(b.notes || "").slice(0, 250),
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      });
    });
    await batch.commit();
  }

  async function updateCloudSlot(booking, patchFields) {
    if (!cloudState.user || !cloudState.db || !cloudState.firestoreApi) return;
    const docId = booking.slotDocId || (typeof booking.id === "string" ? booking.id : null);
    if (!docId) return;
    const { doc, updateDoc, serverTimestamp } = cloudState.firestoreApi;
    try {
      await updateDoc(doc(cloudState.db, "schedule_slots", docId), {
        ...patchFields,
        updatedAt: serverTimestamp(),
      });
    } catch (err) {
      cloudState.errorMsg = err && err.message ? err.message : "Gagal update ke cloud";
      emitSyncEvent("cloud_error", cloudState.errorMsg);
    }
  }

  async function connectCloudSync() {
    const ok = await initCloudSdk();
    if (!ok || !cloudState.authApi || !cloudState.auth) {
      showToast("Sinkron lokal antar-tab aktif. Buka lewat HTTPS/hosting untuk Sinkron Cloud Google.");
      return false;
    }
    try {
      const provider = new cloudState.authApi.GoogleAuthProvider();
      await cloudState.authApi.signInWithPopup(cloudState.auth, provider);
      showToast("Sinkron Cloud Google aktif! Jadwal terhubung real-time dengan Android.");
      return true;
    } catch (err) {
      cloudState.errorMsg = err && err.message ? err.message : "Login Google dibatalkan";
      emitSyncEvent("cloud_error", cloudState.errorMsg);
      showToast("Sinkron lokal tetap aktif (" + cloudState.errorMsg + ")");
      return false;
    }
  }

  async function disconnectCloudSync() {
    stopCloudSnapshotSync();
    if (cloudState.authApi && cloudState.auth) {
      await cloudState.authApi.signOut(cloudState.auth);
    }
    cloudState.user = null;
    cloudState.mode = "LOCAL_REALTIME";
    emitSyncEvent("cloud_signed_out");
    showToast("Keluar dari Sinkron Cloud. Sinkron lokal tetap aktif.");
  }

  function formatHour(hour) {
    const h = Number(hour);
    if (h === 24 || h === 0) return "00:00";
    return String(h).padStart(2, "0") + ":00";
  }

  function formatRange(startHour, durationHours) {
    return `${formatHour(startHour)} – ${formatHour(startHour + durationHours)}`;
  }

  function parseManualHour(raw, allowMidnight) {
    if (!raw) return null;
    const cleaned = String(raw).trim().replace(".", ":");
    const parts = cleaned.split(":");
    if (parts.length > 2) return null;
    if (!/^\d+$/.test(parts[0])) return null;
    if (parts.length === 2 && parts[1] !== "00") return null;
    const parsed = parseInt(parts[0], 10);
    if ((parsed === 0 || parsed === 24) && allowMidnight) return 24;
    return parsed;
  }

  function formatRupiah(amount) {
    return "Rp " + Number(amount || 0).toLocaleString("id-ID");
  }

  function buildWhatsAppUrl(phone, message) {
    const normalized = String(phone || "082142272367").replace(/[^0-9]/g, "");
    const intl = normalized.startsWith("0") ? "62" + normalized.slice(1) : normalized;
    return `https://wa.me/${intl}?text=${encodeURIComponent(message)}`;
  }

  function showToast(msg) {
    const el = document.getElementById("toast");
    if (!el) return;
    el.textContent = msg;
    el.classList.remove("hidden");
    clearTimeout(window.__toastTimer);
    window.__toastTimer = setTimeout(() => el.classList.add("hidden"), 3400);
  }

  function formatAvailableRangesSummary(courtBookings) {
    const sorted = [...courtBookings].sort((a, b) => a.startHour - b.startHour);
    const ranges = [];
    let cursor = OPEN_HOUR;
    for (const b of sorted) {
      if (b.startHour > cursor) {
        ranges.push(`${formatHour(cursor)}-${formatHour(b.startHour)}`);
      }
      cursor = Math.max(cursor, b.startHour + b.durationHours);
    }
    if (cursor < CLOSE_HOUR) {
      ranges.push(`${formatHour(cursor)}-${formatHour(CLOSE_HOUR)}`);
    }
    return ranges.length > 0 ? ranges.join(", ") : "Penuh sepanjang hari";
  }

  function calculateExtrasPrice(racketCount, cockCount) {
    const r = Math.max(0, Math.floor(Number(racketCount) || 0));
    const c = Math.max(0, Math.floor(Number(cockCount) || 0));
    return r * RACKET_PRICE + c * COCK_PRICE;
  }

  function formatExtrasSummary(racketCount, cockCount) {
    const r = Math.max(0, Math.floor(Number(racketCount) || 0));
    const c = Math.max(0, Math.floor(Number(cockCount) || 0));
    const parts = [];
    if (r > 0) parts.push(`Sewa Raket ${r}x (${formatRupiah(r * RACKET_PRICE)})`);
    if (c > 0) parts.push(`Beli Cock ${c}x (${formatRupiah(c * COCK_PRICE)})`);
    return parts.join(" • ");
  }

  function createCourtTimeBookings({
    store,
    courtEntries,
    dayOfWeek,
    bookingDate,
    customerName,
    customerPhone,
    paymentStatus,
    paidAmount,
    racketCount = 0,
    cockCount = 0,
    notes,
  }) {
    if (!customerName || !customerName.trim()) {
      return { error: "Nama penyewa / klub wajib diisi." };
    }
    if (!Array.isArray(courtEntries) || courtEntries.length === 0) {
      return { error: "Pilih minimal 1 lapangan." };
    }

    const effectiveDate = bookingDate || getDateForDayOfWeek(dayOfWeek);
    const effectiveDay = bookingDate ? isoDateToDayOfWeek(bookingDate) : dayOfWeek;
    const resolvedEntries = [];

    for (const entry of courtEntries) {
      const court = store.courts.find((c) => c.id === entry.courtId && c.active);
      if (!court) {
        return { error: "Lapangan yang dipilih sedang tidak aktif." };
      }
      if (entry.startHour === null || entry.startHour < OPEN_HOUR || entry.startHour >= CLOSE_HOUR) {
        return { error: `Jam mulai ${court.name} harus antara 08:00 hingga 23:00 (kelipatan jam penuh).` };
      }
      if (entry.endHour === null || entry.endHour <= entry.startHour || entry.endHour > CLOSE_HOUR) {
        return { error: `Jam selesai ${court.name} harus lebih besar dari jam mulai dan maksimal 00:00.` };
      }
      const conflict = store.bookings.find(
        (b) =>
          b.courtId === court.id &&
          (b.bookingDate ? b.bookingDate === effectiveDate : b.dayOfWeek === effectiveDay) &&
          entry.startHour < b.startHour + b.durationHours &&
          b.startHour < entry.endHour
      );
      if (conflict) {
        return {
          error: `Bentrok di ${court.name}: ${formatRange(conflict.startHour, conflict.durationHours)} sudah dipesan (${conflict.customerName}).`,
        };
      }
      resolvedEntries.push({
        court,
        startHour: entry.startHour,
        endHour: entry.endHour,
        duration: entry.endHour - entry.startHour,
      });
    }

    const createdBookings = [];
    const now = Date.now();
    let remainingDp = Number(paidAmount || 0);
    const extrasPrice = calculateExtrasPrice(racketCount, cockCount);
    const extrasLabel = formatExtrasSummary(racketCount, cockCount);
    const combinedNotes = [extrasLabel, (notes || "").trim()].filter(Boolean).join(" | ");

    resolvedEntries.forEach((item, idx) => {
      const courtPrice = item.court.rate * item.duration;
      const totalPrice = courtPrice + (idx === 0 ? extrasPrice : 0);
      let finalPaid = 0;
      if (paymentStatus === "PAID") {
        finalPaid = totalPrice;
      } else if (paymentStatus === "DP") {
        finalPaid = Math.min(Math.max(remainingDp, 0), totalPrice);
        remainingDp = Math.max(remainingDp - finalPaid, 0);
      }

      const booking = {
        id: now + idx,
        courtId: item.court.id,
        courtName: item.court.name,
        dayOfWeek: effectiveDay,
        bookingDate: effectiveDate,
        startHour: item.startHour,
        durationHours: item.duration,
        customerName: customerName.trim(),
        customerPhone: (customerPhone || "").trim(),
        racketCount: idx === 0 ? Math.max(0, Math.floor(Number(racketCount) || 0)) : 0,
        cockCount: idx === 0 ? Math.max(0, Math.floor(Number(cockCount) || 0)) : 0,
        totalPrice,
        paidAmount: finalPaid,
        paymentStatus: paymentStatus || "UNPAID",
        reminderSentCount: 0,
        notes: idx === 0 ? combinedNotes : (notes || "").trim(),
        createdAt: now,
      };
      store.bookings.push(booking);
      store.history.unshift({
        id: now + idx + 1000,
        courtId: item.court.id,
        courtName: item.court.name,
        dayOfWeek: effectiveDay,
        bookingDate: effectiveDate,
        startHour: item.startHour,
        durationHours: item.duration,
        customerName: booking.customerName,
        totalPrice,
        notes: booking.notes,
        status: "CONFIRMED",
        createdAt: now,
      });
      createdBookings.push(booking);
    });

    saveStore(store, { source: "booking_created" });
    if (cloudState.user) {
      pushBookingsToCloud(createdBookings).catch(() => {});
    }
    return { bookings: createdBookings };
  }

  function createBooking({
    store,
    courtIds,
    dayOfWeek,
    bookingDate,
    startHour,
    endHour,
    customerName,
    customerPhone,
    paymentStatus,
    paidAmount,
    notes,
  }) {
    const courtEntries = (courtIds || []).map((id) => ({
      courtId: id,
      startHour,
      endHour,
    }));
    return createCourtTimeBookings({
      store,
      courtEntries,
      dayOfWeek,
      bookingDate,
      customerName,
      customerPhone,
      paymentStatus,
      paidAmount,
      notes,
    });
  }

  // Initialize passive cloud auth listener in background if served over HTTP/HTTPS
  initCloudSdk().catch(() => {});

  window.ManisrejoPortal = {
    OPEN_HOUR,
    CLOSE_HOUR,
    RACKET_PRICE,
    COCK_PRICE,
    DEFAULT_EDITOR_PIN,
    DAYS,
    loadStore,
    saveStore,
    resetStore() {
      const fresh = createDefaultData();
      saveStore(fresh, { source: "reset" });
      return fresh;
    },
    onSync(cb) {
      if (typeof cb === "function") syncListeners.add(cb);
      return () => syncListeners.delete(cb);
    },
    getSyncStatus,
    formatTimeClock,
    connectCloudSync,
    disconnectCloudSync,
    updateCloudSlot,
    formatHour,
    formatRange,
    parseManualHour,
    formatRupiah,
    calculateExtrasPrice,
    formatExtrasSummary,
    dayName,
    todayIsoDay,
    getDateForDayOfWeek,
    isoDateToDayOfWeek,
    formatDateShort,
    formatDayAndDate,
    formatTodayFull,
    buildWhatsAppUrl,
    showToast,
    formatAvailableRangesSummary,
    createBooking,
    createCourtTimeBookings,
  };
})();
