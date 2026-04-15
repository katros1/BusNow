import { useEffect } from "react";
import { useMap } from "react-leaflet";
import { GeoSearchControl, OpenStreetMapProvider } from "leaflet-geosearch";
import "leaflet-geosearch/dist/geosearch.css";

// Rwanda bounding box: SW(-2.84, 28.86)  NE(-1.05, 30.90)
const RWANDA_VIEWBOX = "28.8617,-2.8389,30.8990,-1.0474" as const;

function SearchControl() {
  const map = useMap();

  useEffect(() => {
    const provider = new OpenStreetMapProvider({
      params: {
        countrycodes: "rw",
        viewbox: RWANDA_VIEWBOX,
        bounded: 1,
      },
    });

    // @ts-expect-error – GeoSearchControl typings lag behind the runtime API
    const searchControl = new GeoSearchControl({
      provider,
      style: "bar",
      showMarker: true,
      showPopup: false,
      autoClose: true,
      retainZoomLevel: false,
      animateZoom: true,
      keepResult: true,
      searchLabel: "Search in Rwanda…",
      notFoundMessage: "No places found in Rwanda",
    });

    map.addControl(searchControl);
    return () => {
      map.removeControl(searchControl);
    };
  }, [map]);

  return null;
}

export default SearchControl;
