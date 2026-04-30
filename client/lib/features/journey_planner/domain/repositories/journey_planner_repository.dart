import 'package:dartz/dartz.dart';
import 'package:equatable/equatable.dart';
import 'package:client/core/error/failures.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';

class PlanJourneyParams extends Equatable {
  final List<double> currentLocation;
  final List<double> destinationLocation;
  final int? maxSuggestions;

  const PlanJourneyParams({
    required this.currentLocation,
    required this.destinationLocation,
    this.maxSuggestions,
  });

  @override
  List<Object?> get props => [currentLocation, destinationLocation, maxSuggestions];
}

abstract interface class JourneyPlannerRepository {
  Future<Either<Failure, JourneyPlan>> planJourney(PlanJourneyParams params);
  Future<Either<Failure, NearestStop>> findNearestStop(List<double> currentLocation);
}
