import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'platform_file.dart';

/// The outcome of a pick whose app was restarted before it completed.
///
/// On Android, the system can destroy the activity, or the whole app process,
/// while the file picker is open, for example on low memory devices. The
/// files the user picked are then delivered to a fresh instance of the app,
/// which can recover them through `FilePicker.retrieveLostData()`.
@immutable
class LostFilePickerResult {
  /// Creates a [LostFilePickerResult] with the picked [files] or the
  /// [exception] that prevented processing them.
  const LostFilePickerResult({this.files = const [], this.exception});

  /// The files the user picked, empty if [exception] is set.
  final List<PlatformFile> files;

  /// The error raised while processing the picked files, if any.
  final PlatformException? exception;
}
