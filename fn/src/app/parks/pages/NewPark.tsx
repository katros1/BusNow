import { useState, useEffect, useCallback } from "react";
import L from "leaflet";
import MapView from "@/components/map/MapView";
import type { DrawnShape } from "@/components/map/DrawControl";
import { useCreatePark } from "../api/parks.mutations";
import type { CreateParkPayload } from "../api/parks.types";
import "./NewPark.css";

interface ParkDraft {
  name: string;
  shapes: DrawnShape[];
}

const SHAPE_ICONS: Record<DrawnShape["type"], string> = {
  polygon: "⬡",
  polyline: "—",
};

const SHAPE_LABELS: Record<DrawnShape["type"], string> = {
  polygon: "Polygon zone",
  polyline: "Polyline route",
};

function shapesToCoordinates(shapes: DrawnShape[]): number[][] {
  const coordinates: number[][] = [];
  for (const shape of shapes) {
    if (shape.type === "polygon") {
      const outerRing = (shape.latlngs as unknown as L.LatLng[][])[0] ?? [];
      for (const ll of outerRing) coordinates.push([ll.lat, ll.lng]);
    } else {
      for (const ll of shape.latlngs as L.LatLng[])
        coordinates.push([ll.lat, ll.lng]);
    }
  }
  return coordinates;
}

function buildCreatePayload(draft: ParkDraft): CreateParkPayload {
  return {
    name: draft.name.trim(),
    coordinates: shapesToCoordinates(draft.shapes),
  };
}

function NewPark() {
  const [draft, setDraft] = useState<ParkDraft>({ name: "", shapes: [] });
  const [validationError, setValidationError] = useState<string | null>(null);
  // const [copied, setCopied] = useState(false);

  const createPark = useCreatePark();

  useEffect(() => {
    if (draft.shapes.length === 0) return;
    console.table(
      draft.shapes.map((s) => ({
        id: s.id.slice(0, 8) + "…",
        type: s.type,
        vertices: s.vertexCount,
      })),
    );
  }, [draft.shapes]);

  const handleShapesChange = useCallback((shapes: DrawnShape[]) => {
    setDraft((prev) => ({ ...prev, shapes }));
  }, []);

  const handleSave = useCallback(() => {
    setValidationError(null);

    if (!draft.name.trim()) {
      setValidationError("Park name is required.");
      return;
    }
    if (draft.shapes.length === 0) {
      setValidationError("Draw at least one polygon or line on the map.");
      return;
    }

    createPark.mutate(buildCreatePayload(draft));
  }, [draft, createPark]);

    // const handleCopyResponse = useCallback(() => {
  //   if (!createPark.data) return;
  //   navigator.clipboard
  //     .writeText(JSON.stringify(createPark.data, null, 2))
  //     .then(() => {
  //       setCopied(true);
  //       setTimeout(() => setCopied(false), 2000);
  //     });
  // }, [createPark.data]);

  const polygonCount = draft.shapes.filter((s) => s.type === "polygon").length;
  const polylineCount = draft.shapes.filter((s) => s.type === "polyline").length;

  return (
    <div className="ns-page">
      <header className="ns-header">
        <div className="ns-header__left">
          <span className="ns-badge">
            <span className="ns-badge__dot" />
            Rwanda
          </span>
          <div>
            <h1 className="ns-header__title">Define Park Zone</h1>
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

        <aside className="ns-panel">
          <section className="ns-section">
            <h2 className="ns-section__title">Park Details</h2>
            <div className="ns-field">
              <label className="ns-label" htmlFor="park-name">
                Park name
              </label>
              <input
                id="park-name"
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

          <div className="ns-footer">
            {validationError && (
              <p className="ns-validation-error">{validationError}</p>
            )}
            <button
              id="save-park-btn"
              className="ns-save-btn"
              onClick={handleSave}
              disabled={createPark.isPending}
            >
              {createPark.isPending ? "Saving…" : "Save Park"}
            </button>
          </div>

          {createPark.isSuccess && createPark.data && (
            <>
              <div className="ns-divider" />
              <section className="ns-section">
                <h2 className="ns-section__title">
                  Saved
                  <span className="ns-section__badge ns-section__badge--green">
                    ✓
                  </span>
                </h2>
                {/* <div className="ns-payload-wrap">
                  <pre className="ns-payload-code">
                    {JSON.stringify(createPark.data, null, 2)}
                  </pre>
                  <button
                    className={`ns-copy-btn${copied ? " ns-copy-btn--done" : ""}`}
                    onClick={handleCopyResponse}
                    aria-label="Copy server response JSON"
                  >
                    {copied ? "✓ Copied" : "Copy JSON"}
                  </button>
                </div> */}
              </section>
            </>
          )}
        </aside>
      </div>
    </div>
  );
}

export default NewPark;
