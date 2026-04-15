import { useState, useEffect, useCallback } from "react";
import MapView from "@/components/map/MapView";
import type { DrawnShape } from "@/components/map/DrawControl";
import L from "leaflet";
import "./NewStop.css";

interface StopDraft {
  name: string;
  shapes: DrawnShape[];
}

interface StopPayload {
  name: string;
  coordinates: number[][];
}

const SHAPE_ICONS: Record<DrawnShape["type"], string> = {
  polygon: "⬡",
  polyline: "—",
};

const SHAPE_LABELS: Record<DrawnShape["type"], string> = {
  polygon: "Polygon zone",
  polyline: "Polyline route",
};

/**
 * Converts all drawn shapes into a flat list of [lat, lng] pairs.
 * Polygons: takes the outer ring only (index 0).
 * Polylines: takes the flat latlngs array.
 */
function buildPayload(name: string, shapes: DrawnShape[]): StopPayload {
  const coordinates: number[][] = [];

  for (const shape of shapes) {
    if (shape.type === "polygon") {
      const outerRing = (shape.latlngs as unknown as L.LatLng[][])[0] ?? [];
      for (const ll of outerRing) {
        coordinates.push([ll.lat, ll.lng]);
      }
    } else {
      const pts = shape.latlngs as L.LatLng[];
      for (const ll of pts) {
        coordinates.push([ll.lat, ll.lng]);
      }
    }
  }

  return { name, coordinates };
}

function NewStop() {
  const [draft, setDraft] = useState<StopDraft>({
    name: "",
    shapes: [],
  });

  const [savedPayload, setSavedPayload] = useState<StopPayload | null>(null);
  const [copied, setCopied] = useState(false);
  const [validationError, setValidationError] = useState<string | null>(null);

  useEffect(() => {
    if (draft.shapes.length === 0) return;

    const logPayload = {
      stopName: draft.name || "(unnamed)",
      shapeCount: draft.shapes.length,
      shapes: draft.shapes.map((s) => ({
        id: s.id,
        type: s.type,
        vertexCount: s.vertexCount,
        coordinates: s.latlngs,
      })),
    };

    console.group(
      `[NewStop] Drawn shapes updated — ${draft.shapes.length} shape(s)`,
    );
    console.table(
      draft.shapes.map((s) => ({
        id: s.id.slice(0, 8) + "…",
        type: s.type,
        vertices: s.vertexCount,
      })),
    );
    console.log("Full payload:", logPayload);
    console.groupEnd();

    sessionStorage.setItem("newStop_draft_shapes", JSON.stringify(logPayload));
  }, [draft.shapes, draft.name]);

  const handleShapesChange = useCallback((shapes: DrawnShape[]) => {
    setDraft((prev) => ({ ...prev, shapes }));
  }, []);


  const polygonCount = draft.shapes.filter((s) => s.type === "polygon").length;
  const polylineCount = draft.shapes.filter(
    (s) => s.type === "polyline",
  ).length;

  const handleSave = useCallback(() => {
    setValidationError(null);

    if (!draft.name.trim()) {
      setValidationError("Stop name is required before saving.");
      return;
    }
    if (draft.shapes.length === 0) {
      setValidationError("Draw at least one polygon or line on the map.");
      return;
    }

    const payload = buildPayload(draft.name.trim(), draft.shapes);
    setSavedPayload(payload);

    console.group("[NewStop] ✅ Stop saved — payload ready for backend");
    console.log(JSON.stringify(payload, null, 2));
    console.groupEnd();

    sessionStorage.setItem("newStop_payload", JSON.stringify(payload));
  }, [draft.name, draft.shapes]);

  const handleCopy = useCallback(() => {
    if (!savedPayload) return;
    navigator.clipboard
      .writeText(JSON.stringify(savedPayload, null, 2))
      .then(() => {
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      });
  }, [savedPayload]);

  return (
    <div className="ns-page">
      <header className="ns-header">
        <div className="ns-header__left">
          <span className="ns-badge">
            <span className="ns-badge__dot" />
            Rwanda
          </span>
          <div>
            <h1 className="ns-header__title">Define Stop Zone</h1>
            <p className="ns-header__sub">
              Search a location, then draw polygons or lines to mark boundaries
            </p>
          </div>
        </div>

        <div className="ns-header__right">
          {polygonCount > 0 && (
            <div className="ns-chip ns-chip--blue">
              ⬡ {polygonCount} {polygonCount === 1 ? "zone" : "zones"}
            </div>
          )}
          {polylineCount > 0 && (
            <div className="ns-chip ns-chip--amber">
              — {polylineCount} {polylineCount === 1 ? "line" : "lines"}
            </div>
          )}
        </div>
      </header>

      <div className="ns-body">
        <div className="ns-map-wrap">
          <div className="ns-map-hint">
            <span>🔍 Search</span>
            <span className="ns-map-hint__sep" />
            <span>⬡ Draw polygon</span>
            <span className="ns-map-hint__sep" />
            <span>— Draw line</span>
          </div>
          <MapView onShapesChange={handleShapesChange} />
        </div>

        {/* Side panel */}
        <aside className="ns-panel">
          {/* Stop details */}
          <section className="ns-section">
            <h2 className="ns-section__title">Stop Details</h2>

            <div className="ns-field">
              <label className="ns-label" htmlFor="stop-name">
                Stop name
              </label>
              <input
                id="stop-name"
                className="ns-input"
                type="text"
                placeholder="e.g. Kigali Bus Terminal"
                value={draft.name}
                onChange={(e) =>
                  setDraft((prev) => ({ ...prev, name: e.target.value }))
                }
              />
            </div>
          </section>

          <div className="ns-divider" />

          {/* Drawn shapes */}
          <section className="ns-section ns-section--grow">
            <h2 className="ns-section__title">
              Drawn Shapes
              {draft.shapes.length > 0 && (
                <span className="ns-section__badge">{draft.shapes.length}</span>
              )}
            </h2>

            {draft.shapes.length === 0 ? (
              <div className="ns-empty">
                <span className="ns-empty__icon">✏️</span>
                <p>No shapes yet. Use the toolbar on the map to draw.</p>
              </div>
            ) : (
              <ul className="ns-shape-list">
                {draft.shapes.map((shape, idx) => (
                  <li
                    key={shape.id}
                    className={`ns-shape-item ns-shape-item--${shape.type}`}
                  >
                    <span className="ns-shape-item__icon">
                      {SHAPE_ICONS[shape.type]}
                    </span>
                    <div className="ns-shape-item__info">
                      <span className="ns-shape-item__label">
                        {SHAPE_LABELS[shape.type]} #{idx + 1}
                      </span>
                      <span className="ns-shape-item__meta">
                        {shape.vertexCount} vertices · {shape.id.slice(0, 8)}
                      </span>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <div className="ns-divider" />

          {/* Save footer */}
          <div className="ns-footer">
            {validationError && (
              <p className="ns-validation-error">{validationError}</p>
            )}
            <button
              id="save-stop-btn"
              className="ns-save-btn"
              onClick={handleSave}
            >
              Save Stop
            </button>
          </div>

          {/* Payload preview */}
          {savedPayload && (
            <>
              <div className="ns-divider" />
              <section className="ns-section">
                <h2 className="ns-section__title">
                  Payload
                  <span className="ns-section__badge ns-section__badge--green">
                    Ready
                  </span>
                </h2>
                <div className="ns-payload-wrap">
                  <pre className="ns-payload-code">
                    {JSON.stringify(savedPayload, null, 2)}
                  </pre>
                  <button
                    className={`ns-copy-btn${copied ? " ns-copy-btn--done" : ""}`}
                    onClick={handleCopy}
                    aria-label="Copy JSON payload"
                  >
                    {copied ? "✓ Copied" : "Copy JSON"}
                  </button>
                </div>
              </section>
            </>
          )}
        </aside>
      </div>
    </div>
  );
}

export default NewStop;
