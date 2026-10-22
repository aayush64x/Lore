// The Concepts page's graph view (react-force-graph-2d does the layout and physics).
//
// A circle per concept: bigger = seen on more changes; filled grey (none understood),
// yellow (some) or green (most, 75%+); outlined in its section's color from concepts.txt
// (dashed grey for "Other" suggestions). Sections are pulled toward their own spot so
// they cluster. A line joins two concepts tagged on the same change; thicker = more
// often together. Clicking a circle opens a side panel with that concept's changes.

import { useEffect, useMemo, useRef, useState } from "react";
import { Link } from "react-router-dom";
import ForceGraph2D from "react-force-graph-2d";
import type { ForceGraphMethods, LinkObject, NodeObject } from "react-force-graph-2d";
import { sessionTitle } from "../api/client";
import type { ConceptGraphData, ConceptGraphNode } from "../api/client";

type GraphNode = NodeObject<ConceptGraphNode>;

const SECTION_COLORS = ["#2563eb", "#9333ea", "#db2777", "#ea580c", "#0d9488", "#4f46e5", "#0891b2", "#65a30d"];
const FILL = { none: "#9ca3af", some: "#facc15", most: "#22c55e" };
const HEIGHT = 560;

function fillFor(n: ConceptGraphNode): string {
  if (n.understood === 0) return FILL.none;
  return n.understood / n.seen >= 0.75 ? FILL.most : FILL.some;
}

function radius(n: ConceptGraphNode): number {
  return 8 + 6 * Math.sqrt(n.seen);
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

function SidePanel({ node, color, onClose }: { node: ConceptGraphNode; color: string; onClose: () => void }) {
  return (
    <aside
      aria-label={`${node.name}: changes`}
      className="absolute top-3 right-3 bottom-3 w-80 overflow-y-auto rounded-lg border border-gray-200 bg-white p-4 shadow-lg"
    >
      <div className="flex items-start justify-between gap-2">
        <div>
          <h3 className="font-semibold text-gray-900">{node.other ? `Other: ${node.name}` : node.name}</h3>
          <p className="text-xs" style={{ color }}>
            {node.other ? "Suggested, not on your list" : node.group ?? "No longer on your list"}
          </p>
        </div>
        <button type="button" onClick={onClose} aria-label="Close" className="text-gray-400 hover:text-gray-700">
          ✕
        </button>
      </div>
      <p className="mt-2 text-sm text-gray-700">
        <span className="font-semibold text-gray-900">
          {node.understood} of {node.seen}
        </span>{" "}
        {node.seen === 1 ? "change" : "changes"} understood
      </p>
      <ul className="mt-3 space-y-3">
        {node.changes.map((c) => (
          <li key={c.changeId} className="text-sm">
            <Link
              to={`/sessions/${c.sessionId}/file?path=${encodeURIComponent(c.filePath)}`}
              className="font-mono text-xs text-blue-600 hover:underline break-all"
            >
              {c.filePath}
            </Link>
            {c.understood && <span className="ml-1.5 text-xs text-green-700">✓ understood</span>}
            <div className="text-xs text-gray-500">{sessionTitle(c.sessionReason)}</div>
            {c.description && <p className="mt-0.5 text-gray-700">{c.description}</p>}
          </li>
        ))}
      </ul>
    </aside>
  );
}

export default function ConceptGraph({ data }: { data: ConceptGraphData }) {
  const box = useRef<HTMLDivElement>(null);
  const graph = useRef<ForceGraphMethods<GraphNode, LinkObject<ConceptGraphNode>> | undefined>(undefined);
  const [width, setWidth] = useState(800);
  const [selected, setSelected] = useState<ConceptGraphNode | null>(null);
  const dark = useDark();

  // The library moves the objects it's given, so give it its own copies.
  const graphData = useMemo(
    () => ({ nodes: data.nodes.map((n) => ({ ...n })), links: data.links.map((l) => ({ ...l })) }),
    [data],
  );
  const colorOf = useMemo(() => {
    const colors = new Map(data.groups.map((g, i) => [g, SECTION_COLORS[i % SECTION_COLORS.length]]));
    return (n: ConceptGraphNode) => (n.group ? colors.get(n.group) ?? "#6b7280" : "#6b7280");
  }, [data.groups]);

  useEffect(() => {
    const observer = new ResizeObserver(([entry]) => setWidth(entry.contentRect.width));
    if (box.current) observer.observe(box.current);
    return () => observer.disconnect();
  }, []);

  // Each section gets a spot on a circle, and its concepts are nudged toward it, so
  // sections cluster. "Other" suggestions (no section) drift to the middle.
  useEffect(() => {
    const g = graph.current;
    if (!g) return;
    const spots = new Map(
      data.groups.map((group, i) => {
        const angle = (2 * Math.PI * i) / Math.max(data.groups.length, 1);
        return [group, { x: 150 * Math.cos(angle), y: 150 * Math.sin(angle) }];
      }),
    );
    let nodes: GraphNode[] = [];
    const cluster = Object.assign(
      (alpha: number) => {
        for (const n of nodes) {
          const spot = (n.group && spots.get(n.group)) || { x: 0, y: 0 };
          n.vx = (n.vx ?? 0) + (spot.x - (n.x ?? 0)) * 0.04 * alpha;
          n.vy = (n.vy ?? 0) + (spot.y - (n.y ?? 0)) * 0.04 * alpha;
        }
      },
      { initialize: (all: GraphNode[]) => (nodes = all) },
    );
    g.d3Force("section", cluster);
    // More room than the defaults, so circles and their labels don't overlap.
    (g.d3Force("charge") as unknown as { strength?: (s: number) => void } | undefined)?.strength?.(-260);
    (g.d3Force("link") as unknown as { distance?: (d: number) => void } | undefined)?.distance?.(75);
    g.d3ReheatSimulation();
  }, [data.groups, graphData]);

  return (
    <div className="mt-4">
      <div ref={box} className="relative rounded-lg border border-gray-200 bg-white overflow-hidden" style={{ height: HEIGHT }}>
        <ForceGraph2D
          ref={graph}
          graphData={graphData}
          width={width}
          height={HEIGHT}
          backgroundColor="rgba(0,0,0,0)"
          cooldownTicks={150}
          onEngineStop={() => graph.current?.zoomToFit(400, 70)}
          nodeLabel={(n) => `${n.name}: ${n.understood} of ${n.seen} understood`}
          nodeCanvasObject={(n, ctx, scale) => {
            const r = radius(n);
            ctx.beginPath();
            ctx.arc(n.x ?? 0, n.y ?? 0, r, 0, 2 * Math.PI);
            ctx.fillStyle = fillFor(n);
            ctx.fill();
            ctx.lineWidth = (selected?.id === n.id ? 4 : 2.5) / Math.min(scale, 2);
            ctx.strokeStyle = colorOf(n);
            ctx.setLineDash(n.other ? [3, 2] : []);
            ctx.stroke();
            ctx.setLineDash([]);
            const font = 12 / Math.min(scale, 2.5);
            ctx.font = `${font}px Inter Variable, sans-serif`;
            ctx.textAlign = "center";
            ctx.textBaseline = "top";
            const label = n.other ? `Other: ${n.name}` : n.name;
            const labelY = (n.y ?? 0) + r + 3 / Math.min(scale, 2);
            // A halo in the background color, so lines and circles behind the label don't cut through it.
            ctx.lineWidth = 3 / Math.min(scale, 2.5);
            ctx.lineJoin = "round";
            ctx.strokeStyle = dark ? "#111827" : "#ffffff";
            ctx.strokeText(label, n.x ?? 0, labelY);
            ctx.fillStyle = dark ? "#e5e7eb" : "#374151";
            ctx.fillText(label, n.x ?? 0, labelY);
          }}
          nodePointerAreaPaint={(n, color, ctx) => {
            ctx.fillStyle = color;
            ctx.beginPath();
            ctx.arc(n.x ?? 0, n.y ?? 0, radius(n) + 2, 0, 2 * Math.PI);
            ctx.fill();
          }}
          linkWidth={(l) => 1 + 2 * ((l.count as number) - 1)}
          linkColor={() => (dark ? "rgba(156,163,175,0.45)" : "rgba(107,114,128,0.35)")}
          linkLabel={(l) => `Together on ${l.count} ${l.count === 1 ? "change" : "changes"}`}
          onNodeClick={(n) => setSelected(data.nodes.find((d) => d.id === n.id) ?? null)}
          onBackgroundClick={() => setSelected(null)}
        />

        {selected && <SidePanel node={selected} color={colorOf(selected)} onClose={() => setSelected(null)} />}
      </div>
      {/* Legend: fill = understood, outline = section. */}
      <div className="mt-2 px-1 text-xs text-gray-600 space-y-1">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
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
        </div>
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          {data.groups.map((g) => (
            <span key={g} className="inline-flex items-center gap-1">
              <span className="inline-block w-2.5 h-2.5 rounded-full border-2" style={{ borderColor: colorOf({ group: g } as ConceptGraphNode) }} />
              {g}
            </span>
          ))}
          {data.nodes.some((n) => n.other) && (
            <span className="inline-flex items-center gap-1">
              <span className="inline-block w-2.5 h-2.5 rounded-full border-2 border-dashed border-gray-400" />
              Suggested
            </span>
          )}
        </div>
        <div>Bigger = seen on more changes · thicker line = tagged together more often · click a circle</div>
      </div>
    </div>
  );
}
