import 'package:dartz/dartz.dart';
import 'package:client/core/error/failures.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/domain/repositories/journey_planner_repository.dart';

export 'package:client/features/journey_planner/domain/repositories/journey_planner_repository.dart'
    show PlanJourneyParams;

class PlanJourneyUseCase {
  final JourneyPlannerRepository _repository;
  const PlanJourneyUseCase(this._repository);

  Future<Either<Failure, JourneyPlan>> call(PlanJourneyParams params) =>
      _repository.planJourney(params);
}
