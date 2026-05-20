import 'package:equatable/equatable.dart';
import 'package:latlong2/latlong.dart';

class JourneyPlan extends Equatable {
  final List<RouteSuggestion> suggestions;
  const JourneyPlan({required this.suggestions});
  @override
  List<Object?> get props => [suggestions];
}

class RouteSuggestion extends Equatable {
  final String routeId;
  final String routeName;
  final List<LatLng> routeCoordinates;
  final RoutePoint boardingPoint;
  final RoutePoint destinationPoint;
  final double walkToBoardingKm;
  final double walkToDestinationKm;
  final double totalWalkingKm;
  final int walkToBoardingMinutes;
  final int walkToDestinationMinutes;
  final int totalWalkingMinutes;
  final String tier;

  const RouteSuggestion({
    required this.routeId,
    required this.routeName,
    required this.routeCoordinates,
    required this.boardingPoint,
    required this.destinationPoint,
    required this.walkToBoardingKm,
    required this.walkToDestinationKm,
    required this.totalWalkingKm,
    required this.walkToBoardingMinutes,
    required this.walkToDestinationMinutes,
    required this.totalWalkingMinutes,
    required this.tier,
  });

  @override
  List<Object?> get props => [routeId];
}

class RoutePoint extends Equatable {
  final String pointId;
  final String pointName;
  final String pointType;
  final int sequence;
  final LatLng coordinates;

  const RoutePoint({
    required this.pointId,
    required this.pointName,
    required this.pointType,
    required this.sequence,
    required this.coordinates,
  });

  bool get isBusPark => pointType == 'BUS_PARK';

  @override
  List<Object?> get props => [pointId];
}

class NearestStop extends Equatable {
  final String stopId;
  final String stopName;
  final LatLng coordinates;
  final double distanceKm;

  const NearestStop({
    required this.stopId,
    required this.stopName,
    required this.coordinates,
    required this.distanceKm,
  });

  @override
  List<Object?> get props => [stopId];
}

// Centroid of a stop's polygon geometry from the route stops endpoint.
class RouteStopPoint extends Equatable {
  final String id;
  final String name;
  final int sequence;
  final LatLng coordinates;

  const RouteStopPoint({
    required this.id,
    required this.name,
    required this.sequence,
    required this.coordinates,
  });

  factory RouteStopPoint.fromJson(Map<String, dynamic> json) {
    final coords = json['coordinates'] as List<dynamic>;
    // Compute centroid from polygon ring [[lng,lat], ...]
    double sumLng = 0, sumLat = 0;
    int count = 0;
    for (final pt in coords) {
      final pair = pt as List<dynamic>;
      sumLng += (pair[0] as num).toDouble();
      sumLat += (pair[1] as num).toDouble();
      count++;
    }
    return RouteStopPoint(
      id: json['id'] as String,
      name: json['name'] as String,
      sequence: json['sequence'] as int,
      coordinates: count > 0
          ? LatLng(sumLat / count, sumLng / count)
          : const LatLng(0, 0),
    );
  }

  @override
  List<Object?> get props => [id];
}

class OsmPlace extends Equatable {
  final String name;
  final String displayName;
  final double lat;
  final double lon;

  const OsmPlace({
    required this.name,
    required this.displayName,
    required this.lat,
    required this.lon,
  });

  factory OsmPlace.fromJson(Map<String, dynamic> json) {
    final displayName = json['display_name'] as String;
    final rawName = json['name'] as String?;
    final name = (rawName != null && rawName.isNotEmpty)
        ? rawName
        : displayName.split(',').first.trim();
    return OsmPlace(
      name: name,
      displayName: displayName,
      lat: double.parse(json['lat'] as String),
      lon: double.parse(json['lon'] as String),
    );
  }

  factory OsmPlace.fromPhoton(Map<String, dynamic> feature) {
    final coords =
        (feature['geometry'] as Map<String, dynamic>)['coordinates'] as List<dynamic>;
    final props = feature['properties'] as Map<String, dynamic>;
    final name = props['name'] as String? ??
        props['street'] as String? ??
        props['city'] as String? ??
        '';
    final city = props['city'] as String? ?? props['county'] as String? ?? '';
    final country = props['country'] as String? ?? '';
    final parts = <String>[
      if (name.isNotEmpty) name,
      if (city.isNotEmpty && city != name) city,
      if (country.isNotEmpty) country,
    ];
    return OsmPlace(
      name: name.isNotEmpty ? name : (city.isNotEmpty ? city : 'Place'),
      displayName: parts.join(', '),
      lat: (coords[1] as num).toDouble(),
      lon: (coords[0] as num).toDouble(),
    );
  }

  @override
  List<Object?> get props => [lat, lon];
}
