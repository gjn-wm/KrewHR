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
const WS_ID = "ws_alice_123";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

async function seedWorkspace(db, uid = ALICE_UID) {
  const now = pastDate(5000);
  await db.collection("workspaces").doc(WS_ID).set({
    ownerUid: uid,
    memberUids: [uid],
    companyName: "KrewHR Labs",
    seatLimit: 50,
    activeSeatCount: 1,
    userRole: "HR_ADMIN",
    biometricBadgeEnabled: true,
    calendarSyncEnabled: true,
    accountingFormat: "CSV",
    createdAt: now,
    updatedAt: now,
  });
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

test("Unauthenticated users cannot read or write PII profiles or workspaces", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("workspaces").doc(WS_ID).get());
  await assertFails(unauthDb.collection("workspaces").doc(WS_ID).collection("employees").get());
});

test("PII Isolation: owner can manage private profile; another user is denied", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(3000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      fullName: "Alice Vance",
      workEmail: "alice@krewhr.io",
      phone: "+1-555-0101",
      taxFilingStatus: "SINGLE",
      mfaLockEnabled: true,
      activeWorkspaceId: WS_ID,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      phone: "+1-555-9999",
      updatedAt: pastDate(500),
    })
  );
  // Reject Ghost field & future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      ghostField: true,
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      phone: "+1-555-0102",
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Workspace & Employee CRUD: Kotlin payloads succeed, unfiltered list & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate(4000);

  // Parent gate check: creating employee before workspace exists must fail
  await assertFails(
    aliceDb.collection("workspaces").doc(WS_ID).collection("employees").doc("emp_01").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      employeeCode: "KRW-001",
      fullName: "Alice Vance",
      jobTitle: "VP People",
      department: "People",
      role: "HR_ADMIN",
      workEmail: "alice@krewhr.io",
      workMode: "HYBRID",
      shiftWindow: "09:00 - 18:00",
      status: "ACTIVE",
      baseMonthlySalary: 9500,
      annualLeaveBalance: 18,
      sickLeaveBalance: 10,
      documents: ["Offer Letter"],
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(seedWorkspace(aliceDb, ALICE_UID));

  // Valid employee create (NO 'id' field because Kotlin uses @DocumentId)
  await assertSucceeds(
    aliceDb.collection("workspaces").doc(WS_ID).collection("employees").doc("emp_01").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      employeeCode: "KRW-001",
      fullName: "Alice Vance",
      jobTitle: "VP People",
      department: "People",
      role: "HR_ADMIN",
      workEmail: "alice@krewhr.io",
      workMode: "HYBRID",
      shiftWindow: "09:00 - 18:00",
      status: "ACTIVE",
      baseMonthlySalary: 9500,
      annualLeaveBalance: 18,
      sickLeaveBalance: 10,
      documents: ["Offer Letter"],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Reject payload containing redundant @DocumentId 'id' key
  await assertFails(
    aliceDb.collection("workspaces").doc(WS_ID).collection("employees").doc("emp_02").set({
      id: "emp_02",
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      employeeCode: "KRW-002",
      fullName: "Marcus Chen",
      jobTitle: "Staff Engineer",
      department: "Engineering",
      role: "MANAGER",
      workEmail: "marcus@krewhr.io",
      workMode: "REMOTE",
      shiftWindow: "09:00 - 18:00",
      status: "ACTIVE",
      baseMonthlySalary: 11200,
      annualLeaveBalance: 15,
      sickLeaveBalance: 10,
      documents: ["Offer Letter", "NDA"],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Filtered Kotlin query succeeds; unfiltered query fails
  await assertSucceeds(
    aliceDb.collection("workspaces").doc(WS_ID).collection("employees")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    aliceDb.collection("workspaces").doc(WS_ID).collection("employees").get()
  );
  await assertFails(
    bobDb.collection("workspaces").doc(WS_ID).collection("employees").doc("emp_01").get()
  );
  await assertFails(
    bobDb.collection("workspaces").doc(WS_ID).collection("employees")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
});

test("Leave, Attendance, Payroll & ServiceItem: terminal state locking and partial updates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await seedWorkspace(aliceDb, ALICE_UID);
  const now = pastDate(5000);

  // 1. Attendance check-in then check-out (cannot check out twice)
  const attRef = aliceDb.collection("workspaces").doc(WS_ID).collection("attendance").doc("att_1");
  await assertSucceeds(
    attRef.set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      employeeName: "Alice Vance",
      department: "People",
      dateLabel: "2026-11-17",
      checkInTime: "09:02",
      verificationMode: "GPS",
      locationLabel: "HQ Geofence Verified",
      latitude: 37.7749,
      longitude: -122.4194,
      selfieCaptured: false,
      shiftWindow: "09:00 - 18:00",
      hoursWorked: 0,
      status: "CHECKED_IN",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    attRef.update({
      checkOutTime: "18:05",
      hoursWorked: 9.05,
      status: "CHECKED_OUT",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    attRef.update({
      checkOutTime: "19:00",
      hoursWorked: 10.0,
      status: "CHECKED_OUT",
      updatedAt: pastDate(200),
    })
  );

  // 2. Leave Request approval locks terminal state
  const leaveRef = aliceDb.collection("workspaces").doc(WS_ID).collection("leave_requests").doc("lv_1");
  await assertSucceeds(
    leaveRef.set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      employeeName: "Marcus Chen",
      department: "Engineering",
      leaveType: "ANNUAL",
      startDate: "2026-11-24",
      endDate: "2026-11-26",
      daysCount: 3,
      reason: "Family travel",
      status: "PENDING",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    leaveRef.update({
      status: "APPROVED",
      reviewedBy: "Alice Vance",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    leaveRef.update({
      status: "REJECTED",
      reviewedBy: "Alice Vance",
      updatedAt: pastDate(200),
    })
  );

  // 3. Payroll PAID state is immutable
  const payRef = aliceDb.collection("workspaces").doc(WS_ID).collection("payroll_runs").doc("pay_1");
  await assertSucceeds(
    payRef.set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      employeeName: "Alice Vance",
      employeeCode: "KRW-001",
      department: "People",
      periodLabel: "Nov 2026",
      basePay: 9500,
      overtimePay: 0,
      taxDeduction: 1900,
      benefitsDeduction: 350,
      netPay: 7250,
      daysWorked: 20,
      status: "SCHEDULED",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    payRef.update({
      status: "PAID",
      updatedAt: pastDate(400),
    })
  );
  await assertFails(
    payRef.update({
      netPay: 99999,
      updatedAt: pastDate(200),
    })
  );
});

test("Onboarding, Channel Posts, Performance & Notifications: valid writes and queries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await seedWorkspace(aliceDb, ALICE_UID);
  const now = pastDate(4000);

  await assertSucceeds(
    aliceDb.collection("workspaces").doc(WS_ID).collection("onboarding_tasks").doc("task_1").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      employeeName: "Elena Rostova",
      flowType: "ONBOARDING",
      title: "Acknowledge Employee Security Policy",
      category: "POLICY",
      dueDate: "2026-11-20",
      completed: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("workspaces").doc(WS_ID).collection("channel_posts").doc("post_1").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      channelId: "all-hands",
      authorName: "Alice Vance",
      authorRole: "VP People",
      postType: "ANNOUNCEMENT",
      body: "Q4 Open Enrollment closes this Friday at 5 PM.",
      ackCount: 12,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("workspaces").doc(WS_ID).collection("performance_items").doc("perf_1").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      itemKind: "TRAINING",
      title: "2026 Security & Privacy Compliance",
      assigneeName: "All 50 Employees",
      subtitle: "Mandatory annual module",
      progressPercent: 80,
      rating: 5,
      status: "ON_TRACK",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("workspaces").doc(WS_ID).collection("notifications").doc("n_1").set({
      ownerUid: ALICE_UID,
      memberUids: [ALICE_UID],
      title: "Leave Request Pending",
      message: "Marcus Chen requested 3 days of Annual Leave.",
      category: "APPROVAL",
      unread: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("workspaces").doc(WS_ID).collection("channel_posts")
      .where("memberUids", "array-contains", ALICE_UID)
      .get()
  );
});
