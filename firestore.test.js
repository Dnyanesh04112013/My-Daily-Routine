const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

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

test("Unauthenticated user: denied all access to profiles, activities, and habits", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("activities").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("habits").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("activities").doc("act_1").set({
      userId: ALICE_UID,
      dateKey: "2026-11-22",
      title: "Morning Run",
      description: "",
      category: "HEALTH",
      startMinute: 420,
      endMinute: 450,
      sortOrder: 0,
      completed: false,
      flexible: true,
      reminderEnabled: false,
      reminderMinutesBefore: 10,
      status: "SCHEDULED",
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("UserProfile: owner can create, read, and update; cannot list users or spoof another user", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      themeMode: "SYSTEM",
      morningStartMinute: 420,
      dayEndMinute: 1320,
      defaultBufferMinutes: 10,
      notificationsEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeMode: "DARK",
      defaultBufferMinutes: 15,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field or immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Activities: owner CRUD, filtered queries, partial updates, and security bounds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const actRef = aliceDb.collection("users").doc(ALICE_UID).collection("activities").doc("act_1");
  await assertSucceeds(
    actRef.set({
      userId: ALICE_UID,
      dateKey: "2026-11-22",
      title: "Deep Work Sprint",
      description: "Architecture design",
      category: "FOCUS",
      startMinute: 540,
      endMinute: 630,
      sortOrder: 0,
      completed: false,
      flexible: true,
      reminderEnabled: true,
      reminderMinutesBefore: 10,
      status: "SCHEDULED",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Queries matching Kotlin repository
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activities").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("activities")
      .where("dateKey", "==", "2026-11-22").get()
  );

  // Partial update: mark completed / reschedule
  await assertSucceeds(
    actRef.update({
      completed: true,
      status: "COMPLETED",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject invalid time range (endMinute <= startMinute)
  await assertFails(
    actRef.update({
      startMinute: 700,
      endMinute: 650,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject `@DocumentId` `"id"` key in write payload
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("activities").doc("act_2").set({
      id: "act_2",
      userId: ALICE_UID,
      dateKey: "2026-11-22",
      title: "Invalid ID field",
      description: "",
      category: "FOCUS",
      startMinute: 600,
      endMinute: 630,
      sortOrder: 1,
      completed: false,
      flexible: true,
      reminderEnabled: false,
      reminderMinutesBefore: 10,
      status: "SCHEDULED",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("activities").doc("act_3").set({
      userId: ALICE_UID,
      dateKey: "2026-11-22",
      title: "Future time",
      description: "",
      category: "FOCUS",
      startMinute: 600,
      endMinute: 630,
      sortOrder: 1,
      completed: false,
      flexible: true,
      reminderEnabled: false,
      reminderMinutesBefore: 10,
      status: "SCHEDULED",
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Cross-user isolation
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("activities").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("activities").doc("act_1").delete()
  );
  await assertSucceeds(actRef.delete());
});

test("Habits: owner CRUD, completedDates updates, and cross-user isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  const habitRef = aliceDb.collection("users").doc(ALICE_UID).collection("habits").doc("hab_1");
  await assertSucceeds(
    habitRef.set({
      userId: ALICE_UID,
      title: "Drink 2L Water",
      category: "HEALTH",
      targetDaysPerWeek: 7,
      reminderTimeMinute: 540,
      completedDates: ["2026-11-21"],
      createdDateKey: "2026-11-20",
      createdAt: created,
      updatedAt: created,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("habits").get()
  );

  await assertSucceeds(
    habitRef.update({
      completedDates: ["2026-11-21", "2026-11-22"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject immutable createdDateKey change
  await assertFails(
    habitRef.update({
      createdDateKey: "2026-11-01",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("habits").get()
  );
  await assertSucceeds(habitRef.delete());
});
