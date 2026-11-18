import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:moonfin/data/services/iptv_recording_guard.dart';

class _Client extends http.BaseClient {
  _Client(this._handler);

  final Future<http.StreamedResponse> Function(http.BaseRequest request)
  _handler;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) =>
      _handler(request);
}

http.StreamedResponse _json(int statusCode, String body) =>
    http.StreamedResponse(
      Stream.value(utf8.encode(body)),
      statusCode,
      headers: {HttpHeaders.contentTypeHeader: 'application/json'},
    );

void main() {
  test(
    'active framework recording exposes only its stable live playback URL',
    () async {
      Uri? requestedUri;
      final guard = IptvRecordingGuard(
        client: _Client((request) async {
          requestedUri = request.url;
          return _json(
            200,
            '{"active":true,"channel":"TNT Sports AR","title":"Huracán vs. Racing","description":"Liga Profesional","live_available":true}',
          );
        }),
      );

      final status = await guard.check();

      expect(requestedUri, IptvRecordingGuard.statusUri);
      expect(status.active, isTrue);
      expect(status.canWatchLive, isTrue);
      expect(status.title, 'Huracán vs. Racing');
      expect(status.liveUri, IptvRecordingGuard.liveUri);
    },
  );

  test(
    'an unavailable framework check leaves Jellyfin tuning unlocked',
    () async {
      final guard = IptvRecordingGuard(
        client: _Client((_) async => throw const SocketException('offline')),
      );

      final status = await guard.check();

      expect(status.active, isFalse);
      expect(status.isKnown, isFalse);
      expect(status.canWatchLive, isFalse);
    },
  );
}
