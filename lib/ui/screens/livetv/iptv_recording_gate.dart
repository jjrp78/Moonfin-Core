import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../../../data/services/iptv_recording_guard.dart';
import '../playback/trailer_player_screen.dart';

/// Protects the local IPTV framework's active recording without making
/// ordinary Jellyfin Live TV dependent on that framework being reachable.
///
/// A confirmed active recording replaces a requested Jellyfin channel with the
/// framework's stable recording stream. An unknown check deliberately falls
/// through to [child], preserving normal Jellyfin tuning.
class IptvRecordingGate extends StatefulWidget {
  const IptvRecordingGate({
    required this.child,
    this.openCurrentRecordingOnly = false,
    super.key,
  });

  final Widget child;
  final bool openCurrentRecordingOnly;

  @override
  State<IptvRecordingGate> createState() => _IptvRecordingGateState();
}

class _IptvRecordingGateState extends State<IptvRecordingGate> {
  IptvRecordingStatus? _status;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final status = await IptvRecordingGuard().check();
    if (mounted) setState(() => _status = status);
  }

  @override
  Widget build(BuildContext context) {
    final status = _status;
    if (status == null) {
      return const Scaffold(
        backgroundColor: Colors.black,
        body: Center(child: CircularProgressIndicator()),
      );
    }

    if (status.active && status.canWatchLive) {
      return TrailerPlayerScreen(
        trailerUrl: IptvRecordingGuard.liveUri.toString(),
        directStream: true,
      );
    }

    if (status.active) {
      return _RecordingLockedScreen(status: status);
    }

    if (widget.openCurrentRecordingOnly) {
      return const _NoCurrentRecordingScreen();
    }

    return widget.child;
  }
}

class _RecordingLockedScreen extends StatelessWidget {
  const _RecordingLockedScreen({required this.status});

  final IptvRecordingStatus status;

  @override
  Widget build(BuildContext context) {
    final title = status.title ?? status.label ?? 'Current IPTV recording';
    final details = [
      if (status.channel != null) status.channel!,
      if (status.description != null) status.description!,
    ];
    return Scaffold(
      backgroundColor: const Color(0xFF12070A),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 740),
            child: Padding(
              padding: const EdgeInsets.all(40),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Icon(
                    Icons.fiber_manual_record,
                    color: Colors.redAccent,
                    size: 54,
                  ),
                  const SizedBox(height: 24),
                  Text(
                    'IPTV RECORDING IN PROGRESS',
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                      color: Colors.white,
                      fontWeight: FontWeight.w900,
                    ),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    title,
                    style: Theme.of(context).textTheme.titleLarge?.copyWith(
                      color: Colors.white,
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  if (details.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Text(
                      details.join(' · '),
                      style: Theme.of(context).textTheme.titleMedium
                          ?.copyWith(color: Colors.white70),
                    ),
                  ],
                  const SizedBox(height: 28),
                  Text(
                    'Channel tuning is locked to protect this recording. The live stream is not ready yet.',
                    style: Theme.of(context).textTheme.bodyLarge
                        ?.copyWith(color: Colors.white70),
                  ),
                  const SizedBox(height: 30),
                  FilledButton.icon(
                    onPressed: () => context.pop(),
                    icon: const Icon(Icons.arrow_back),
                    label: const Text('BACK'),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _NoCurrentRecordingScreen extends StatelessWidget {
  const _NoCurrentRecordingScreen();

  @override
  Widget build(BuildContext context) => Scaffold(
    backgroundColor: Colors.black,
    body: Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.tv_off, color: Colors.white70, size: 50),
          const SizedBox(height: 18),
          Text(
            'No current IPTV recording',
            style: Theme.of(context).textTheme.titleLarge
                ?.copyWith(color: Colors.white, fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 24),
          FilledButton.icon(
            onPressed: () => context.pop(),
            icon: const Icon(Icons.arrow_back),
            label: const Text('BACK'),
          ),
        ],
      ),
    ),
  );
}
