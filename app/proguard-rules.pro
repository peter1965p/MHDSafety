# ML Kit (Barcode + Texterkennung) nicht von R8 umbauen lassen.
# Sonst stürzt die Release-Version beim Öffnen des Scanners ab
# (NullPointerException in BarcodeScanning.getClient, Version 1.0.2).
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode_bundled.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-keep class com.google.android.gms.internal.mlkit_common.** { *; }
