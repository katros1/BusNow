package ba.backend.tracking.dto;

import java.util.UUID;

/**
 * WebSocket subscription request sent by the frontend before listening for updates.
 *
 * <p>For all vehicles on a route omit {@code plateNumber}.
 * <br>For a specific vehicle provide {@code plateNumber} and set {@code continueTracking=true}
 * to keep streaming after the bus passes the destination stop.
 *
 * <p>After sending this message the client should STOMP-subscribe to:
 * <ul>
 *   <li>{@code /topic/tracking/route/{routeId}} – all buses on the route</li>
 *   <li>{@code /topic/tracking/vehicle/{plateNumber}} – one specific bus</li>
 * </ul>
 */
public record TrackingSubscribeRequest(
        UUID    routeId,
        String  boardingStopId,
        String  destinationStopId,
        String  plateNumber,
        Boolean continueTracking
) {}
