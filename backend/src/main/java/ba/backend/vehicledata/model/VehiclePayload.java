package ba.backend.vehicledata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VehiclePayload {

    private GpsData    gps;
    private Passengers passengers;
    private DeviceData device;

    // ── Getters / Setters ────────────────────────────────────────────────────

    public GpsData    getGps()        { return gps; }
    public Passengers getPassengers() { return passengers; }
    public DeviceData getDevice()     { return device; }

    public void setGps(GpsData g)        { this.gps        = g; }
    public void setPassengers(Passengers p) { this.passengers = p; }
    public void setDevice(DeviceData d)  { this.device     = d; }

    // ── GPS ──────────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GpsData {
        private boolean valid;
        private String  latitude;
        private String  longitude;
        @JsonProperty("speed_kmh")    private String speedKmh;
        @JsonProperty("heading_deg")  private String headingDeg;
        private String  compass;
        private int     satellites;
        @JsonProperty("bytes_received") private long bytesReceived;

        public boolean isValid()          { return valid; }
        public String  getLatitude()      { return latitude; }
        public String  getLongitude()     { return longitude; }
        public String  getSpeedKmh()      { return speedKmh; }
        public String  getHeadingDeg()    { return headingDeg; }
        public String  getCompass()       { return compass; }
        public int     getSatellites()    { return satellites; }
        public long    getBytesReceived() { return bytesReceived; }

        public void setValid(boolean v)            { this.valid         = v; }
        public void setLatitude(String v)          { this.latitude      = v; }
        public void setLongitude(String v)         { this.longitude     = v; }
        public void setSpeedKmh(String v)          { this.speedKmh      = v; }
        public void setHeadingDeg(String v)        { this.headingDeg    = v; }
        public void setCompass(String v)           { this.compass       = v; }
        public void setSatellites(int v)           { this.satellites    = v; }
        public void setBytesReceived(long v)       { this.bytesReceived = v; }
    }

    // ── Passengers ───────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Passengers {
        @JsonProperty("in")        private int in;
        @JsonProperty("out")       private int out;
        @JsonProperty("remaining") private int remaining;
        @JsonProperty("errors")    private int errors;

        public int getIn()        { return in; }
        public int getOut()       { return out; }
        public int getRemaining() { return remaining; }
        public int getErrors()    { return errors; }

        public void setIn(int v)        { this.in        = v; }
        public void setOut(int v)       { this.out       = v; }
        public void setRemaining(int v) { this.remaining = v; }
        public void setErrors(int v)    { this.errors    = v; }
    }

    // ── Device ───────────────────────────────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DeviceData {
        private String id;
        private String timestamp;
        @JsonProperty("uptime_s")    private long   uptimeSeconds;
        @JsonProperty("wifi_ssid")   private String wifiSsid;
        @JsonProperty("rssi_dbm")    private int    rssiDbm;
        @JsonProperty("free_heap_b") private long   freeHeapBytes;
        @JsonProperty("sends_ok")    private int    sendsOk;
        @JsonProperty("sends_fail")  private int    sendsFail;
        @JsonProperty("distance_km") private String distanceKm;

        public String getId()            { return id; }
        public String getTimestamp()     { return timestamp; }
        public long   getUptimeSeconds() { return uptimeSeconds; }
        public String getWifiSsid()      { return wifiSsid; }
        public int    getRssiDbm()       { return rssiDbm; }
        public long   getFreeHeapBytes() { return freeHeapBytes; }
        public int    getSendsOk()       { return sendsOk; }
        public int    getSendsFail()     { return sendsFail; }
        public String getDistanceKm()   { return distanceKm; }

        public void setId(String v)            { this.id            = v; }
        public void setTimestamp(String v)     { this.timestamp     = v; }
        public void setUptimeSeconds(long v)   { this.uptimeSeconds = v; }
        public void setWifiSsid(String v)      { this.wifiSsid      = v; }
        public void setRssiDbm(int v)          { this.rssiDbm       = v; }
        public void setFreeHeapBytes(long v)   { this.freeHeapBytes = v; }
        public void setSendsOk(int v)          { this.sendsOk       = v; }
        public void setSendsFail(int v)        { this.sendsFail     = v; }
        public void setDistanceKm(String v)    { this.distanceKm    = v; }
    }
}
