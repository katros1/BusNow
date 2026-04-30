import 'package:dartz/dartz.dart';
import 'package:dio/dio.dart';
import 'package:client/core/error/failures.dart';
import 'package:client/features/journey_planner/data/datasources/journey_planner_remote_datasource.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/domain/repositories/journey_planner_repository.dart';

class JourneyPlannerRepositoryImpl implements JourneyPlannerRepository {
  final JourneyPlannerRemoteDataSource _dataSource;
  const JourneyPlannerRepositoryImpl(this._dataSource);

  @override
  Future<Either<Failure, JourneyPlan>> planJourney(PlanJourneyParams params) async {
    try {
      final model = await _dataSource.planJourney(params);
      return Right(model.toEntity());
    } on DioException catch (e) {
      return Left(_mapDioException(e));
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  @override
  Future<Either<Failure, NearestStop>> findNearestStop(List<double> currentLocation) async {
    try {
      final model = await _dataSource.findNearestStop(currentLocation);
      return Right(model.toEntity());
    } on DioException catch (e) {
      return Left(_mapDioException(e));
    } catch (e) {
      return Left(ServerFailure(e.toString()));
    }
  }

  Failure _mapDioException(DioException e) {
    if (e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout ||
        e.type == DioExceptionType.sendTimeout ||
        e.type == DioExceptionType.connectionError) {
      return const NetworkFailure('No internet connection or server unreachable');
    }
    final statusCode = e.response?.statusCode;
    return ServerFailure('Server error${statusCode != null ? ' ($statusCode)' : ''}: ${e.message ?? ''}');
  }
}
