import 'package:dartz/dartz.dart';
import 'package:client/core/error/failures.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/domain/repositories/journey_planner_repository.dart';

class FindNearestStopUseCase {
  final JourneyPlannerRepository _repository;
  const FindNearestStopUseCase(this._repository);

  Future<Either<Failure, NearestStop>> call(List<double> currentLocation) =>
      _repository.findNearestStop(currentLocation);
}
