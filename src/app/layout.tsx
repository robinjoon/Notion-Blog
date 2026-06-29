import type { Metadata } from "next";
import "./globals.css";
import { getPageService } from "@/server/page-service";

function toMetadataBase(baseUrl?: string): URL | undefined {
  if (!baseUrl) {
    return undefined;
  }

  try {
    return new URL(baseUrl);
  } catch {
    return undefined;
  }
}

export async function generateMetadata(): Promise<Metadata> {
  const pageService = await getPageService();
  const head = await pageService.getSiteHeadSettings();

  return {
    metadataBase: toMetadataBase(head.baseUrl),
    title: head.titleTemplate && head.defaultTitle
      ? {
          default: head.defaultTitle,
          template: head.titleTemplate
        }
      : (head.defaultTitle ?? head.siteName ?? "Notion-Blog"),
    description: head.defaultDescription ?? "A self-hosted Notion blog",
    applicationName: head.siteName,
    icons: head.faviconUrl ? { icon: head.faviconUrl } : undefined,
    openGraph: {
      title: head.ogTitle ?? head.defaultTitle ?? head.siteName,
      description: head.ogDescription ?? head.defaultDescription,
      images: head.ogImageUrl ? [head.ogImageUrl] : undefined,
      type: (head.ogType as "website" | "article" | "book" | "profile" | "music.song" | "music.album" | "music.playlist" | "music.radio_station" | "video.movie" | "video.episode" | "video.tv_show" | "video.other" | undefined) ?? "website"
    },
    twitter: {
      card: (head.twitterCard as "summary" | "summary_large_image" | "player" | "app" | undefined) ?? "summary_large_image",
      site: head.twitterSite,
      title: head.ogTitle ?? head.defaultTitle ?? head.siteName,
      description: head.ogDescription ?? head.defaultDescription,
      images: head.ogImageUrl ? [head.ogImageUrl] : undefined
    },
    robots: head.robots
  };
}

export default async function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  const pageService = await getPageService();
  const head = await pageService.getSiteHeadSettings();

  return (
    <html lang={head.language ?? "ko"}>
      <head>{head.customCss ? <style>{head.customCss}</style> : null}</head>
      <body>{children}</body>
    </html>
  );
}
