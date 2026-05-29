import type { Metadata } from "next";
import "@/features/match-live/live-canvas.css";
import LiveCanvas from "@/features/match-live/LiveCanvas";

export const metadata: Metadata = {
  title: "Live Preview — Next Tennis Manager",
};

export default function LivePreviewPage() {
  return <LiveCanvas />;
}
