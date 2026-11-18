import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;

/// Safe, unauthenticated state exposed by the local IPTV recording framework.
///
/// A check failure intentionally produces [IptvRecordingStatus.unknown] so
/// Jellyfin Live TV continues to work normally when the local framework is
/// unreachable.
class IptvRecordingStatus {
  const IptvRecordingStatus({
    required this.isKnown,
    required this.active,
    required this.liveAvailable,
    this.channel,
    this.title,
    this.description,
    this.label,
  });

  const IptvRecordingStatus.unknown()
    : isKnown = false,
      active = false,
      liveAvailable = false,
      channel = null,
      title = null,
      description = null,
      label = null;

  final bool isKnown;
  final bool active;
  final bool liveAvailable;
  final String? channel;
  final String? title;
  final String? description;
  final String? label;

  bool get canWatchLive => active && liveAvailable;

  Uri? get liveUri => canWatchLive ? IptvRecordingGuard.liveUri : null;

  factory IptvRecordingStatus.fromJson(Map<String, dynamic> json) {
    String? readText(String key) {
      final value = json[key];
      if (value is! String) return null;
      final trimmed = value.trim();
      return trimmed.isEmpty ? null : trimmed;
    }

    return IptvRecordingStatus(
      isKnown: true,
      active: json['active'] == true,
      liveAvailable: json['live_available'] == true,
      channel: readText('channel'),
      title: readText('title'),
      description: readText('description'),
      label: readText('label'),
    );
  }
}

class IptvRecordingGuard {
  IptvRecordingGuard({http.Client? client}) : _client = client ?? http.Client();

  static final Uri statusUri = Uri.parse(
    'http://192.168.1.4:5000/api/recording/live/status',
  );
  static final Uri liveUri = Uri.parse(
    'http://192.168.1.4:5000/recording/live/current/index.m3u8',
  );
  static const _timeout = Duration(seconds: 3);

  final http.Client _client;

  Future<IptvRecordingStatus> check() async {
    try {
      final response = await _client.get(statusUri).timeout(_timeout);
      if (response.statusCode != 200) {
        return const IptvRecordingStatus.unknown();
      }
      final decoded = jsonDecode(response.body);
      if (decoded is! Map<String, dynamic>) {
        return const IptvRecordingStatus.unknown();
      }
      return IptvRecordingStatus.fromJson(decoded);
    } on Object {
      return const IptvRecordingStatus.unknown();
    } finally {
      _client.close();
    }
  }
}
