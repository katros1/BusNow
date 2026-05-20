import 'package:dio/dio.dart';
import 'package:client/features/journey_planner/data/models/journey_plan_model.dart';
import 'package:client/features/journey_planner/data/models/nearest_stop_model.dart';
import 'package:client/features/journey_planner/domain/repositories/journey_planner_repository.dart';

class JourneyPlannerRemoteDataSource {
  final Dio _dio;
  const JourneyPlannerRemoteDataSource(this._dio);

  /// GET /journey-planner/plan
  /// Params:  fromLat, fromLng, toLat, toLng, maxSuggestions
  /// Convention: currentLocation / destinationLocation stored as [longitude, latitude].
  Future<JourneyPlanModel> planJourney(PlanJourneyParams params) async {
    try {
      final fromLng = params.currentLocation[0];
      final fromLat = params.currentLocation[1];
      final toLng = params.destinationLocation[0];
      final toLat = params.destinationLocation[1];

      final response = await _dio.get(
        '/journey-planner/plan',
        queryParameters: {
          'fromLat': fromLat,
          'fromLng': fromLng,
          'toLat': toLat,
          'toLng': toLng,
          'maxSuggestions': params.maxSuggestions ?? 5,
        },
      );
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
    return Exception(
        'Server error${statusCode != null ? ' ($statusCode)' : ''}: ${e.message}');
  }
}
