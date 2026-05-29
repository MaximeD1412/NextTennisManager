import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Next Tennis Manager",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="fr">
      <body>{children}</body>
    </html>
  );
}
