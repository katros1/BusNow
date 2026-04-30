import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:client/features/journey_planner/domain/entities/journey_entities.dart';
import 'package:client/features/journey_planner/domain/usecases/plan_journey_usecase.dart';
import 'package:client/features/journey_planner/presentation/providers/journey_planner_providers.dart';

class JourneyPlanNotifier extends AsyncNotifier<JourneyPlan?> {
  @override
  Future<JourneyPlan?> build() async => null;

  Future<void> plan({
    required List<double> origin,
    required List<double> destination,
  }) async {
    state = const AsyncLoading();
    final result = await ref.read(planJourneyUseCaseProvider).call(
          PlanJourneyParams(
            currentLocation: origin,
            destinationLocation: destination,
          ),
        );
    state = result.fold(
      (failure) => AsyncError(failure.message, StackTrace.current),
      AsyncData.new,
    );
  }
}
