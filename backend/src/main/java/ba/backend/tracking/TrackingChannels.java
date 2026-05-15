package ba.backend.tracking;

public final class TrackingChannels {

    private TrackingChannels() {}

    // Redis pub/sub channel prefixes
    public static final String ROUTE_CHANNEL_PREFIX   = "tracking:route:";
    public static final String VEHICLE_CHANNEL_PREFIX = "tracking:vehicle:";

    // STOMP topic prefixes
    public static final String ROUTE_TOPIC_PREFIX   = "/topic/tracking/route/";
    public static final String VEHICLE_TOPIC_PREFIX = "/topic/tracking/vehicle/";

    public static String routeChannel(String routeId)       { return ROUTE_CHANNEL_PREFIX   + routeId; }
    public static String vehicleChannel(String plateNumber) { return VEHICLE_CHANNEL_PREFIX + plateNumber; }
    public static String routeTopic(String routeId)         { return ROUTE_TOPIC_PREFIX     + routeId; }
    public static String vehicleTopic(String plateNumber)   { return VEHICLE_TOPIC_PREFIX   + plateNumber; }

    public static boolean isRouteChannel(String channel)   { return channel.startsWith(ROUTE_CHANNEL_PREFIX); }
    public static boolean isVehicleChannel(String channel) { return channel.startsWith(VEHICLE_CHANNEL_PREFIX); }

    public static String extractRouteId(String channel)   { return channel.substring(ROUTE_CHANNEL_PREFIX.length()); }
    public static String extractPlate(String channel)     { return channel.substring(VEHICLE_CHANNEL_PREFIX.length()); }
}
