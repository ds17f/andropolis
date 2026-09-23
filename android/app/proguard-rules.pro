# JNI: native code looks these up by name (Java_micropolis_port_MicropolisNative_*).
-keep class micropolis.port.MicropolisNative { *; }
-keepclasseswithmembernames class * { native <methods>; }
