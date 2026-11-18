import '../../../data/services/iptv_recording_guard.dart';

/// Presentation-ready copy for the framework recorder status in the Live TV
/// guide. Unknown status stays invisible so the guide preserves fail-open
/// Jellyfin tuning behavior.
class IptvRecordingGuideBanner {
  const IptvRecordingGuideBanner._({
    required this.isVisible,
    required this.headline,
    required this.detail,
    required this.actionLabel,
    required this.canWatch,
  });

  final bool isVisible;
  final String headline;
  final String detail;
  final String actionLabel;
  final bool canWatch;

  static const _hidden = IptvRecordingGuideBanner._(
    isVisible: false,
    headline: '',
    detail: '',
    actionLabel: '',
    canWatch: false,
  );

  factory IptvRecordingGuideBanner.fromStatus(IptvRecordingStatus status) {
    if (!status.isKnown || !status.active) return _hidden;

    final detailParts = <String>[?status.channel, ?status.title];
    final detail = detailParts.isEmpty
        ? (status.label ?? 'An IPTV recording is active')
        : detailParts.join(' · ');

    return IptvRecordingGuideBanner._(
      isVisible: true,
      headline: 'RECORDING IN PROGRESS',
      detail: detail,
      actionLabel: status.canWatchLive
          ? 'WATCH CURRENT RECORDING'
          : 'LIVE FEED PREPARING',
      canWatch: status.canWatchLive,
    );
  }
}
