const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-shuttlec-cbccc3c7-a99c-484f-9d0e-055fde3b0723";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const ADMIN_UID = "admin_789";
const ADMIN_EMAIL = "Riskykharis00@gmail.com";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function dbFor(context) {
  return context.firestore({ databaseId: DATABASE_ID });
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Pengguna belum login ditolak membaca atau menulis jadwal dan data pribadi", async () => {
  const unauthDb = dbFor(testEnv.unauthenticatedContext());
  await assertFails(
    unauthDb.collection("schedule_slots").where("venueId", "==", "manisrejo").get()
  );
  await assertFails(
    unauthDb.collection("courts").where("venueId", "==", "manisrejo").get()
  );
  await assertFails(
    unauthDb.collection("schedule_slots").doc("slot_1").set({
      venueId: "manisrejo",
      userId: ALICE_UID,
      courtId: 1,
      courtName: "Lapangan A",
      dayOfWeek: 1,
      startHour: 18,
      durationHours: 2,
      displayName: "PB Manisrejo",
      totalPrice: 30000,
      paidAmount: 0,
      paymentStatus: "UNPAID",
      status: "AKTIF",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("Pengguna terautentikasi bisa membuat jadwal non-PII + booking pribadi atomik dan membatalkan miliknya", async () => {
  const aliceCtx = testEnv.authenticatedContext(ALICE_UID, {
    email: "alice@example.com",
    email_verified: true,
  });
  const aliceDb = dbFor(aliceCtx);
  const ts = new Date(Date.now() - 2000);

  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("schedule_slots").doc("slot_alice_1"), {
    venueId: "manisrejo",
    userId: ALICE_UID,
    courtId: 1,
    courtName: "Lapangan A",
    dayOfWeek: 2,
    bookingDate: "2026-11-18",
    startHour: 18,
    durationHours: 2,
    displayName: "PB Manisrejo",
    totalPrice: 52000,
    paidAmount: 0,
    paymentStatus: "UNPAID",
    racketCount: 1,
    cockCount: 1,
    reminderEnabled: true,
    reminderSentCount: 0,
    notes: "Sewa Raket 1x (Rp 10.000) • Beli Cock 1x (Rp 12.000)",
    status: "AKTIF",
    createdAt: ts,
    updatedAt: ts,
  });
  batch.set(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookings").doc("slot_alice_1"),
    {
      userId: ALICE_UID,
      slotId: "slot_alice_1",
      customerName: "PB Manisrejo",
      customerPhone: "081234567890",
      notes: "Sewa Raket 1x (Rp 10.000) • Beli Cock 1x (Rp 12.000)",
      createdAt: ts,
      updatedAt: ts,
    }
  );
  await assertSucceeds(batch.commit());

  // Query dengan filter venueId == "manisrejo" diizinkan untuk sinkronisasi otomatis real-time
  await assertSucceeds(
    aliceDb.collection("schedule_slots").where("venueId", "==", "manisrejo").get()
  );

  // Query tanpa filter venueId wajib ditolak
  await assertFails(aliceDb.collection("schedule_slots").get());

  // Pembuat bisa membatalkan jadwal AKTIF miliknya sendiri
  await assertSucceeds(
    aliceDb.collection("schedule_slots").doc("slot_alice_1").update({
      status: "DIBATALKAN",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Setelah DIBATALKAN (terminal state), non-admin tidak bisa mengubah lagi
  await assertFails(
    aliceDb.collection("schedule_slots").doc("slot_alice_1").update({
      status: "AKTIF",
      updatedAt: new Date(Date.now() - 300),
    })
  );
});

test("Isolasi PII dan proteksi anti-spoofing: Bob tidak bisa membaca telepon Alice atau mengubah jadwal Alice", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = dbFor(context);
    await db.collection("schedule_slots").doc("slot_alice_private").set({
      venueId: "manisrejo",
      userId: ALICE_UID,
      courtId: 1,
      courtName: "Lapangan A",
      dayOfWeek: 3,
      startHour: 19,
      durationHours: 2,
      displayName: "Tim A",
      totalPrice: 30000,
      paidAmount: 0,
      paymentStatus: "UNPAID",
      status: "AKTIF",
      createdAt: ts,
      updatedAt: ts,
    });
    await db.collection("users").doc(ALICE_UID).collection("bookings").doc("slot_alice_private").set({
      userId: ALICE_UID,
      slotId: "slot_alice_private",
      customerName: "Tim A",
      customerPhone: "081299998888",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  const bobDb = dbFor(
    testEnv.authenticatedContext(BOB_UID, {
      email: "bob@example.com",
      email_verified: true,
    })
  );

  // Bob bisa melihat slot jadwal terisi agar sinkron dan tidak bentrok, tetapi TIDAK bisa membaca telepon PII Alice
  await assertSucceeds(
    bobDb.collection("schedule_slots").where("venueId", "==", "manisrejo").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("bookings").doc("slot_alice_private").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("bookings").get()
  );

  // Bob tidak bisa membatalkan atau menandai lunas booking Alice
  await assertFails(
    bobDb.collection("schedule_slots").doc("slot_alice_private").update({
      paymentStatus: "PAID",
      paidAmount: 30000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertFails(
    bobDb.collection("schedule_slots").doc("slot_alice_private").update({
      status: "DIBATALKAN",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Tolak field "id" (@DocumentId poison) dan ghost field
  await assertFails(
    bobDb.collection("schedule_slots").doc("slot_bad_id").set({
      id: "slot_bad_id",
      venueId: "manisrejo",
      userId: BOB_UID,
      courtId: 2,
      courtName: "Lapangan B",
      dayOfWeek: 4,
      startHour: 18,
      durationHours: 1,
      displayName: "Bob",
      totalPrice: 15000,
      paidAmount: 0,
      paymentStatus: "UNPAID",
      status: "AKTIF",
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Admin terverifikasi bisa menandai Lunas dan mengelola Lapangan; email belum terverifikasi ditolak", async () => {
  const ts = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await dbFor(context).collection("schedule_slots").doc("slot_pending").set({
      venueId: "manisrejo",
      userId: ALICE_UID,
      courtId: 2,
      courtName: "Lapangan B",
      dayOfWeek: 5,
      startHour: 20,
      durationHours: 2,
      displayName: "Klub RT 04",
      totalPrice: 30000,
      paidAmount: 10000,
      paymentStatus: "DP",
      status: "AKTIF",
      createdAt: ts,
      updatedAt: ts,
    });
  });

  // Email admin palsu tanpa email_verified == true ditolak
  const spoofAdminDb = dbFor(
    testEnv.authenticatedContext("spoof_uid", {
      email: ADMIN_EMAIL,
      email_verified: false,
    })
  );
  await assertFails(
    spoofAdminDb.collection("schedule_slots").doc("slot_pending").update({
      paymentStatus: "PAID",
      paidAmount: 30000,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin terverifikasi diizinkan mengelola pembayaran & lapangan
  const realAdminDb = dbFor(
    testEnv.authenticatedContext(ADMIN_UID, {
      email: ADMIN_EMAIL,
      email_verified: true,
    })
  );
  await assertSucceeds(
    realAdminDb.collection("schedule_slots").doc("slot_pending").update({
      paymentStatus: "PAID",
      paidAmount: 30000,
      reminderEnabled: false,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(
    realAdminDb.collection("courts").doc("court_1").set({
      venueId: "manisrejo",
      courtNumber: 1,
      name: "Lapangan A",
      surfaceType: "",
      hourlyRateRegular: 15000,
      hourlyRatePrime: 15000,
      hourlyRateWeekend: 15000,
      active: true,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Admin dapat sinkronkan database kontak pemesan via collectionGroup("bookings"), sedangkan user biasa ditolak
  const aliceDbForCg = dbFor(
    testEnv.authenticatedContext(ALICE_UID, {
      email: "alice@example.com",
      email_verified: true,
    })
  );
  await assertSucceeds(
    aliceDbForCg.collection("users").doc(ALICE_UID).collection("bookings").doc("slot_alice_cg").set({
      userId: ALICE_UID,
      slotId: "slot_pending",
      customerName: "Klub RT 04",
      customerPhone: "085711223344",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(realAdminDb.collectionGroup("bookings").get());
  await assertFails(
    dbFor(
      testEnv.authenticatedContext(BOB_UID, {
        email: "bob@example.com",
        email_verified: true,
      })
    ).collectionGroup("bookings").get()
  );
});
