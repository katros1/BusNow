import 'package:dio/dio.dart';
import 'package:client/features/journey_planner/data/models/journey_plan_model.dart';
import 'package:client/features/journey_planner/data/models/nearest_stop_model.dart';
import 'package:client/features/journey_planner/domain/repositories/journey_planner_repository.dart';

class JourneyPlannerRemoteDataSource {
  final Dio _dio;
  const JourneyPlannerRemoteDataSource(this._dio);

  Future<JourneyPlanModel> planJourney(PlanJourneyParams params) async {
    try {
      final body = {
        'currentLocation': params.currentLocation,
        'destinationLocation': params.destinationLocation,
        'maxSuggestions': params.maxSuggestions ?? 5,
      };
      final response = await _dio.post('/journey-planner/plan', data: body);
      return JourneyPlanModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  Future<NearestStopModel> findNearestStop(List<double> currentLocation) async {
    try {
      final response = await _dio.post(
        '/journey-planner/nearest-stop',
        data: {'currentLocation': currentLocation},
      );
      return NearestStopModel.fromJson(response.data as Map<String, dynamic>);
    } on DioException catch (e) {
      throw _handleDioError(e);
    }
  }

  Exception _handleDioError(DioException e) {
    final statusCode = e.response?.statusCode;
    if (e.type == DioExceptionType.connectionTimeout ||
        e.type == DioExceptionType.receiveTimeout ||
        e.type == DioExceptionType.sendTimeout ||
        e.type == DioExceptionType.connectionError) {
      return Exception('Network error: Unable to reach server');
    }
    return Exception('Server error${statusCode != null ? ' ($statusCode)' : ''}: ${e.message}');
  }
}
