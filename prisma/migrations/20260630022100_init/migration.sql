-- CreateSchema
CREATE SCHEMA IF NOT EXISTS "public";

-- CreateEnum
CREATE TYPE "SlugAliasStatus" AS ENUM ('ACTIVE', 'INACTIVE', 'CONFLICTED');

-- CreateEnum
CREATE TYPE "RefreshTargetKind" AS ENUM ('SETTINGS', 'PAGE');

-- CreateEnum
CREATE TYPE "SyncRunStatus" AS ENUM ('SUCCESS', 'FAILED', 'SKIPPED');

-- CreateTable
CREATE TABLE "SiteSettings" (
    "id" TEXT NOT NULL,
    "settingsDatabaseId" TEXT NOT NULL,
    "rootPageId" TEXT NOT NULL,
    "headerPageId" TEXT,
    "footerPageId" TEXT,
    "headJson" JSONB NOT NULL,
    "lastSyncedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "SiteSettings_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "NotionPage" (
    "pageId" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "notionUrl" TEXT NOT NULL,
    "publicUrl" TEXT,
    "lastEditedTime" TIMESTAMP(3),
    "lastSyncedAt" TIMESTAMP(3),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "NotionPage_pkey" PRIMARY KEY ("pageId")
);

-- CreateTable
CREATE TABLE "PageRoute" (
    "pageId" TEXT NOT NULL,
    "canonicalSlug" TEXT NOT NULL,
    "isActive" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "PageRoute_pkey" PRIMARY KEY ("pageId")
);

-- CreateTable
CREATE TABLE "SlugAlias" (
    "id" TEXT NOT NULL,
    "pageId" TEXT NOT NULL,
    "slug" TEXT NOT NULL,
    "status" "SlugAliasStatus" NOT NULL DEFAULT 'ACTIVE',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "SlugAlias_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "PageSnapshot" (
    "pageId" TEXT NOT NULL,
    "snapshotJson" JSONB NOT NULL,
    "notionLastEditedTime" TIMESTAMP(3) NOT NULL,
    "capturedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "PageSnapshot_pkey" PRIMARY KEY ("pageId")
);

-- CreateTable
CREATE TABLE "RefreshTarget" (
    "targetKind" "RefreshTargetKind" NOT NULL,
    "targetId" TEXT NOT NULL,
    "nextRefreshAt" TIMESTAMP(3) NOT NULL,
    "lastSyncedAt" TIMESTAMP(3),
    "failureCount" INTEGER NOT NULL DEFAULT 0,
    "lastError" TEXT,
    "lockedAt" TIMESTAMP(3),
    "lockedBy" TEXT,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "RefreshTarget_pkey" PRIMARY KEY ("targetKind","targetId")
);

-- CreateTable
CREATE TABLE "SyncRun" (
    "id" TEXT NOT NULL,
    "targetKind" "RefreshTargetKind" NOT NULL,
    "targetId" TEXT NOT NULL,
    "status" "SyncRunStatus" NOT NULL,
    "startedAt" TIMESTAMP(3) NOT NULL,
    "finishedAt" TIMESTAMP(3),
    "error" TEXT,

    CONSTRAINT "SyncRun_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE UNIQUE INDEX "SiteSettings_settingsDatabaseId_key" ON "SiteSettings"("settingsDatabaseId");

-- CreateIndex
CREATE UNIQUE INDEX "PageRoute_canonicalSlug_key" ON "PageRoute"("canonicalSlug");

-- CreateIndex
CREATE UNIQUE INDEX "SlugAlias_slug_key" ON "SlugAlias"("slug");

-- CreateIndex
CREATE INDEX "SlugAlias_pageId_idx" ON "SlugAlias"("pageId");

-- CreateIndex
CREATE INDEX "RefreshTarget_nextRefreshAt_idx" ON "RefreshTarget"("nextRefreshAt");

-- CreateIndex
CREATE INDEX "RefreshTarget_lockedAt_idx" ON "RefreshTarget"("lockedAt");

-- AddForeignKey
ALTER TABLE "PageRoute" ADD CONSTRAINT "PageRoute_pageId_fkey" FOREIGN KEY ("pageId") REFERENCES "NotionPage"("pageId") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "SlugAlias" ADD CONSTRAINT "SlugAlias_pageId_fkey" FOREIGN KEY ("pageId") REFERENCES "NotionPage"("pageId") ON DELETE CASCADE ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "PageSnapshot" ADD CONSTRAINT "PageSnapshot_pageId_fkey" FOREIGN KEY ("pageId") REFERENCES "NotionPage"("pageId") ON DELETE CASCADE ON UPDATE CASCADE;
