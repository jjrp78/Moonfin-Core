import 'package:flutter_test/flutter_test.dart';
import 'package:moonfin/data/services/iptv_recording_guard.dart';
import 'package:moonfin/ui/screens/livetv/iptv_recording_guide_banner.dart';

void main() {
  group('IptvRecordingGuideBanner', () {
    test('makes an active recording obvious and playable in the guide', () {
      const status = IptvRecordingStatus(
        isKnown: true,
        active: true,
        liveAvailable: true,
        channel: 'TNT Sports AR',
        title: 'Boca Juniors vs. Banfield',
        description: 'Liga Profesional',
        label: 'Boca vs. Banfield',
      );

      final banner = IptvRecordingGuideBanner.fromStatus(status);

      expect(banner.isVisible, isTrue);
      expect(banner.headline, 'RECORDING IN PROGRESS');
      expect(banner.detail, 'TNT Sports AR · Boca Juniors vs. Banfield');
      expect(banner.actionLabel, 'WATCH CURRENT RECORDING');
      expect(banner.canWatch, isTrue);
    });

    test('keeps the active banner visible while the live feed prepares', () {
      const status = IptvRecordingStatus(
        isKnown: true,
        active: true,
        liveAvailable: false,
        channel: 'TNT Sports AR',
        title: 'Boca Juniors vs. Banfield',
      );

      final banner = IptvRecordingGuideBanner.fromStatus(status);

      expect(banner.isVisible, isTrue);
      expect(banner.actionLabel, 'LIVE FEED PREPARING');
      expect(banner.canWatch, isFalse);
    });

    test('does not show a lockout banner for inactive or unknown status', () {
      const inactive = IptvRecordingStatus(
        isKnown: true,
        active: false,
        liveAvailable: false,
      );

      expect(IptvRecordingGuideBanner.fromStatus(inactive).isVisible, isFalse);
      expect(
        IptvRecordingGuideBanner.fromStatus(const IptvRecordingStatus.unknown())
            .isVisible,
        isFalse,
      );
    });
  });
}
