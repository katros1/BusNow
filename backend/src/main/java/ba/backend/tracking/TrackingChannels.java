package ba.backend.tracking;

/**
 * Redis pub/sub channel name constants for the live tracking pipeline.
 *
 * Published channels:
 *   tracking:vehicle:{plateNumber}  – one specific bus (used by RedisTrackingRelay)
 *   tracking:route:{routeId}        – all buses on a route (kept for future use)
 */
public final class TrackingChannels {

    private TrackingChannels() {}

    public static final String ROUTE_CHANNEL_PREFIX   = "tracking:route:";
    public static final String VEHICLE_CHANNEL_PREFIX = "tracking:vehicle:";

    public static String routeChannel(String routeId)       { return ROUTE_CHANNEL_PREFIX   + routeId; }
    public static String vehicleChannel(String plateNumber) { return VEHICLE_CHANNEL_PREFIX + plateNumber; }

    public static boolean isRouteChannel(String channel)   { return channel.startsWith(ROUTE_CHANNEL_PREFIX); }
    public static boolean isVehicleChannel(String channel) { return channel.startsWith(VEHICLE_CHANNEL_PREFIX); }

    public static String extractRouteId(String channel) { return channel.substring(ROUTE_CHANNEL_PREFIX.length()); }
    public static String extractPlate(String channel)   { return channel.substring(VEHICLE_CHANNEL_PREFIX.length()); }
}
