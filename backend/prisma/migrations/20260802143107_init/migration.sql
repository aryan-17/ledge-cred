-- CreateTable
CREATE TABLE "User" (
    "uid" TEXT NOT NULL,
    "fcmToken" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "User_pkey" PRIMARY KEY ("uid")
);

-- CreateTable
CREATE TABLE "Transaction" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "amountPaise" BIGINT NOT NULL,
    "type" TEXT NOT NULL,
    "cardLast4" TEXT,
    "bank" TEXT NOT NULL,
    "txnTime" TIMESTAMP(3) NOT NULL,
    "dedupeHash" TEXT NOT NULL,
    "matchedSettleEventId" TEXT,
    "suggestedType" TEXT,
    "suggestedConfidence" DOUBLE PRECISION,
    "reviewed" BOOLEAN NOT NULL DEFAULT false,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "Transaction_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "SettleEvent" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "parentRef" TEXT NOT NULL,
    "suffix" TEXT,
    "status" TEXT NOT NULL,
    "requestedAmountPaise" BIGINT NOT NULL,
    "pendingSnapshotPaise" BIGINT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL,
    "clearedAt" TIMESTAMP(3),
    "clearedAmountPaise" BIGINT,
    "updatedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "deletedAt" TIMESTAMP(3),

    CONSTRAINT "SettleEvent_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "Transaction_userId_updatedAt_idx" ON "Transaction"("userId", "updatedAt");

-- CreateIndex
CREATE INDEX "SettleEvent_userId_updatedAt_idx" ON "SettleEvent"("userId", "updatedAt");

-- AddForeignKey
ALTER TABLE "Transaction" ADD CONSTRAINT "Transaction_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("uid") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SettleEvent" ADD CONSTRAINT "SettleEvent_userId_fkey" FOREIGN KEY ("userId") REFERENCES "User"("uid") ON DELETE RESTRICT ON UPDATE CASCADE;
