import 'package:latlong2/latlong.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';

class JourneyPlanModel {
  final List<RouteSuggestionModel> suggestions;
  const JourneyPlanModel({required this.suggestions});

  factory JourneyPlanModel.fromJson(Map<String, dynamic> json) {
    final rawList = json['suggestions'] as List<dynamic>? ?? [];
    return JourneyPlanModel(
      suggestions: rawList
          .map((e) => RouteSuggestionModel.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }

  JourneyPlan toEntity() =>
      JourneyPlan(suggestions: suggestions.map((s) => s.toEntity()).toList());
}

class RouteSuggestionModel {
  final String routeId;
  final String routeName;
  final List<LatLng> routeCoordinates;
  final RoutePointModel boardingPoint;
  final RoutePointModel destinationPoint;
  final double? walkToBoardingKm;           // null when GPS was unavailable
  final double distanceToDestinationKm;
  final double totalWalkingKm;
  final int walkToBoardingMinutes;
  final int distanceToDestinationMinutes;
  final int totalWalkingMinutes;
  final String tier;
  final int fareAmount;
  final int requiredCardBalance;

  const RouteSuggestionModel({
    required this.routeId,
    required this.routeName,
    required this.routeCoordinates,
    required this.boardingPoint,
    required this.destinationPoint,
    this.walkToBoardingKm,
    required this.distanceToDestinationKm,
    required this.totalWalkingKm,
    required this.walkToBoardingMinutes,
    required this.distanceToDestinationMinutes,
    required this.totalWalkingMinutes,
    required this.tier,
    this.fareAmount = 0,
    this.requiredCardBalance = 0,
  });

  factory RouteSuggestionModel.fromJson(Map<String, dynamic> json) {
    final rawCoords = json['routeCoordinates'] as List<dynamic>? ?? [];
    final coords = rawCoords.map((c) {
      final pair = c as List<dynamic>;
      // API returns [longitude, latitude]
      return LatLng((pair[1] as num).toDouble(), (pair[0] as num).toDouble());
    }).toList();

    final rawBoarding = json['walkToBoardingKm'];

    return RouteSuggestionModel(
      routeId: json['routeId'] as String? ?? '',
      routeName: json['routeName'] as String? ?? '',
      routeCoordinates: coords,
      boardingPoint: RoutePointModel.fromJson(
          json['boardingPoint'] as Map<String, dynamic>),
      destinationPoint: RoutePointModel.fromJson(
          json['destinationPoint'] as Map<String, dynamic>),
      walkToBoardingKm: rawBoarding != null ? (rawBoarding as num).toDouble() : null,
      distanceToDestinationKm:
          (json['distanceToDestinationKm'] as num? ?? 0).toDouble(),
      totalWalkingKm: (json['totalWalkingKm'] as num? ?? 0).toDouble(),
      walkToBoardingMinutes: json['walkToBoardingMinutes'] as int? ?? 0,
      distanceToDestinationMinutes:
          json['distanceToDestinationMinutes'] as int? ?? 0,
      totalWalkingMinutes: json['totalWalkingMinutes'] as int? ?? 0,
      tier: json['tier'] as String? ?? '',
      fareAmount: json['fareAmount'] as int? ?? 0,
      requiredCardBalance: json['requiredCardBalance'] as int? ?? 0,
    );
  }

  RouteSuggestion toEntity() => RouteSuggestion(
        routeId: routeId,
        routeName: routeName,
        routeCoordinates: routeCoordinates,
        boardingPoint: boardingPoint.toEntity(),
        destinationPoint: destinationPoint.toEntity(),
        walkToBoardingKm: walkToBoardingKm,
        distanceToDestinationKm: distanceToDestinationKm,
        totalWalkingKm: totalWalkingKm,
        walkToBoardingMinutes: walkToBoardingMinutes,
        distanceToDestinationMinutes: distanceToDestinationMinutes,
        totalWalkingMinutes: totalWalkingMinutes,
        tier: tier,
        fareAmount: fareAmount,
        requiredCardBalance: requiredCardBalance,
      );
}

class RoutePointModel {
  final String pointId;
  final String pointName;
  final String pointType;
  final int sequence;
  final LatLng coordinates;

  const RoutePointModel({
    required this.pointId,
    required this.pointName,
    required this.pointType,
    required this.sequence,
    required this.coordinates,
  });

  factory RoutePointModel.fromJson(Map<String, dynamic> json) {
    final raw = json['coordinates'] as List<dynamic>;
    // API returns [longitude, latitude]
    return RoutePointModel(
      pointId: json['pointId'] as String? ?? '',
      pointName: json['pointName'] as String? ?? '',
      pointType: json['pointType'] as String? ?? '',
      sequence: json['sequence'] as int? ?? 0,
      coordinates: LatLng(
        (raw[1] as num).toDouble(),
        (raw[0] as num).toDouble(),
      ),
    );
  }

  RoutePoint toEntity() => RoutePoint(
        pointId: pointId,
        pointName: pointName,
        pointType: pointType,
        sequence: sequence,
        coordinates: coordinates,
      );
}
