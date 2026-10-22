// The Concepts page's graph view (react-force-graph-2d draws it and handles zoom and clicks).
//
// A circle per concept: bigger = seen on more changes; filled grey (none understood),
// yellow (some) or green (most, 75%+). Concepts sit in a block per category (the # headings
// in concepts.txt, then suggestions), each block with its name on top. The blocks are laid
// out in rows here, not by physics, so circles, names and headings never overlap. Clicking a
// circle opens a side panel with the concept's meaning and its changes, the same list as
// the table.

import { useEffect, useMemo, useRef, useState } from "react";
import ForceGraph2D from "react-force-graph-2d";
import type { ForceGraphMethods, LinkObject, NodeObject } from "react-force-graph-2d";
import type { ConceptGraphData, ConceptGraphNode } from "../api/client";
import ConceptChanges from "./ConceptChanges";

type GraphNode = NodeObject<ConceptGraphNode>;

const FILL = { none: "#9ca3af", some: "#facc15", most: "#22c55e" };
const MAX_HEIGHT = 480;
const SUGGESTED = "Suggested";
const NO_LONGER = "No longer on your list";

// Layout sizes, in graph units (1 unit = 1 screen pixel before zooming).
const SLOT_WIDTH = 170; // one concept and its name
const SLOT_GAP = 22; // below a circle: room for its name
const PER_ROW = 2; // concepts side by side in a category block
const BLOCK_GAP = 40; // between category blocks
const HEADING = 30; // a block's heading
const MAX_LABEL = 26; // longer names are cut short (the full name is on hover and in the panel)

function fillFor(n: ConceptGraphNode): string {
  if (n.understood === 0) return FILL.none;
  return n.understood / n.seen >= 0.75 ? FILL.most : FILL.some;
}

function radius(n: ConceptGraphNode): number {
  return 8 + 6 * Math.sqrt(n.seen);
}

function fullName(n: ConceptGraphNode): string {
  return n.other ? `Other: ${n.name}` : n.name;
}

function shortName(n: ConceptGraphNode): string {
  const name = fullName(n);
  return name.length > MAX_LABEL ? `${name.slice(0, MAX_LABEL - 1).trimEnd()}…` : name;
}

function categoryOf(n: ConceptGraphNode): string {
  return n.group ?? (n.other ? SUGGESTED : NO_LONGER);
}

type Heading = { text: string; x: number; y: number };

/**
 * Places every concept: categories in concepts.txt order, as blocks that wrap into rows to
 * fit the width; inside a block, concepts most-seen first, PER_ROW to a line.
 */
function layout(data: ConceptGraphData, width: number) {
  const byCategory = new Map<string, ConceptGraphNode[]>();
  for (const category of [...data.groups, SUGGESTED, NO_LONGER]) byCategory.set(category, []);
  for (const n of data.nodes) byCategory.get(categoryOf(n))?.push(n);

  const tallest = Math.max(...data.nodes.map(radius), 0);
  const slotHeight = 2 * tallest + SLOT_GAP;
  const blockWidth = PER_ROW * SLOT_WIDTH;
  const perLine = Math.max(1, Math.floor((width + BLOCK_GAP) / (blockWidth + BLOCK_GAP)));

  const positions = new Map<string, { x: number; y: number }>();
  const headings: Heading[] = [];
  let column = 0;
  let top = 0;
  let lineHeight = 0;
  let right = 0;
  for (const [category, nodes] of byCategory) {
    if (nodes.length === 0) continue;
    if (column === perLine) {
      column = 0;
      top += lineHeight + BLOCK_GAP;
      lineHeight = 0;
    }
    const left = column * (blockWidth + BLOCK_GAP);
    headings.push({ text: category, x: left + blockWidth / 2, y: top });
    nodes.forEach((n, i) => {
      positions.set(n.id, {
        x: left + (i % PER_ROW) * SLOT_WIDTH + SLOT_WIDTH / 2,
        y: top + HEADING + Math.floor(i / PER_ROW) * slotHeight + tallest,
      });
    });
    lineHeight = Math.max(lineHeight, HEADING + Math.ceil(nodes.length / PER_ROW) * slotHeight);
    right = Math.max(right, left + blockWidth);
    column++;
  }
  return { positions, headings, width: right, height: top + lineHeight };
}

/** Follows the light/dark switch, so the labels stay readable. */
function useDark(): boolean {
  const [dark, setDark] = useState(() => document.documentElement.classList.contains("dark"));
  useEffect(() => {
    const observer = new MutationObserver(() => setDark(document.documentElement.classList.contains("dark")));
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["class"] });
    return () => observer.disconnect();
  }, []);
  return dark;
}

/** Text with a halo in the background color, so nothing behind it cuts through. */
function drawLabel(ctx: CanvasRenderingContext2D, text: string, x: number, y: number, dark: boolean, bold = false) {
  ctx.font = `${bold ? "600 13px" : "12px"} Inter Variable, sans-serif`;
  ctx.textAlign = "center";
  ctx.lineWidth = 3;
  ctx.lineJoin = "round";
  ctx.strokeStyle = dark ? "#111827" : "#ffffff";
  ctx.strokeText(text, x, y);
  ctx.fillStyle = dark ? (bold ? "#f9fafb" : "#e5e7eb") : bold ? "#111827" : "#374151";
  ctx.fillText(text, x, y);
}

function SidePanel({ node, onClose }: { node: ConceptGraphNode; onClose: () => void }) {
  return (
    <aside
      aria-label={`${node.name}: changes`}
      className="absolute top-3 right-3 bottom-3 w-80 overflow-y-auto rounded-lg border border-gray-200 bg-white p-4 shadow-lg"
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <h3 className="font-semibold text-gray-900">{fullName(node)}</h3>
          <p className="text-xs text-gray-500">{node.other ? "Suggested, not on your list" : node.group ?? NO_LONGER}</p>
        </div>
        <button type="button" onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-700">
          ✕
        </button>
      </div>
      {node.meaning && <p className="mt-2 text-sm text-gray-600">{node.meaning}</p>}
      <p className="mt-2 text-sm text-gray-700">
        <span className="font-semibold text-gray-900">
          {node.understood} of {node.seen}
        </span>{" "}
        {node.seen === 1 ? "change" : "changes"} understood
      </p>
      <div className="mt-3">
        <ConceptChanges changes={node.changes} />
      </div>
    </aside>
  );
}

export default function ConceptGraph({ data }: { data: ConceptGraphData }) {
  const box = useRef<HTMLDivElement>(null);
  const graph = useRef<ForceGraphMethods<GraphNode, LinkObject<ConceptGraphNode>> | undefined>(undefined);
  const [width, setWidth] = useState(800);
  const [selected, setSelected] = useState<ConceptGraphNode | null>(null);
  const dark = useDark();

  const placed = useMemo(() => layout(data, width - 60), [data, width]);
  // The box is only as tall as the layout needs, up to MAX_HEIGHT (then it zooms out to fit).
  const height = Math.min(MAX_HEIGHT, placed.height + 60);
  // Copies pinned in place (fx/fy), so the library's physics doesn't move them. No lines.
  const graphData = useMemo(
    () => ({
      nodes: data.nodes.map((n) => {
        const at = placed.positions.get(n.id) ?? { x: 0, y: 0 };
        return { ...n, x: at.x, y: at.y, fx: at.x, fy: at.y };
      }),
      links: [],
    }),
    [data, placed],
  );

  useEffect(() => {
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    if (box.current) observer.observe(box.current);
    return () => observer.disconnect();
  }, []);

  // Zoom to show the whole layout, headings included, but never past 1:1 (small graphs
  // would get huge text).
  function fit() {
    const g = graph.current;
    if (!g) return;
    g.centerAt(placed.width / 2, placed.height / 2);
    g.zoom(Math.min(1, (width - 40) / placed.width, (height - 40) / placed.height));
  }

  return (
    <div className="mt-4">
      <div ref={box} className="relative rounded-lg border border-gray-200 bg-white overflow-hidden" style={{ height }}>
        <ForceGraph2D
          ref={graph}
          graphData={graphData}
          width={width}
          height={height}
          backgroundColor="rgba(0,0,0,0)"
          cooldownTicks={1}
          onEngineStop={fit}
          nodeLabel={(n) => `${fullName(n)}: ${n.understood} of ${n.seen} understood`}
          nodeCanvasObject={(n, ctx, scale) => {
            const r = radius(n);
            ctx.beginPath();
            ctx.arc(n.x ?? 0, n.y ?? 0, r, 0, 2 * Math.PI);
            ctx.fillStyle = fillFor(n);
            ctx.fill();
            // A thin outline only, so a selected circle can stand out.
            ctx.lineWidth = (selected?.id === n.id ? 3 : 1) / Math.min(scale, 2);
            ctx.strokeStyle = selected?.id === n.id ? (dark ? "#f9fafb" : "#111827") : dark ? "#111827" : "#ffffff";
            ctx.stroke();
            ctx.textBaseline = "top";
            drawLabel(ctx, shortName(n), n.x ?? 0, (n.y ?? 0) + r + 4, dark);
          }}
          nodePointerAreaPaint={(n, color, ctx) => {
            ctx.fillStyle = color;
            ctx.beginPath();
            ctx.arc(n.x ?? 0, n.y ?? 0, radius(n) + 2, 0, 2 * Math.PI);
            ctx.fill();
          }}
          // Each category's name, on top of its block.
          onRenderFramePost={(ctx) => {
            ctx.textBaseline = "top";
            for (const h of placed.headings) drawLabel(ctx, h.text, h.x, h.y, dark, true);
          }}
          enableNodeDrag={false}
          onNodeClick={(n) => setSelected(data.nodes.find((d) => d.id === n.id) ?? null)}
          onBackgroundClick={() => setSelected(null)}
        />
        {selected && <SidePanel node={selected} onClose={() => setSelected(null)} />}
      </div>

      <div className="mt-2 px-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-600">
        {[
          ["None understood", FILL.none],
          ["Some", FILL.some],
          ["Most (75%+)", FILL.most],
        ].map(([label, color]) => (
          <span key={label} className="inline-flex items-center gap-1">
            <span className="inline-block w-2.5 h-2.5 rounded-full" style={{ background: color }} />
            {label}
          </span>
        ))}
        <span>Bigger = seen on more changes · grouped by category · click a circle</span>
      </div>
    </div>
  );
}
